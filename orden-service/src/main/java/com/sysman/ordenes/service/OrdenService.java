package com.sysman.ordenes.service;

import com.sysman.ordenes.config.ContextoPeticion;
import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.domain.Prioridad;
import com.sysman.ordenes.dto.ActualizarEstadoRequest;
import com.sysman.ordenes.dto.CrearOrdenRequest;
import com.sysman.ordenes.dto.FiltroOrden;
import com.sysman.ordenes.dto.OrdenResponse;
import com.sysman.ordenes.dto.OrdenResumenResponse;
import com.sysman.ordenes.dto.PaginaResponse;
import com.sysman.ordenes.entity.Cliente;
import com.sysman.ordenes.entity.Orden;
import com.sysman.ordenes.entity.OrdenHistorico;
import com.sysman.ordenes.entity.TipoOrden;
import com.sysman.ordenes.exception.RecursoNoEncontradoException;
import com.sysman.ordenes.exception.ReglaNegocioException;
import com.sysman.ordenes.exception.SolicitudInvalidaException;
import com.sysman.ordenes.mapper.OrdenMapper;
import com.sysman.ordenes.repository.ClienteRepository;
import com.sysman.ordenes.repository.OrdenEstadoProcedimiento;
import com.sysman.ordenes.repository.OrdenEstadoProcedimiento.CambioEstado;
import com.sysman.ordenes.repository.OrdenEstadoProcedimiento.ResultadoCambioEstado;
import com.sysman.ordenes.repository.OrdenHistoricoRepository;
import com.sysman.ordenes.repository.OrdenRepository;
import com.sysman.ordenes.repository.TipoOrdenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static com.sysman.ordenes.repository.OrdenSpecifications.conEstado;
import static com.sysman.ordenes.repository.OrdenSpecifications.creadaEntre;
import static com.sysman.ordenes.repository.OrdenSpecifications.delCliente;
import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Casos de uso de órdenes. Cada método público define su frontera transaccional.
 */
@Service
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

    /** Rango por defecto del listado cuando no se envían fechas. */
    static final int DIAS_RANGO_POR_DEFECTO = 30;
    /** Rango máximo consultable: limita el nº de particiones recorridas. */
    static final int DIAS_RANGO_MAXIMO = 366;

    private final OrdenRepository ordenRepository;
    private final OrdenHistoricoRepository historicoRepository;
    private final ClienteRepository clienteRepository;
    private final TipoOrdenRepository tipoOrdenRepository;
    private final OrdenEstadoProcedimiento estadoProcedimiento;
    private final OrdenMapper mapper;
    private final Clock clock;

    public OrdenService(OrdenRepository ordenRepository,
                        OrdenHistoricoRepository historicoRepository,
                        ClienteRepository clienteRepository,
                        TipoOrdenRepository tipoOrdenRepository,
                        OrdenEstadoProcedimiento estadoProcedimiento,
                        OrdenMapper mapper,
                        Clock clock) {
        this.ordenRepository = ordenRepository;
        this.historicoRepository = historicoRepository;
        this.clienteRepository = clienteRepository;
        this.tipoOrdenRepository = tipoOrdenRepository;
        this.estadoProcedimiento = estadoProcedimiento;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ crear
    @Transactional
    public OrdenResponse crear(CrearOrdenRequest request) {
        String usuario = ContextoPeticion.usuario();

        Cliente cliente = clienteRepository.findById(request.idCliente())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El cliente " + request.idCliente() + " no existe"));
        if (!cliente.estaActivo()) {
            throw new ReglaNegocioException("CLIENTE_INACTIVO",
                    "El cliente " + cliente.getId() + " está inactivo y no puede registrar órdenes");
        }

        String codigoTipo = request.tipoOrden().trim().toUpperCase(Locale.ROOT);
        TipoOrden tipo = tipoOrdenRepository.findById(codigoTipo)
                .orElseThrow(() -> new ReglaNegocioException("TIPO_ORDEN_INVALIDO",
                        "El tipo de orden '" + codigoTipo + "' no existe"));
        if (!tipo.estaActivo()) {
            throw new ReglaNegocioException("TIPO_ORDEN_INVALIDO",
                    "El tipo de orden '" + codigoTipo + "' no está activo");
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        Orden orden = new Orden();
        orden.setCliente(cliente);
        orden.setTipoOrden(tipo);
        orden.setEstado(EstadoOrden.REGISTRADA);
        orden.setPrioridad(request.prioridad() != null ? request.prioridad() : Prioridad.MEDIA);
        orden.setDescripcion(request.descripcion().trim());
        orden.setDireccionServicio(request.direccionServicio());
        orden.setFechaEstado(ahora);
        orden.setFechaCreacion(ahora);
        orden.setCreadoPor(usuario);

        // flush: garantiza que la ORDEN exista antes del INSERT del histórico (FK)
        Orden guardada = ordenRepository.saveAndFlush(orden);
        OrdenHistorico historico = historicoRepository.save(
                OrdenHistorico.deCreacion(guardada, usuario, ContextoPeticion.correlationId()));

        log.info("Orden creada",
                kv("evento", "ORDEN_CREADA"),
                kv("idOrden", guardada.getId()),
                kv("idCliente", cliente.getId()),
                kv("tipoOrden", tipo.getCodigo()),
                kv("prioridad", guardada.getPrioridad()));

        return mapper.aResponse(guardada, List.of(historico));
    }

    // --------------------------------------------------------------- consultar
    @Transactional(readOnly = true)
    public OrdenResponse obtener(Long id) {
        Orden orden = ordenRepository.findConDetalleById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La orden " + id + " no existe"));
        List<OrdenHistorico> historial = historicoRepository.findByIdOrdenOrderByFechaCambioAscIdAsc(id);
        return mapper.aResponse(orden, historial);
    }

    // ---------------------------------------------------------- cambiar estado
    /**
     * Delegado al procedimiento PL/SQL (validación de transición, bloqueo de
     * fila, control de versión e histórico). Si el procedimiento falla, la
     * excepción traducida provoca el rollback de toda la transacción.
     */
    @Transactional
    public OrdenResponse actualizarEstado(Long id, ActualizarEstadoRequest request) {
        String usuario = ContextoPeticion.usuario();
        ResultadoCambioEstado resultado = estadoProcedimiento.ejecutar(new CambioEstado(
                id,
                request.estado(),
                request.observacion(),
                usuario,
                request.version(),
                OrdenHistorico.ORIGEN_API,
                ContextoPeticion.correlationId()));

        log.info("Estado de orden actualizado",
                kv("evento", "ORDEN_ESTADO_ACTUALIZADO"),
                kv("idOrden", id),
                kv("estadoNuevo", request.estado()),
                kv("versionNueva", resultado.versionNueva()),
                kv("idHistorico", resultado.idHistorico()));

        // El contexto de persistencia está vacío: se lee el estado ya actualizado
        return obtener(id);
    }

    // ------------------------------------------------------------------ listar
    @Transactional(readOnly = true)
    public PaginaResponse<OrdenResumenResponse> listar(FiltroOrden filtro, Pageable pageable) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fin = filtro.fechaFin() != null ? filtro.fechaFin() : hoy;
        LocalDate inicio = filtro.fechaInicio() != null ? filtro.fechaInicio() : fin.minusDays(DIAS_RANGO_POR_DEFECTO);

        if (inicio.isAfter(fin)) {
            throw new SolicitudInvalidaException("fechaInicio no puede ser posterior a fechaFin");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > DIAS_RANGO_MAXIMO) {
            throw new SolicitudInvalidaException(
                    "El rango de fechas no puede superar " + DIAS_RANGO_MAXIMO + " días");
        }

        // [inicio 00:00, fin+1 00:00): fechaFin inclusiva y apta para partition pruning
        Specification<Orden> spec = Specification.allOf(
                creadaEntre(inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay()),
                conEstado(filtro.estado()),
                delCliente(filtro.idCliente()));

        PaginaResponse<OrdenResumenResponse> pagina =
                PaginaResponse.de(ordenRepository.findAll(spec, pageable).map(mapper::aResumen));

        log.debug("Listado de órdenes", kv("evento", "ORDEN_LISTADO"), kv("estado", filtro.estado()),
                kv("fechaInicio", inicio), kv("fechaFin", fin), kv("total", pagina.totalElementos()));
        return pagina;
    }
}
