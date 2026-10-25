package com.sysman.ordenes.repository;

import com.sysman.ordenes.entity.OrdenHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenHistoricoRepository extends JpaRepository<OrdenHistorico, Long> {

    /** Usa el índice IX_HIST_ORDEN (ID_ORDEN, FECHA_CAMBIO). */
    List<OrdenHistorico> findByIdOrdenOrderByFechaCambioAscIdAsc(Long idOrden);
}
