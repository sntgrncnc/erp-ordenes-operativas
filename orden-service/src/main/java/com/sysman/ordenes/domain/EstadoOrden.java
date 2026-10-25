package com.sysman.ordenes.domain;

/**
 * Estados de una orden. Las transiciones permitidas NO se codifican aquí: se
 * parametrizan en la tabla TRANSICION_ESTADO y las valida el procedimiento
 * PL/SQL {@code PKG_ORDEN.ACTUALIZAR_ESTADO} (única fuente de verdad).
 */
public enum EstadoOrden {
    REGISTRADA,
    ASIGNADA,
    EN_EJECUCION,
    SUSPENDIDA,
    EJECUTADA,
    CERRADA,
    CANCELADA
}
