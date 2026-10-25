package com.sysman.ordenes.dto;

import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.domain.Prioridad;

import java.time.LocalDateTime;
import java.util.List;

/** Detalle completo de una orden, incluido su historial de estados. */
public record OrdenResponse(
        Long id,
        ClienteResumen cliente,
        TipoOrdenResumen tipoOrden,
        EstadoOrden estado,
        Prioridad prioridad,
        String descripcion,
        String direccionServicio,
        LocalDateTime fechaEstado,
        Long version,
        String creadoPor,
        LocalDateTime fechaCreacion,
        String modificadoPor,
        LocalDateTime fechaModificacion,
        List<HistoricoResponse> historial
) {

    public record ClienteResumen(Long id, String tipoDocumento, String numeroDocumento, String nombre) {
    }

    public record TipoOrdenResumen(String codigo, String descripcion, Integer slaHoras) {
    }

    public record HistoricoResponse(
            Long id,
            EstadoOrden estadoAnterior,
            EstadoOrden estadoNuevo,
            String observacion,
            Long versionOrden,
            String origen,
            String usuario,
            String correlationId,
            String idTransaccion,
            LocalDateTime fechaCambio
    ) {
    }
}
