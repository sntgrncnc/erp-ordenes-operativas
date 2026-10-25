package com.sysman.ordenes.exception;

import org.springframework.http.HttpStatus;

/** 400 - Parámetros de la solicitud inválidos (incluye ORA-20005). */
public class SolicitudInvalidaException extends NegocioException {

    public SolicitudInvalidaException(String mensaje) {
        super("SOLICITUD_INVALIDA", HttpStatus.BAD_REQUEST, mensaje);
    }

    public SolicitudInvalidaException(String mensaje, Throwable causa) {
        super("SOLICITUD_INVALIDA", HttpStatus.BAD_REQUEST, mensaje, causa);
    }
}
