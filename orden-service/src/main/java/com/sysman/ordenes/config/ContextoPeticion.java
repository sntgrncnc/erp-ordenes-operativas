package com.sysman.ordenes.config;

import org.slf4j.MDC;

/**
 * Acceso al contexto de la petición actual (usuario y correlación) que
 * {@link CorrelationIdFilter} deja en el MDC.
 * En un entorno productivo el usuario provendría del token JWT (Spring Security).
 */
public final class ContextoPeticion {

    private static final String USUARIO_POR_DEFECTO = "sistema";

    private ContextoPeticion() {
    }

    public static String usuario() {
        String usuario = MDC.get(CorrelationIdFilter.MDC_USUARIO);
        return usuario != null ? usuario : USUARIO_POR_DEFECTO;
    }

    public static String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_CORRELATION);
    }
}
