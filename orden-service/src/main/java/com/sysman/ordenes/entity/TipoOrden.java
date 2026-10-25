package com.sysman.ordenes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "TIPO_ORDEN")
public class TipoOrden extends EntidadAuditable {

    @Id
    @Column(name = "CODIGO", length = 30)
    private String codigo;

    @Column(name = "DESCRIPCION", nullable = false, length = 200)
    private String descripcion;

    @Column(name = "SLA_HORAS", nullable = false)
    private Integer slaHoras;

    @Column(name = "ACTIVO", nullable = false, length = 1)
    private String activo;

    public TipoOrden() {
    }

    public boolean estaActivo() {
        return "S".equals(activo);
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getSlaHoras() {
        return slaHoras;
    }

    public void setSlaHoras(Integer slaHoras) {
        this.slaHoras = slaHoras;
    }

    public String getActivo() {
        return activo;
    }

    public void setActivo(String activo) {
        this.activo = activo;
    }
}
