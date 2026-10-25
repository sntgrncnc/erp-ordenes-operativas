package com.sysman.ordenes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Reloj UTC inyectable para el manejo consistente de fechas en toda la aplicación. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("API de Gestión de Órdenes Operativas")
                .description("Creación, consulta, cambio de estado y listado de órdenes con trazabilidad")
                .version("1.0.0"));
    }
}
