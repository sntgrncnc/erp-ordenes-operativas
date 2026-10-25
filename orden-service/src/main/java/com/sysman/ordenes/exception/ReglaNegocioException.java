package com.sysman.ordenes.exception;

import org.springframework.http.HttpStatus;

/** 422 - Regla de negocio incumplida (cliente inactivo, observación requerida...). */
public class ReglaNegocioException extends NegocioException {

    public ReglaNegocioException(String codigo, String mensaje) {
        super(codigo, HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    public ReglaNegocioException(String codigo, String mensaje, Throwable causa) {
        super(codigo, HttpStatus.UNPROCESSABLE_ENTITY, mensaje, causa);
    }
}
