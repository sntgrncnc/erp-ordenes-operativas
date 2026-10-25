package com.sysman.ordenes.mapper;

import com.sysman.ordenes.dto.OrdenResponse;
import com.sysman.ordenes.dto.OrdenResponse.ClienteResumen;
import com.sysman.ordenes.dto.OrdenResponse.HistoricoResponse;
import com.sysman.ordenes.dto.OrdenResponse.TipoOrdenResumen;
import com.sysman.ordenes.dto.OrdenResumenResponse;
import com.sysman.ordenes.entity.Cliente;
import com.sysman.ordenes.entity.Orden;
import com.sysman.ordenes.entity.OrdenHistorico;
import com.sysman.ordenes.entity.TipoOrden;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conversión entidad -> DTO. Debe invocarse dentro de la transacción (relaciones LAZY). */
@Component
public class OrdenMapper {

    public OrdenResponse aResponse(Orden orden, List<OrdenHistorico> historial) {
        Cliente c = orden.getCliente();
        TipoOrden t = orden.getTipoOrden();
        return new OrdenResponse(
                orden.getId(),
                new ClienteResumen(c.getId(), c.getTipoDocumento(), c.getNumeroDocumento(), c.getNombre()),
                new TipoOrdenResumen(t.getCodigo(), t.getDescripcion(), t.getSlaHoras()),
                orden.getEstado(),
                orden.getPrioridad(),
                orden.getDescripcion(),
                orden.getDireccionServicio(),
                orden.getFechaEstado(),
                orden.getVersion(),
                orden.getCreadoPor(),
                orden.getFechaCreacion(),
                orden.getModificadoPor(),
                orden.getFechaModificacion(),
                historial.stream().map(this::aHistorico).toList());
    }

    public OrdenResumenResponse aResumen(Orden orden) {
        return new OrdenResumenResponse(
                orden.getId(),
                orden.getCliente().getId(),
                orden.getCliente().getNombre(),
                orden.getTipoOrden().getCodigo(),
                orden.getEstado(),
                orden.getPrioridad(),
                orden.getFechaEstado(),
                orden.getFechaCreacion(),
                orden.getVersion());
    }

    public HistoricoResponse aHistorico(OrdenHistorico h) {
        return new HistoricoResponse(
                h.getId(),
                h.getEstadoAnterior(),
                h.getEstadoNuevo(),
                h.getObservacion(),
                h.getVersionOrden(),
                h.getOrigen(),
                h.getUsuario(),
                h.getCorrelationId(),
                h.getIdTransaccion(),
                h.getFechaCambio());
    }
}
