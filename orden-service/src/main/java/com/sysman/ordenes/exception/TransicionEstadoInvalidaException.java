package com.sysman.ordenes.exception;

import org.springframework.http.HttpStatus;

/** 422 - La transición de estado solicitada no está permitida (ORA-20002). */
public class TransicionEstadoInvalidaException extends NegocioException {

    public TransicionEstadoInvalidaException(String mensaje, Throwable causa) {
        super("TRANSICION_INVALIDA", HttpStatus.UNPROCESSABLE_ENTITY, mensaje, causa);
    }
}
