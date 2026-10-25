package com.sysman.ordenes.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Propaga un identificador de correlación por petición (cabecera
 * {@code X-Correlation-Id}). Se agrega al MDC para que aparezca en todos los
 * logs JSON, se devuelve en la respuesta y se persiste en ORDEN_HISTORICO,
 * permitiendo trazar una operación desde el log hasta la base de datos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String HEADER_CORRELATION = "X-Correlation-Id";
    public static final String HEADER_USUARIO = "X-Usuario";
    public static final String MDC_CORRELATION = "correlationId";
    public static final String MDC_USUARIO = "usuario";

    private static final Pattern VALOR_SEGURO = Pattern.compile("^[A-Za-z0-9._\\-]{1,64}$");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long inicio = System.nanoTime();
        String correlationId = valorSeguro(request.getHeader(HEADER_CORRELATION))
                ? request.getHeader(HEADER_CORRELATION)
                : UUID.randomUUID().toString();
        String usuario = valorSeguro(request.getHeader(HEADER_USUARIO)) ? request.getHeader(HEADER_USUARIO) : "anonimo";

        MDC.put(MDC_CORRELATION, correlationId);
        MDC.put(MDC_USUARIO, usuario);
        response.setHeader(HEADER_CORRELATION, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator")) {
                log.info("Petición HTTP procesada",
                        kv("evento", "HTTP_REQUEST"),
                        kv("metodo", request.getMethod()),
                        kv("ruta", request.getRequestURI()),
                        kv("status", response.getStatus()),
                        kv("duracionMs", (System.nanoTime() - inicio) / 1_000_000));
            }
            MDC.clear();
        }
    }

    /** Evita inyección de valores arbitrarios en logs / BD. */
    private static boolean valorSeguro(String valor) {
        return StringUtils.hasText(valor) && VALOR_SEGURO.matcher(valor).matches();
    }
}
