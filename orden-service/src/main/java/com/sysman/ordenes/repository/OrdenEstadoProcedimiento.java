package com.sysman.ordenes.repository;

import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.exception.OracleErrorTranslator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;

/**
 * Invoca el procedimiento almacenado PKG_ORDEN.ACTUALIZAR_ESTADO vía JDBC.
 * <p>
 * Se ejecuta sobre la MISMA conexión/transacción que JPA (JpaTransactionManager
 * expone la conexión JDBC al JdbcTemplate), por lo que el cambio de estado y
 * cualquier otra operación del servicio se confirman o revierten juntas.
 */
@Repository
public class OrdenEstadoProcedimiento {

    static final String LLAMADA =
            "{call PKG_ORDEN.ACTUALIZAR_ESTADO(?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    private final JdbcTemplate jdbcTemplate;
    private final OracleErrorTranslator errorTranslator;

    public OrdenEstadoProcedimiento(JdbcTemplate jdbcTemplate, OracleErrorTranslator errorTranslator) {
        this.jdbcTemplate = jdbcTemplate;
        this.errorTranslator = errorTranslator;
    }

    public record CambioEstado(Long idOrden, EstadoOrden estadoNuevo, String observacion, String usuario,
                               Long versionEsperada, String origen, String correlationId) {
    }

    public record ResultadoCambioEstado(long versionNueva, long idHistorico) {
    }

    public ResultadoCambioEstado ejecutar(CambioEstado cambio) {
        try {
            return jdbcTemplate.execute(LLAMADA, (CallableStatementCallback<ResultadoCambioEstado>) cs -> {
                cs.setLong(1, cambio.idOrden());
                cs.setString(2, cambio.estadoNuevo().name());
                cs.setString(3, cambio.observacion());
                cs.setString(4, cambio.usuario());
                if (cambio.versionEsperada() == null) {
                    cs.setNull(5, Types.NUMERIC);
                } else {
                    cs.setLong(5, cambio.versionEsperada());
                }
                cs.setString(6, cambio.origen());
                cs.setString(7, cambio.correlationId());
                cs.registerOutParameter(8, Types.NUMERIC);
                cs.registerOutParameter(9, Types.NUMERIC);
                cs.execute();
                return new ResultadoCambioEstado(cs.getLong(8), cs.getLong(9));
            });
        } catch (DataAccessException ex) {
            throw errorTranslator.traducir(ex);
        }
    }
}
