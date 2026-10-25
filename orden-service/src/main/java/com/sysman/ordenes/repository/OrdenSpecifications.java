package com.sysman.ordenes.repository;

import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.entity.Orden;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Filtros dinámicos del listado. El filtro por FECHA_CREACION siempre está
 * presente para que Oracle aplique partition pruning sobre ORDEN.
 */
public final class OrdenSpecifications {

    private OrdenSpecifications() {
    }

    public static Specification<Orden> conEstado(EstadoOrden estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    /** Rango semiabierto [desde, hasta). */
    public static Specification<Orden> creadaEntre(LocalDateTime desde, LocalDateTime hasta) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("fechaCreacion"), desde),
                cb.lessThan(root.get("fechaCreacion"), hasta));
    }

    public static Specification<Orden> delCliente(Long idCliente) {
        return (root, query, cb) -> idCliente == null ? null : cb.equal(root.get("cliente").get("id"), idCliente);
    }
}
