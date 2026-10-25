package com.sysman.ordenes.dto;

import com.sysman.ordenes.domain.EstadoOrden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Solicitud de cambio de estado")
public record ActualizarEstadoRequest(

        @Schema(example = "ASIGNADA")
        @NotNull(message = "El estado es obligatorio")
        EstadoOrden estado,

        @Schema(example = "Asignada a la cuadrilla 12")
        @Size(max = 1000, message = "La observación admite máximo 1000 caracteres")
        String observacion,

        @Schema(description = "Versión de la orden leída por el cliente (bloqueo optimista). "
                + "Si se envía y no coincide con la actual se responde 409.", example = "0")
        @PositiveOrZero(message = "La versión debe ser mayor o igual a cero")
        Long version
) {
}
