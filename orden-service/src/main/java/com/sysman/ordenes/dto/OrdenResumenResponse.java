package com.sysman.ordenes.dto;

import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.domain.Prioridad;

import java.time.LocalDateTime;

/** Vista resumida de una orden para el listado paginado (sin historial). */
public record OrdenResumenResponse(
        Long id,
        Long idCliente,
        String nombreCliente,
        String tipoOrden,
        EstadoOrden estado,
        Prioridad prioridad,
        LocalDateTime fechaEstado,
        LocalDateTime fechaCreacion,
        Long version
) {
}
