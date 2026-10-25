package com.sysman.ordenes.repository;

import com.sysman.ordenes.entity.TipoOrden;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoOrdenRepository extends JpaRepository<TipoOrden, String> {
}
