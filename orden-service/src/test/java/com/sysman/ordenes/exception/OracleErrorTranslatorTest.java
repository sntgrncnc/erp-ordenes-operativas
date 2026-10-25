package com.sysman.ordenes.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class OracleErrorTranslatorTest {

    private final OracleErrorTranslator translator = new OracleErrorTranslator();

    private DataAccessException crearExcepcion(int errorCode, String mensaje) {
        SQLException sqlEx = new SQLException("ORA-" + errorCode + ": " + mensaje, "72000", errorCode);
        return new UncategorizedSQLException("task", "sql", sqlEx);
    }

    @Test
    @DisplayName("Traduce ORA-20001 a RecursoNoEncontradoException")
    void traduceOrdenNoExiste() {
        DataAccessException dae = crearExcepcion(20001, "La orden 999 no existe");
        RuntimeException result = translator.traducir(dae);

        assertThat(result).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(result.getMessage()).isEqualTo("La orden 999 no existe");
    }

    @Test
    @DisplayName("Traduce ORA-20002 a TransicionEstadoInvalidaException")
    void traduceTransicionInvalida() {
        DataAccessException dae = crearExcepcion(20002, "Transición no permitida: REGISTRADA -> CERRADA");
        RuntimeException result = translator.traducir(dae);

        assertThat(result).isInstanceOf(TransicionEstadoInvalidaException.class);
        assertThat(result.getMessage()).isEqualTo("Transición no permitida: REGISTRADA -> CERRADA");
    }

    @Test
    @DisplayName("Traduce ORA-20003 a ConflictoConcurrenciaException (versión)")
    void traduceConflictoVersion() {
        DataAccessException dae = crearExcepcion(20003, "La orden 1 fue modificada por otro usuario");
        RuntimeException result = translator.traducir(dae);

        assertThat(result).isInstanceOf(ConflictoConcurrenciaException.class);
        ConflictoConcurrenciaException cce = (ConflictoConcurrenciaException) result;
        assertThat(cce.getCodigo()).isEqualTo("CONFLICTO_CONCURRENCIA");
    }

    @Test
    @DisplayName("Traduce ORA-20004 a ConflictoConcurrenciaException (bloqueo)")
    void traduceRecursoBloqueado() {
        DataAccessException dae = crearExcepcion(20004, "La orden 1 está siendo modificada por otra transacción");
        RuntimeException result = translator.traducir(dae);

        assertThat(result).isInstanceOf(ConflictoConcurrenciaException.class);
        ConflictoConcurrenciaException cce = (ConflictoConcurrenciaException) result;
        assertThat(cce.getCodigo()).isEqualTo("RECURSO_BLOQUEADO");
    }

    @Test
    @DisplayName("Traduce ORA-20005 a SolicitudInvalidaException")
    void traduceParametroInvalido() {
        DataAccessException dae = crearExcepcion(20005, "El estado 'INVALIDO' no existe");
        RuntimeException result = translator.traducir(dae);

        assertThat(result).isInstanceOf(SolicitudInvalidaException.class);
        assertThat(result.getMessage()).isEqualTo("El estado 'INVALIDO' no existe");
    }

    @Test
    @DisplayName("Traduce ORA-20006 a ReglaNegocioException (observación requerida)")
    void traduceObservacionRequerida() {
        DataAccessException dae = crearExcepcion(20006, "La transición requiere una observación");
        RuntimeException result = translator.traducir(dae);

        assertThat(result).isInstanceOf(ReglaNegocioException.class);
        ReglaNegocioException rne = (ReglaNegocioException) result;
        assertThat(rne.getCodigo()).isEqualTo("OBSERVACION_REQUERIDA");
    }

    @Test
    @DisplayName("Propaga la excepción original si no es un ORA-20xxx mapeado")
    void propagaExcepcionDesconocida() {
        DataAccessException dae = crearExcepcion(942, "table or view does not exist");
        RuntimeException result = translator.traducir(dae);

        assertThat(result).isSameAs(dae);
    }
}
