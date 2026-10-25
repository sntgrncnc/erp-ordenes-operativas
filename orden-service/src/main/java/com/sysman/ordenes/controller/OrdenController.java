package com.sysman.ordenes.controller;

import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.dto.ActualizarEstadoRequest;
import com.sysman.ordenes.dto.CrearOrdenRequest;
import com.sysman.ordenes.dto.FiltroOrden;
import com.sysman.ordenes.dto.OrdenResponse;
import com.sysman.ordenes.dto.OrdenResumenResponse;
import com.sysman.ordenes.dto.PaginaResponse;
import com.sysman.ordenes.dto.RespuestaError;
import com.sysman.ordenes.service.OrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;

@Tag(name = "Órdenes", description = "Gestión de órdenes operativas")
@RestController
@RequestMapping("/orden")
public class OrdenController {

    private final OrdenService ordenService;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    @Operation(summary = "Crear orden")
    @ApiResponse(responseCode = "201", description = "Orden creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    @ApiResponse(responseCode = "404", description = "Cliente no existe",
            content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    @ApiResponse(responseCode = "422", description = "Cliente inactivo / tipo inválido",
            content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    @PostMapping
    public ResponseEntity<OrdenResponse> crear(@Valid @RequestBody CrearOrdenRequest request) {
        OrdenResponse creada = ordenService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @Operation(summary = "Consultar orden (incluye historial de estados)")
    @ApiResponse(responseCode = "200", description = "Orden encontrada")
    @ApiResponse(responseCode = "404", description = "Orden no existe",
            content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    @GetMapping("/{id}")
    public OrdenResponse obtener(@PathVariable @Positive(message = "El id debe ser positivo") Long id) {
        return ordenService.obtener(id);
    }

    @Operation(summary = "Actualizar estado (procedimiento PL/SQL PKG_ORDEN.ACTUALIZAR_ESTADO)")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "404", description = "Orden no existe",
            content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    @ApiResponse(responseCode = "409", description = "Conflicto de versión o registro bloqueado",
            content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    @ApiResponse(responseCode = "422", description = "Transición no permitida / observación requerida",
            content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    @PutMapping("/{id}/estado")
    public OrdenResponse actualizarEstado(@PathVariable @Positive(message = "El id debe ser positivo") Long id,
                                          @Valid @RequestBody ActualizarEstadoRequest request) {
        return ordenService.actualizarEstado(id, request);
    }

    @Operation(summary = "Listar órdenes con paginación y filtros",
            description = "Fechas en formato yyyy-MM-dd (UTC, inclusivas). Sin fechas se consultan los "
                    + "últimos 30 días; el rango máximo es 366 días. Tamaño de página máximo: 100.")
    @GetMapping
    public PaginaResponse<OrdenResumenResponse> listar(
            @RequestParam(required = false) EstadoOrden estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) @Positive(message = "El id del cliente debe ser positivo") Long idCliente,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ordenService.listar(new FiltroOrden(estado, fechaInicio, fechaFin, idCliente), pageable);
    }
}
