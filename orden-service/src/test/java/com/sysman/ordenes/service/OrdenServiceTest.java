package com.sysman.ordenes.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenServiceTest {

    @Mock
    private OrdenRepository ordenRepository;

    @Mock
    private OrdenHistoricoRepository historicoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private TipoOrdenRepository tipoOrdenRepository;

    @Mock
    private OrdenEstadoProcedimiento estadoProcedimiento;

    @Spy
    private OrdenMapper mapper = new OrdenMapper();

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private OrdenService ordenService;

    private Cliente clienteActivo;
    private TipoOrden tipoInstalacion;
    private Orden ordenEjemplo;

    @BeforeEach
    void setUp() {
        clienteActivo = new Cliente();
        clienteActivo.setId(1L);
        clienteActivo.setTipoDocumento("CC");
        clienteActivo.setNumeroDocumento("1020304050");
        clienteActivo.setNombre("María Fernanda Gómez");
        clienteActivo.setEstado("ACTIVO");
        clienteActivo.setVersion(0L);

        tipoInstalacion = new TipoOrden();
        tipoInstalacion.setCodigo("INSTALACION");
        tipoInstalacion.setDescripcion("Instalación nuevo servicio");
        tipoInstalacion.setSlaHoras(120);
        tipoInstalacion.setActivo("S");

        ordenEjemplo = new Orden();
        ordenEjemplo.setId(100L);
        ordenEjemplo.setCliente(clienteActivo);
        ordenEjemplo.setTipoOrden(tipoInstalacion);
        ordenEjemplo.setEstado(EstadoOrden.REGISTRADA);
        ordenEjemplo.setPrioridad(Prioridad.ALTA);
        ordenEjemplo.setDescripcion("Instalar medidor de agua");
        ordenEjemplo.setDireccionServicio("Calle 100 # 15-20");
        ordenEjemplo.setVersion(0L);
        ordenEjemplo.setCreadoPor("admin");
        ordenEjemplo.setFechaCreacion(LocalDateTime.now(clock));
        ordenEjemplo.setFechaEstado(LocalDateTime.now(clock));
    }

    @Nested
    @DisplayName("Crear Orden")
    class CrearOrdenTests {

        @Test
        @DisplayName("Crea la orden exitosamente y registra su histórico de creación")
        void creaOrdenExitosamente() {
            CrearOrdenRequest req = new CrearOrdenRequest(
                    1L, "INSTALACION", "Instalar medidor de agua", "Calle 100 # 15-20", Prioridad.ALTA);

            when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteActivo));
            when(tipoOrdenRepository.findById("INSTALACION")).thenReturn(Optional.of(tipoInstalacion));
            when(ordenRepository.saveAndFlush(any(Orden.class))).thenReturn(ordenEjemplo);

            OrdenHistorico hist = OrdenHistorico.deCreacion(ordenEjemplo, "admin", "corr-123");
            hist.setId(500L);
            when(historicoRepository.save(any(OrdenHistorico.class))).thenReturn(hist);

            OrdenResponse res = ordenService.crear(req);

            assertThat(res).isNotNull();
            assertThat(res.id()).isEqualTo(100L);
            assertThat(res.estado()).isEqualTo(EstadoOrden.REGISTRADA);
            assertThat(res.prioridad()).isEqualTo(Prioridad.ALTA);
            assertThat(res.cliente().nombre()).isEqualTo("María Fernanda Gómez");
            assertThat(res.historial()).hasSize(1);
            assertThat(res.historial().get(0).estadoNuevo()).isEqualTo(EstadoOrden.REGISTRADA);

            verify(ordenRepository).saveAndFlush(any(Orden.class));
            verify(historicoRepository).save(any(OrdenHistorico.class));
        }

        @Test
        @DisplayName("Falla al crear si el cliente no existe")
        void fallaSiClienteNoExiste() {
            CrearOrdenRequest req = new CrearOrdenRequest(
                    99L, "INSTALACION", "Instalar", "Calle 1", Prioridad.MEDIA);

            when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ordenService.crear(req))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("El cliente 99 no existe");
        }

        @Test
        @DisplayName("Falla al crear si el cliente está inactivo")
        void fallaSiClienteInactivo() {
            clienteActivo.setEstado("INACTIVO");
            CrearOrdenRequest req = new CrearOrdenRequest(
                    1L, "INSTALACION", "Instalar", "Calle 1", Prioridad.MEDIA);

            when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteActivo));

            assertThatThrownBy(() -> ordenService.crear(req))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("está inactivo");
        }

        @Test
        @DisplayName("Falla al crear si el tipo de orden no existe")
        void fallaSiTipoOrdenNoExiste() {
            CrearOrdenRequest req = new CrearOrdenRequest(
                    1L, "NO_EXISTE", "Instalar", "Calle 1", Prioridad.MEDIA);

            when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteActivo));
            when(tipoOrdenRepository.findById("NO_EXISTE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ordenService.crear(req))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("El tipo de orden 'NO_EXISTE' no existe");
        }

        @Test
        @DisplayName("Falla al crear si el tipo de orden está inactivo")
        void fallaSiTipoOrdenInactivo() {
            tipoInstalacion.setActivo("N");
            CrearOrdenRequest req = new CrearOrdenRequest(
                    1L, "INSTALACION", "Instalar", "Calle 1", Prioridad.MEDIA);

            when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteActivo));
            when(tipoOrdenRepository.findById("INSTALACION")).thenReturn(Optional.of(tipoInstalacion));

            assertThatThrownBy(() -> ordenService.crear(req))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no está activo");
        }
    }

    @Nested
    @DisplayName("Consultar Orden")
    class ConsultarOrdenTests {

        @Test
        @DisplayName("Retorna la orden y su trazabilidad histórica")
        void consultaOrdenConHistorial() {
            when(ordenRepository.findConDetalleById(100L)).thenReturn(Optional.of(ordenEjemplo));

            OrdenHistorico h1 = new OrdenHistorico();
            h1.setId(1L);
            h1.setIdOrden(100L);
            h1.setEstadoAnterior(null);
            h1.setEstadoNuevo(EstadoOrden.REGISTRADA);
            h1.setVersionOrden(0L);
            h1.setUsuario("admin");
            h1.setOrigen("API");
            h1.setFechaCambio(LocalDateTime.now(clock));

            when(historicoRepository.findByIdOrdenOrderByFechaCambioAscIdAsc(100L))
                    .thenReturn(List.of(h1));

            OrdenResponse res = ordenService.obtener(100L);

            assertThat(res.id()).isEqualTo(100L);
            assertThat(res.historial()).hasSize(1);
            assertThat(res.historial().get(0).estadoNuevo()).isEqualTo(EstadoOrden.REGISTRADA);
        }

        @Test
        @DisplayName("Lanza 404 si la orden no existe")
        void lanza404SiNoExiste() {
            when(ordenRepository.findConDetalleById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ordenService.obtener(999L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("La orden 999 no existe");
        }
    }

    @Nested
    @DisplayName("Actualizar Estado")
    class ActualizarEstadoTests {

        @Test
        @DisplayName("Ejecuta el procedimiento PL/SQL y recarga la orden actualizada")
        void actualizaEstadoExitosamente() {
            ActualizarEstadoRequest req = new ActualizarEstadoRequest(EstadoOrden.ASIGNADA, "Asignado a cuadrilla 4", 0L);

            when(estadoProcedimiento.ejecutar(any(CambioEstado.class)))
                    .thenReturn(new ResultadoCambioEstado(1L, 20L));

            Orden ordenActualizada = new Orden();
            ordenActualizada.setId(100L);
            ordenActualizada.setCliente(clienteActivo);
            ordenActualizada.setTipoOrden(tipoInstalacion);
            ordenActualizada.setEstado(EstadoOrden.ASIGNADA);
            ordenActualizada.setPrioridad(Prioridad.ALTA);
            ordenActualizada.setDescripcion("Instalar medidor");
            ordenActualizada.setVersion(1L);
            ordenActualizada.setCreadoPor("admin");
            ordenActualizada.setFechaCreacion(LocalDateTime.now(clock));
            ordenActualizada.setFechaEstado(LocalDateTime.now(clock));

            when(ordenRepository.findConDetalleById(100L)).thenReturn(Optional.of(ordenActualizada));
            when(historicoRepository.findByIdOrdenOrderByFechaCambioAscIdAsc(100L)).thenReturn(List.of());

            OrdenResponse res = ordenService.actualizarEstado(100L, req);

            assertThat(res.estado()).isEqualTo(EstadoOrden.ASIGNADA);
            assertThat(res.version()).isEqualTo(1L);

            ArgumentCaptor<CambioEstado> captor = ArgumentCaptor.forClass(CambioEstado.class);
            verify(estadoProcedimiento).ejecutar(captor.capture());
            assertThat(captor.getValue().idOrden()).isEqualTo(100L);
            assertThat(captor.getValue().estadoNuevo()).isEqualTo(EstadoOrden.ASIGNADA);
            assertThat(captor.getValue().versionEsperada()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Listar Órdenes")
    class ListarOrdenesTests {

        @Test
        @DisplayName("Lista órdenes con paginación")
        void listaConPaginacion() {
            LocalDate hoy = LocalDate.now(clock);
            FiltroOrden filtro = new FiltroOrden(EstadoOrden.REGISTRADA, hoy.minusDays(5), hoy, 1L);
            Pageable pageable = PageRequest.of(0, 10);

            Page<Orden> page = new PageImpl<>(List.of(ordenEjemplo), pageable, 1);
            when(ordenRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            PaginaResponse<OrdenResumenResponse> res = ordenService.listar(filtro, pageable);

            assertThat(res.totalElementos()).isEqualTo(1);
            assertThat(res.contenido()).hasSize(1);
            assertThat(res.contenido().get(0).estado()).isEqualTo(EstadoOrden.REGISTRADA);
        }

        @Test
        @DisplayName("Rechaza si fechaInicio es posterior a fechaFin")
        void rechazaFechaInicioMayorAFechaFin() {
            LocalDate hoy = LocalDate.now(clock);
            FiltroOrden filtro = new FiltroOrden(null, hoy, hoy.minusDays(1), null);

            assertThatThrownBy(() -> ordenService.listar(filtro, PageRequest.of(0, 10)))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("fechaInicio no puede ser posterior a fechaFin");
        }

        @Test
        @DisplayName("Rechaza si el rango de fechas supera los 366 días")
        void rechazaRangoMayorAUnAno() {
            LocalDate hoy = LocalDate.now(clock);
            FiltroOrden filtro = new FiltroOrden(null, hoy.minusDays(400), hoy, null);

            assertThatThrownBy(() -> ordenService.listar(filtro, PageRequest.of(0, 10)))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("no puede superar 366 días");
        }
    }
}
