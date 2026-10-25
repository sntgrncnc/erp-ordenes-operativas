package com.sysman.ordenes.entity;

import com.sysman.ordenes.domain.EstadoOrden;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Registro inmutable del historial de estados (trazabilidad / auditoría).
 * La BD rechaza UPDATE/DELETE sobre esta tabla (trigger TRG_ORDEN_HIST_INMUTABLE).
 * ID_TRANSACCION y USUARIO_BD los completa el trigger TRG_ORDEN_HIST_BI.
 */
@Immutable
@Entity
@Table(name = "ORDEN_HISTORICO")
public class OrdenHistorico {

    public static final String ORIGEN_API = "API";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_HISTORICO")
    private Long id;

    @Column(name = "ID_ORDEN", nullable = false)
    private Long idOrden;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_ANTERIOR", length = 20)
    private EstadoOrden estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_NUEVO", nullable = false, length = 20)
    private EstadoOrden estadoNuevo;

    @Column(name = "OBSERVACION", length = 1000)
    private String observacion;

    @Column(name = "VERSION_ORDEN", nullable = false)
    private Long versionOrden;

    @Column(name = "ORIGEN", nullable = false, length = 30)
    private String origen;

    @Column(name = "CORRELATION_ID", length = 64)
    private String correlationId;

    @Column(name = "ID_TRANSACCION", insertable = false, updatable = false, length = 64)
    private String idTransaccion;

    @Column(name = "USUARIO", nullable = false, length = 100)
    private String usuario;

    @Column(name = "FECHA_CAMBIO", nullable = false)
    private LocalDateTime fechaCambio;

    public OrdenHistorico() {
    }

    /** Construye el registro de histórico asociado a la creación de una orden. */
    public static OrdenHistorico deCreacion(Orden orden, String usuario, String correlationId) {
        OrdenHistorico h = new OrdenHistorico();
        h.setIdOrden(orden.getId());
        h.setEstadoAnterior(null);
        h.setEstadoNuevo(orden.getEstado());
        h.setObservacion("Creación de la orden");
        h.setVersionOrden(orden.getVersion() != null ? orden.getVersion() : 0L);
        h.setOrigen(ORIGEN_API);
        h.setCorrelationId(correlationId);
        h.setUsuario(usuario);
        h.setFechaCambio(orden.getFechaCreacion());
        return h;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdOrden() {
        return idOrden;
    }

    public void setIdOrden(Long idOrden) {
        this.idOrden = idOrden;
    }

    public EstadoOrden getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(EstadoOrden estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public EstadoOrden getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(EstadoOrden estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public Long getVersionOrden() {
        return versionOrden;
    }

    public void setVersionOrden(Long versionOrden) {
        this.versionOrden = versionOrden;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getIdTransaccion() {
        return idTransaccion;
    }

    public void setIdTransaccion(String idTransaccion) {
        this.idTransaccion = idTransaccion;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getFechaCambio() {
        return fechaCambio;
    }

    public void setFechaCambio(LocalDateTime fechaCambio) {
        this.fechaCambio = fechaCambio;
    }
}
