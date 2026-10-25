package com.sysman.ordenes.entity;

import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.domain.Prioridad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Orden operativa. La tabla está particionada mensualmente por FECHA_CREACION.
 * <p>
 * El ID se obtiene de SEQ_ORDEN en bloques de 50 (optimizador pooled), lo que
 * reduce los viajes a la BD en escenarios de alta inserción.
 * <p>
 * Los cambios de estado NO se hacen modificando esta entidad: se delegan al
 * procedimiento PL/SQL PKG_ORDEN.ACTUALIZAR_ESTADO (ver OrdenEstadoProcedimiento).
 */
@Entity
@Table(name = "ORDEN")
public class Orden extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqOrden")
    @SequenceGenerator(name = "seqOrden", sequenceName = "SEQ_ORDEN", allocationSize = 50)
    @Column(name = "ID_ORDEN")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_CLIENTE", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CODIGO_TIPO_ORDEN", nullable = false)
    private TipoOrden tipoOrden;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoOrden estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORIDAD", nullable = false, length = 10)
    private Prioridad prioridad;

    @Column(name = "DESCRIPCION", nullable = false, length = 1000)
    private String descripcion;

    @Column(name = "DIRECCION_SERVICIO", length = 250)
    private String direccionServicio;

    @Column(name = "FECHA_ESTADO", nullable = false)
    private LocalDateTime fechaEstado;

    /** Control de concurrencia optimista (también lo incrementa el PL/SQL). */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    public Orden() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public TipoOrden getTipoOrden() {
        return tipoOrden;
    }

    public void setTipoOrden(TipoOrden tipoOrden) {
        this.tipoOrden = tipoOrden;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrden estado) {
        this.estado = estado;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(Prioridad prioridad) {
        this.prioridad = prioridad;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDireccionServicio() {
        return direccionServicio;
    }

    public void setDireccionServicio(String direccionServicio) {
        this.direccionServicio = direccionServicio;
    }

    public LocalDateTime getFechaEstado() {
        return fechaEstado;
    }

    public void setFechaEstado(LocalDateTime fechaEstado) {
        this.fechaEstado = fechaEstado;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
