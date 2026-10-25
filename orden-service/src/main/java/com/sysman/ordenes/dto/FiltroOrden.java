package com.sysman.ordenes.dto;

import com.sysman.ordenes.domain.EstadoOrden;

import java.time.LocalDate;

/** Filtros del listado de órdenes. Todas las fechas son UTC e inclusivas. */
public record FiltroOrden(
        EstadoOrden estado,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Long idCliente
) {
}
