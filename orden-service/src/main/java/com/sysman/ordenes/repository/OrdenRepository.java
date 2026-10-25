package com.sysman.ordenes.repository;

import com.sysman.ordenes.entity.Orden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface OrdenRepository extends JpaRepository<Orden, Long>, JpaSpecificationExecutor<Orden> {

    /** Carga la orden con cliente y tipo en una sola consulta (evita N+1). */
    @EntityGraph(attributePaths = {"cliente", "tipoOrden"})
    Optional<Orden> findConDetalleById(Long id);

    /** Listado paginado con filtros dinámicos; cliente y tipo en la misma consulta. */
    @Override
    @NonNull
    @EntityGraph(attributePaths = {"cliente", "tipoOrden"})
    Page<Orden> findAll(Specification<Orden> spec, @NonNull Pageable pageable);
}
