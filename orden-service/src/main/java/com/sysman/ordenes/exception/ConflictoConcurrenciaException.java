package com.sysman.ordenes.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 - Conflicto de concurrencia: la orden fue modificada por otro usuario
 * (versión distinta, ORA-20003) o está bloqueada por otra transacción (ORA-20004).
 */
public class ConflictoConcurrenciaException extends NegocioException {

    public ConflictoConcurrenciaException(String codigo, String mensaje, Throwable causa) {
        super(codigo, HttpStatus.CONFLICT, mensaje, causa);
    }

    public static ConflictoConcurrenciaException versionDesactualizada(String mensaje, Throwable causa) {
        return new ConflictoConcurrenciaException("CONFLICTO_CONCURRENCIA", mensaje, causa);
    }

    public static ConflictoConcurrenciaException recursoBloqueado(String mensaje, Throwable causa) {
        return new ConflictoConcurrenciaException("RECURSO_BLOQUEADO", mensaje, causa);
    }
}
