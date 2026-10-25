package com.sysman.ordenes.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción base de negocio. Cada subtipo define su código funcional y el
 * status HTTP con el que el {@link GlobalExceptionHandler} la expone.
 */
public abstract class NegocioException extends RuntimeException {

    private final String codigo;
    private final HttpStatus status;

    protected NegocioException(String codigo, HttpStatus status, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
        this.status = status;
    }

    protected NegocioException(String codigo, HttpStatus status, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
        this.status = status;
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
