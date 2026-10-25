package com.sysman.ordenes.dto;

import com.sysman.ordenes.domain.Prioridad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear una orden")
public record CrearOrdenRequest(

        @Schema(example = "1")
        @NotNull(message = "El cliente es obligatorio")
        @Positive(message = "El id del cliente debe ser positivo")
        Long idCliente,

        @Schema(example = "REPARACION")
        @NotBlank(message = "El tipo de orden es obligatorio")
        @Size(max = 30, message = "El tipo de orden admite máximo 30 caracteres")
        String tipoOrden,

        @Schema(example = "Fuga de agua en la acometida principal")
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 1000, message = "La descripción admite máximo 1000 caracteres")
        String descripcion,

        @Schema(example = "Cra 7 # 45-10, Bogotá")
        @Size(max = 250, message = "La dirección admite máximo 250 caracteres")
        String direccionServicio,

        @Schema(description = "Por defecto MEDIA", example = "ALTA")
        Prioridad prioridad
) {
}
