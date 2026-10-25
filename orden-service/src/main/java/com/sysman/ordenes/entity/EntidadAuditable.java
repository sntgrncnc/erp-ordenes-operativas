package com.sysman.ordenes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Campos auditables comunes (quién y cuándo creó / modificó el registro).
 * Todas las fechas se manejan en UTC.
 */
@MappedSuperclass
public abstract class EntidadAuditable {

    @Column(name = "CREADO_POR", nullable = false, updatable = false, length = 100)
    private String creadoPor;

    @Column(name = "FECHA_CREACION", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "FECHA_MODIFICACION")
    private LocalDateTime fechaModificacion;

    @PrePersist
    protected void antesDeInsertar() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (creadoPor == null) {
            creadoPor = "sistema";
        }
    }

    @PreUpdate
    protected void antesDeActualizar() {
        fechaModificacion = LocalDateTime.now(ZoneOffset.UTC);
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(String creadoPor) {
        this.creadoPor = creadoPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public void setModificadoPor(String modificadoPor) {
        this.modificadoPor = modificadoPor;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    public void setFechaModificacion(LocalDateTime fechaModificacion) {
        this.fechaModificacion = fechaModificacion;
    }
}
