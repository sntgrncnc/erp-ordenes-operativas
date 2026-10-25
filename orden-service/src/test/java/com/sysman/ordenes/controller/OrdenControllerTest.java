package com.sysman.ordenes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sysman.ordenes.domain.EstadoOrden;
import com.sysman.ordenes.domain.Prioridad;
import com.sysman.ordenes.dto.ActualizarEstadoRequest;
import com.sysman.ordenes.dto.CrearOrdenRequest;
import com.sysman.ordenes.dto.OrdenResponse;
import com.sysman.ordenes.dto.OrdenResponse.ClienteResumen;
import com.sysman.ordenes.dto.OrdenResponse.TipoOrdenResumen;
import com.sysman.ordenes.dto.OrdenResumenResponse;
import com.sysman.ordenes.dto.PaginaResponse;
import com.sysman.ordenes.exception.ConflictoConcurrenciaException;
import com.sysman.ordenes.exception.GlobalExceptionHandler;
import com.sysman.ordenes.exception.RecursoNoEncontradoException;
import com.sysman.ordenes.exception.TransicionEstadoInvalidaException;
import com.sysman.ordenes.service.OrdenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrdenControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private OrdenService ordenService;

    @InjectMocks
    private OrdenController ordenController;

    private OrdenResponse ordenResponseMock;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ordenController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        ordenResponseMock = new OrdenResponse(
                100L,
                new ClienteResumen(1L, "CC", "1020304050", "María Gómez"),
                new TipoOrdenResumen("INSTALACION", "Instalación de nuevo servicio", 120),
                EstadoOrden.REGISTRADA,
                Prioridad.ALTA,
                "Instalar medidor",
                "Calle 100 # 15-20",
                LocalDateTime.of(2026, 10, 25, 12, 0),
                0L,
                "admin",
                LocalDateTime.of(2026, 10, 25, 12, 0),
                null,
                null,
                List.of()
        );
    }

    @Nested
    @DisplayName("POST /orden")
    class CrearOrdenEndpointTests {

        @Test
        @DisplayName("Crea orden con 201 y cabecera Location")
        void creaOrden201() throws Exception {
            CrearOrdenRequest req = new CrearOrdenRequest(
                    1L, "INSTALACION", "Instalar medidor", "Calle 100", Prioridad.ALTA);

            when(ordenService.crear(any(CrearOrdenRequest.class))).thenReturn(ordenResponseMock);

            mockMvc.perform(post("/orden")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/orden/100")))
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.estado").value("REGISTRADA"));
        }

        @Test
        @DisplayName("Rechaza solicitud con 400 si faltan campos obligatorios")
        void rechazaCamposFaltantes400() throws Exception {
            // Cliente nulo y descripción vacía
            CrearOrdenRequest req = new CrearOrdenRequest(null, "", "", null, null);

            mockMvc.perform(post("/orden")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.detalles", hasSize(3)));
        }
    }

    @Nested
    @DisplayName("GET /orden/{id}")
    class ConsultarOrdenEndpointTests {

        @Test
        @DisplayName("Retorna 200 y detalle de orden")
        void retorna200SiExiste() throws Exception {
            when(ordenService.obtener(100L)).thenReturn(ordenResponseMock);

            mockMvc.perform(get("/orden/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.cliente.nombre").value("María Gómez"));
        }

        @Test
        @DisplayName("Retorna 404 si la orden no existe")
        void retorna404SiNoExiste() throws Exception {
            when(ordenService.obtener(999L))
                    .thenThrow(new RecursoNoEncontradoException("La orden 999 no existe"));

            mockMvc.perform(get("/orden/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                    .andExpect(jsonPath("$.mensaje").value("La orden 999 no existe"));
        }
    }

    @Nested
    @DisplayName("PUT /orden/{id}/estado")
    class ActualizarEstadoEndpointTests {

        @Test
        @DisplayName("Retorna 200 al actualizar estado exitosamente")
        void actualizaEstado200() throws Exception {
            ActualizarEstadoRequest req = new ActualizarEstadoRequest(EstadoOrden.ASIGNADA, "Asignado", 0L);

            OrdenResponse actualizada = new OrdenResponse(
                    100L, ordenResponseMock.cliente(), ordenResponseMock.tipoOrden(),
                    EstadoOrden.ASIGNADA, Prioridad.ALTA, "Instalar medidor", "Calle 100",
                    LocalDateTime.of(2026, 10, 25, 12, 30), 1L, "admin",
                    LocalDateTime.of(2026, 10, 25, 12, 0), "operador",
                    LocalDateTime.of(2026, 10, 25, 12, 30), List.of()
            );

            when(ordenService.actualizarEstado(eq(100L), any(ActualizarEstadoRequest.class)))
                    .thenReturn(actualizada);

            mockMvc.perform(put("/orden/100/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ASIGNADA"))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        @DisplayName("Retorna 422 si la transición es inválida")
        void retorna422TransicionInvalida() throws Exception {
            ActualizarEstadoRequest req = new ActualizarEstadoRequest(EstadoOrden.CERRADA, "Cerrar", 0L);

            when(ordenService.actualizarEstado(eq(100L), any(ActualizarEstadoRequest.class)))
                    .thenThrow(new TransicionEstadoInvalidaException("Transición no permitida: REGISTRADA -> CERRADA", null));

            mockMvc.perform(put("/orden/100/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                    .andExpect(jsonPath("$.mensaje").value("Transición no permitida: REGISTRADA -> CERRADA"));
        }

        @Test
        @DisplayName("Retorna 409 ante conflicto de concurrencia de versión")
        void retorna409ConflictoConcurrencia() throws Exception {
            ActualizarEstadoRequest req = new ActualizarEstadoRequest(EstadoOrden.ASIGNADA, "Asignar", 0L);

            when(ordenService.actualizarEstado(eq(100L), any(ActualizarEstadoRequest.class)))
                    .thenThrow(ConflictoConcurrenciaException.versionDesactualizada(
                            "La orden 100 fue modificada por otro usuario", null));

            mockMvc.perform(put("/orden/100/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONFLICTO_CONCURRENCIA"));
        }
    }

    @Nested
    @DisplayName("GET /orden (Listado)")
    class ListarOrdenesEndpointTests {

        @Test
        @DisplayName("Retorna 200 con resultados paginados")
        void retorna200Listado() throws Exception {
            OrdenResumenResponse resumen = new OrdenResumenResponse(
                    100L, 1L, "María Gómez", "INSTALACION", EstadoOrden.REGISTRADA,
                    Prioridad.ALTA, LocalDateTime.now(), LocalDateTime.now(), 0L
            );
            PaginaResponse<OrdenResumenResponse> pagina = new PaginaResponse<>(
                    List.of(resumen), 0, 20, 1L, 1, true
            );

            when(ordenService.listar(any(), any(Pageable.class))).thenReturn(pagina);

            mockMvc.perform(get("/orden")
                            .param("estado", "REGISTRADA")
                            .param("fechaInicio", "2026-10-01")
                            .param("fechaFin", "2026-10-25"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(100));
        }
    }
}
