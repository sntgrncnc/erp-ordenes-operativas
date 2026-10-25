-- =============================================================================
--  PKG_ORDEN - Lógica transaccional de cambio de estado de órdenes
--
--  ACTUALIZAR_ESTADO:
--    1. Valida parámetros
--    2. Bloquea la fila de la orden (pesimista, SELECT ... FOR UPDATE WAIT)
--    3. Valida la versión esperada (optimista, opcional)
--    4. Valida la transición contra TRANSICION_ESTADO
--    5. Actualiza la orden (estado, versión, campos auditables)
--    6. Inserta el registro en ORDEN_HISTORICO
--    Ante cualquier error: ROLLBACK TO SAVEPOINT, registra el intento en
--    ORDEN_ERROR_LOG (transacción autónoma) y propaga una excepción
--    personalizada (RAISE_APPLICATION_ERROR -20001..-20006).
--
--  IMPORTANTE: el procedimiento NO hace COMMIT. La transacción la demarca el
--  llamador (Spring @Transactional), de modo que el cambio de estado puede
--  formar parte de una unidad de trabajo mayor.
-- =============================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF

CREATE OR REPLACE PACKAGE PKG_ORDEN AUTHID DEFINER AS

    -- -------------------------------------------------------------------------
    -- Excepciones personalizadas (contrato con la capa Java)
    -- -------------------------------------------------------------------------
    e_orden_no_existe          EXCEPTION;  -- HTTP 404
    e_transicion_invalida      EXCEPTION;  -- HTTP 422
    e_conflicto_version        EXCEPTION;  -- HTTP 409
    e_orden_bloqueada          EXCEPTION;  -- HTTP 409
    e_parametro_invalido       EXCEPTION;  -- HTTP 400
    e_observacion_requerida    EXCEPTION;  -- HTTP 422

    PRAGMA EXCEPTION_INIT(e_orden_no_existe,       -20001);
    PRAGMA EXCEPTION_INIT(e_transicion_invalida,   -20002);
    PRAGMA EXCEPTION_INIT(e_conflicto_version,     -20003);
    PRAGMA EXCEPTION_INIT(e_orden_bloqueada,       -20004);
    PRAGMA EXCEPTION_INIT(e_parametro_invalido,    -20005);
    PRAGMA EXCEPTION_INIT(e_observacion_requerida, -20006);

    c_err_orden_no_existe       CONSTANT PLS_INTEGER := -20001;
    c_err_transicion_invalida   CONSTANT PLS_INTEGER := -20002;
    c_err_conflicto_version     CONSTANT PLS_INTEGER := -20003;
    c_err_orden_bloqueada       CONSTANT PLS_INTEGER := -20004;
    c_err_parametro_invalido    CONSTANT PLS_INTEGER := -20005;
    c_err_observacion_requerida CONSTANT PLS_INTEGER := -20006;

    /**
     * Actualiza el estado de una orden validando la transición y registrando
     * el histórico.
     *
     * @param p_id_orden          Identificador de la orden
     * @param p_estado_nuevo      Estado destino
     * @param p_observacion       Observación del cambio (obligatoria según la transición)
     * @param p_usuario           Usuario de negocio que realiza el cambio
     * @param p_version_esperada  Versión que el cliente leyó (NULL = no validar)
     * @param p_origen            API | BATCH | PLSQL | INTEGRACION
     * @param p_correlation_id    Id de correlación de la petición
     * @param o_version_nueva     Versión resultante de la orden
     * @param o_id_historico      Id del registro de histórico insertado
     */
    PROCEDURE ACTUALIZAR_ESTADO (
        p_id_orden          IN  ORDEN.ID_ORDEN%TYPE,
        p_estado_nuevo      IN  VARCHAR2,
        p_observacion       IN  VARCHAR2,
        p_usuario           IN  VARCHAR2,
        p_version_esperada  IN  ORDEN.VERSION%TYPE DEFAULT NULL,
        p_origen            IN  VARCHAR2           DEFAULT 'API',
        p_correlation_id    IN  VARCHAR2           DEFAULT NULL,
        o_version_nueva     OUT ORDEN.VERSION%TYPE,
        o_id_historico      OUT ORDEN_HISTORICO.ID_HISTORICO%TYPE
    );

    /** Retorna 'S' si la transición origen -> destino está permitida. */
    FUNCTION ES_TRANSICION_VALIDA (
        p_estado_origen   IN VARCHAR2,
        p_estado_destino  IN VARCHAR2
    ) RETURN VARCHAR2;

END PKG_ORDEN;
/
SHOW ERRORS PACKAGE PKG_ORDEN

CREATE OR REPLACE PACKAGE BODY PKG_ORDEN AS

    -- ORA-00054 (NOWAIT) y ORA-30006 (WAIT n agotado): recurso ocupado
    e_recurso_ocupado_nowait EXCEPTION;
    e_recurso_ocupado_wait   EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_recurso_ocupado_nowait, -54);
    PRAGMA EXCEPTION_INIT(e_recurso_ocupado_wait,   -30006);

    c_max_observacion CONSTANT PLS_INTEGER := 1000;

    -- -------------------------------------------------------------------------
    -- Registro de errores en transacción AUTÓNOMA: persiste aunque la
    -- transacción principal haga ROLLBACK.
    -- -------------------------------------------------------------------------
    PROCEDURE registrar_error (
        p_id_orden        IN NUMBER,
        p_estado          IN VARCHAR2,
        p_codigo          IN NUMBER,
        p_mensaje         IN VARCHAR2,
        p_backtrace       IN VARCHAR2,
        p_usuario         IN VARCHAR2,
        p_correlation_id  IN VARCHAR2
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        INSERT INTO ORDEN_ERROR_LOG (ID_ORDEN, ESTADO_SOLICITADO, CODIGO_ERROR, MENSAJE,
                                     BACKTRACE, USUARIO, CORRELATION_ID)
        VALUES (p_id_orden, SUBSTR(p_estado, 1, 30), p_codigo, SUBSTR(p_mensaje, 1, 4000),
                SUBSTR(p_backtrace, 1, 4000), SUBSTR(p_usuario, 1, 100), SUBSTR(p_correlation_id, 1, 64));
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;  -- el log nunca debe ocultar el error original
    END registrar_error;

    -- -------------------------------------------------------------------------
    FUNCTION ES_TRANSICION_VALIDA (
        p_estado_origen   IN VARCHAR2,
        p_estado_destino  IN VARCHAR2
    ) RETURN VARCHAR2 IS
        v_existe PLS_INTEGER;
    BEGIN
        SELECT COUNT(*)
          INTO v_existe
          FROM TRANSICION_ESTADO
         WHERE ESTADO_ORIGEN  = p_estado_origen
           AND ESTADO_DESTINO = p_estado_destino
           AND ACTIVO = 'S';
        RETURN CASE WHEN v_existe > 0 THEN 'S' ELSE 'N' END;
    END ES_TRANSICION_VALIDA;

    -- -------------------------------------------------------------------------
    PROCEDURE ACTUALIZAR_ESTADO (
        p_id_orden          IN  ORDEN.ID_ORDEN%TYPE,
        p_estado_nuevo      IN  VARCHAR2,
        p_observacion       IN  VARCHAR2,
        p_usuario           IN  VARCHAR2,
        p_version_esperada  IN  ORDEN.VERSION%TYPE DEFAULT NULL,
        p_origen            IN  VARCHAR2           DEFAULT 'API',
        p_correlation_id    IN  VARCHAR2           DEFAULT NULL,
        o_version_nueva     OUT ORDEN.VERSION%TYPE,
        o_id_historico      OUT ORDEN_HISTORICO.ID_HISTORICO%TYPE
    ) IS
        v_estado_nuevo     VARCHAR2(30);
        v_usuario          VARCHAR2(100);
        v_origen           VARCHAR2(30);
        v_observacion      VARCHAR2(4000);
        v_estado_actual    ORDEN.ESTADO%TYPE;
        v_version_actual   ORDEN.VERSION%TYPE;
        v_requiere_obs     TRANSICION_ESTADO.REQUIERE_OBSERVACION%TYPE;
        v_estado_conocido  PLS_INTEGER;
        v_ahora            TIMESTAMP(6);
    BEGIN
        -- Punto de restauración: ante error se deshace SOLO el trabajo de este
        -- procedimiento (y se liberan sus bloqueos), sin afectar al llamador.
        SAVEPOINT sp_actualizar_estado;

        v_estado_nuevo := UPPER(TRIM(p_estado_nuevo));
        v_usuario      := NVL(TRIM(p_usuario), SYS_CONTEXT('USERENV', 'SESSION_USER'));
        v_origen       := NVL(UPPER(TRIM(p_origen)), 'API');
        v_observacion  := TRIM(p_observacion);
        v_ahora        := SYSTIMESTAMP;

        -- 1. Validación de parámetros ------------------------------------------
        IF p_id_orden IS NULL OR v_estado_nuevo IS NULL THEN
            RAISE_APPLICATION_ERROR(c_err_parametro_invalido,
                'El identificador de la orden y el estado nuevo son obligatorios');
        END IF;

        IF LENGTH(v_observacion) > c_max_observacion THEN
            RAISE_APPLICATION_ERROR(c_err_parametro_invalido,
                'La observación supera los ' || c_max_observacion || ' caracteres');
        END IF;

        SELECT COUNT(*)
          INTO v_estado_conocido
          FROM TRANSICION_ESTADO
         WHERE ESTADO_DESTINO = v_estado_nuevo OR ESTADO_ORIGEN = v_estado_nuevo;

        IF v_estado_conocido = 0 THEN
            RAISE_APPLICATION_ERROR(c_err_parametro_invalido,
                'El estado ''' || v_estado_nuevo || ''' no existe');
        END IF;

        -- 2. Bloqueo pesimista de la fila (espera máx. 5 s) --------------------
        BEGIN
            SELECT ESTADO, VERSION
              INTO v_estado_actual, v_version_actual
              FROM ORDEN
             WHERE ID_ORDEN = p_id_orden
               FOR UPDATE WAIT 5;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(c_err_orden_no_existe,
                    'La orden ' || p_id_orden || ' no existe');
            WHEN e_recurso_ocupado_wait OR e_recurso_ocupado_nowait THEN
                RAISE_APPLICATION_ERROR(c_err_orden_bloqueada,
                    'La orden ' || p_id_orden || ' está siendo modificada por otra transacción. Intente nuevamente');
        END;

        -- 3. Control de concurrencia optimista ---------------------------------
        IF p_version_esperada IS NOT NULL AND p_version_esperada <> v_version_actual THEN
            RAISE_APPLICATION_ERROR(c_err_conflicto_version,
                'La orden ' || p_id_orden || ' fue modificada por otro usuario (versión esperada '
                || p_version_esperada || ', versión actual ' || v_version_actual || ')');
        END IF;

        -- 4. Validación de la transición ---------------------------------------
        IF v_estado_actual = v_estado_nuevo THEN
            RAISE_APPLICATION_ERROR(c_err_transicion_invalida,
                'La orden ' || p_id_orden || ' ya se encuentra en estado ' || v_estado_actual);
        END IF;

        BEGIN
            SELECT REQUIERE_OBSERVACION
              INTO v_requiere_obs
              FROM TRANSICION_ESTADO
             WHERE ESTADO_ORIGEN  = v_estado_actual
               AND ESTADO_DESTINO = v_estado_nuevo
               AND ACTIVO = 'S';
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(c_err_transicion_invalida,
                    'Transición no permitida: ' || v_estado_actual || ' -> ' || v_estado_nuevo);
        END;

        IF v_requiere_obs = 'S' AND v_observacion IS NULL THEN
            RAISE_APPLICATION_ERROR(c_err_observacion_requerida,
                'La transición ' || v_estado_actual || ' -> ' || v_estado_nuevo || ' requiere una observación');
        END IF;

        -- 5. Actualización de la orden -----------------------------------------
        UPDATE ORDEN
           SET ESTADO             = v_estado_nuevo,
               FECHA_ESTADO       = v_ahora,
               VERSION            = VERSION + 1,
               MODIFICADO_POR     = v_usuario,
               FECHA_MODIFICACION = v_ahora
         WHERE ID_ORDEN = p_id_orden
        RETURNING VERSION INTO o_version_nueva;

        -- 6. Registro en el histórico ------------------------------------------
        INSERT INTO ORDEN_HISTORICO (ID_ORDEN, ESTADO_ANTERIOR, ESTADO_NUEVO, OBSERVACION,
                                     VERSION_ORDEN, ORIGEN, CORRELATION_ID, USUARIO, FECHA_CAMBIO)
        VALUES (p_id_orden, v_estado_actual, v_estado_nuevo, v_observacion,
                o_version_nueva, v_origen, p_correlation_id, v_usuario, v_ahora)
        RETURNING ID_HISTORICO INTO o_id_historico;

    EXCEPTION
        WHEN OTHERS THEN
            DECLARE
                v_codigo    NUMBER         := SQLCODE;
                v_mensaje   VARCHAR2(4000) := SQLERRM;
                v_backtrace VARCHAR2(4000) := DBMS_UTILITY.FORMAT_ERROR_BACKTRACE;
            BEGIN
                ROLLBACK TO SAVEPOINT sp_actualizar_estado;
                registrar_error(p_id_orden, v_estado_nuevo, v_codigo, v_mensaje,
                                v_backtrace, v_usuario, p_correlation_id);
            END;
            RAISE;  -- conserva el código -2000X para que la capa Java lo traduzca
    END ACTUALIZAR_ESTADO;

END PKG_ORDEN;
/
SHOW ERRORS PACKAGE BODY PKG_ORDEN

-- Falla el despliegue si quedó algún objeto PL/SQL inválido
DECLARE
    v_invalidos PLS_INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_invalidos FROM USER_OBJECTS WHERE STATUS = 'INVALID';
    IF v_invalidos > 0 THEN
        RAISE_APPLICATION_ERROR(-20999, 'Existen ' || v_invalidos || ' objetos inválidos en el esquema');
    END IF;
END;
/

EXIT
