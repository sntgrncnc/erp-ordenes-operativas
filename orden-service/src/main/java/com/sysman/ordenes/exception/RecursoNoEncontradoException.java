package com.sysman.ordenes.exception;

import org.springframework.http.HttpStatus;

/** 404 - El recurso solicitado no existe (ORA-20001 para órdenes). */
public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String mensaje) {
        super("RECURSO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, mensaje);
    }

    public RecursoNoEncontradoException(String mensaje, Throwable causa) {
        super("RECURSO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, mensaje, causa);
    }
}
