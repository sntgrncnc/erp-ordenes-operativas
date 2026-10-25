package com.sysman.ordenes.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Formato único de error de la API. */
public record RespuestaError(
        OffsetDateTime timestamp,
        int status,
        String codigo,
        String mensaje,
        String ruta,
        String correlationId,
        List<DetalleError> detalles
) {

    public record DetalleError(String campo, String mensaje) {
    }
}
