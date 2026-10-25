package com.sysman.ordenes.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * Traduce las excepciones personalizadas lanzadas por PKG_ORDEN
 * (RAISE_APPLICATION_ERROR -20001..-20006) a excepciones de negocio.
 * Es el contrato explícito entre la capa PL/SQL y la API.
 */
@Component
public class OracleErrorTranslator {

    public static final int ORDEN_NO_EXISTE = 20001;
    public static final int TRANSICION_INVALIDA = 20002;
    public static final int CONFLICTO_VERSION = 20003;
    public static final int ORDEN_BLOQUEADA = 20004;
    public static final int PARAMETRO_INVALIDO = 20005;
    public static final int OBSERVACION_REQUERIDA = 20006;

    public RuntimeException traducir(DataAccessException ex) {
        SQLException sqlEx = buscarErrorDeAplicacion(ex);
        if (sqlEx == null) {
            return ex;
        }
        String mensaje = limpiarMensaje(sqlEx.getMessage());
        return switch (sqlEx.getErrorCode()) {
            case ORDEN_NO_EXISTE -> new RecursoNoEncontradoException(mensaje, ex);
            case TRANSICION_INVALIDA -> new TransicionEstadoInvalidaException(mensaje, ex);
            case CONFLICTO_VERSION -> ConflictoConcurrenciaException.versionDesactualizada(mensaje, ex);
            case ORDEN_BLOQUEADA -> ConflictoConcurrenciaException.recursoBloqueado(mensaje, ex);
            case PARAMETRO_INVALIDO -> new SolicitudInvalidaException(mensaje, ex);
            case OBSERVACION_REQUERIDA -> new ReglaNegocioException("OBSERVACION_REQUERIDA", mensaje, ex);
            default -> ex;
        };
    }

    /** Recorre la cadena de causas buscando un error ORA-20xxx. */
    private static SQLException buscarErrorDeAplicacion(Throwable ex) {
        Throwable actual = ex;
        while (actual != null) {
            if (actual instanceof SQLException sql && sql.getErrorCode() >= 20000 && sql.getErrorCode() <= 20999) {
                return sql;
            }
            actual = actual.getCause();
        }
        return null;
    }

    /**
     * "ORA-20002: Transición no permitida: A -> B\nORA-06512: at ..." -> "Transición no permitida: A -> B"
     */
    static String limpiarMensaje(String mensajeOracle) {
        if (mensajeOracle == null) {
            return "Error de base de datos";
        }
        String primeraLinea = mensajeOracle.lines().findFirst().orElse(mensajeOracle);
        return primeraLinea.replaceFirst("^ORA-\\d{5}:\\s*", "").trim();
    }
}
