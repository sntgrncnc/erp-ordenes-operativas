# Sistema de Gestión de Órdenes Operativas

Servicio de backend para la gestión del ciclo de vida de órdenes operativas en empresas de servicios públicos (energía, agua, gas). Implementado con **Spring Boot 3.4 (Java 21)**, persistencia en **Oracle Database 23ai Free**, lógica transaccional en **PL/SQL** y orquestación con **Docker Compose**.

---

## 1. Arquitectura

```
                      Peticiones HTTP / JSON
                                │
                                ▼
┌───────────────────────────────────────────────────────────────┐
│ CONTENEDOR: orden-service (Spring Boot 3.4 / Java 21)         │
│                                                               │
│   • CorrelationIdFilter  ──► Propaga X-Correlation-Id al MDC  │
│   • OrdenController      ──► Exposición de endpoints REST     │
│   • GlobalExceptionHandler─► Respuestas de error unificadas   │
│   • OrdenService         ──► Frontera transaccional           │
│   • OrdenRepository      ──► Consultas y listados paginados   │
│   • OrdenEstadoProcedimiento ──► Invocación JDBC a PL/SQL     │
└───────────────────────────────┬───────────────────────────────┘
                                │ Pool HikariCP
                                ▼
┌───────────────────────────────────────────────────────────────┐
│ CONTENEDOR: ordenes-oracle (Oracle Database 23ai Free)        │
│                                                               │
│   • PKG_ORDEN.ACTUALIZAR_ESTADO (Procedimiento PL/SQL):       │
│       - Bloqueo pesimista (SELECT ... FOR UPDATE WAIT 5)      │
│       - Control de concurrencia optimista (validación versión)│
│       - Validación de transiciones contra TRANSICION_ESTADO   │
│       - Inserción de trazabilidad en ORDEN_HISTORICO          │
│       - Registro autónomo de fallos en ORDEN_ERROR_LOG        │
│   • Tablas: CLIENTE, ORDEN, ORDEN_HISTORICO, TRANSICION_ESTADO│
└───────────────────────────────────────────────────────────────┘
```

---

## 2. Estrategia de Base de Datos para Alto Volumen

Diseñado para soportar un volumen proyectado de **1 millón de órdenes mensuales**:

- **Particionamiento por Intervalo Mensual**: Las tablas `ORDEN` y `ORDEN_HISTORICO` se particionan automáticamente por mes según la fecha de creación. Esto permite *partition pruning* en las consultas por rango de fechas y mantenimiento por partición (purga o archivado eficiente).
- **Índice Global Hash para la PK de `ORDEN`**: Particionado en 16 segmentos para distribuir las inserciones secuenciales y mitigar la contención de bloque caliente (*right-hand index contention*).
- **Secuencia con Cache Elevado (`SEQ_ORDEN`)**: Incremento de 50 alineado con el asignador `pooled` de JPA para reducir viajes a disco en inserciones masivas.
- **Trazabilidad e Inmutabilidad**: `ORDEN_HISTORICO` opera como tabla de solo inserción (*append-only*), protegida contra modificaciones y eliminaciones mediante trigger.
- **Índice de Organización (IOT)**: La tabla `TRANSICION_ESTADO` utiliza organización por índice para lecturas inmediatas en memoria.

---

## 3. Endpoints de la API REST

Base URL: `http://localhost:8080`

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/orden` | Crear una nueva orden operativa |
| `GET` | `/orden/{id}` | Consultar detalle de una orden y su trazabilidad histórica |
| `PUT` | `/orden/{id}/estado` | Actualizar estado mediante el procedimiento PL/SQL |
| `GET` | `/orden` | Listar órdenes con paginación (`page`, `size`) y filtros (`estado`, `fechaInicio`, `fechaFin`, `idCliente`) |
| `GET` | `/swagger-ui.html` | Documentación interactiva Swagger UI / OpenAPI 3 |
| `GET` | `/actuator/health` | Estado del servicio y conectividad a base de datos |

---

## 4. Portal Web de Operaciones

Disponible en el navegador en: **`http://localhost:3000`**

Proporciona una interfaz visual moderna para los flujos operativos:
- **Bandeja de Órdenes**: Búsqueda con filtros por estado, fechas y paginación.
- **Nueva Orden**: Registro asistido de órdenes con selección de cliente, tipo de trabajo y prioridad.
- **Ficha y Trazabilidad**: Visualización detallada de la orden y línea de tiempo con su historial inmutable de auditoría.
- **Gestión de Estado**: Transición de estados con validación de reglas y control de concurrencia.

---

## 5. Despliegue con Docker

### Iniciar servicios
```bash
docker compose up -d
```
*Oracle inicializa automáticamente el esquema, el paquete PL/SQL y los catálogos en el primer arranque. La API espera a que la base de datos complete su inicialización.*

### Verificar estado
```bash
docker compose ps
```

### Consultar logs estructurados
```bash
docker compose logs -f orden-service
```

### Detener servicios
```bash
docker compose down
```

---

## 6. Ejemplos de Uso con cURL

### 1. Crear Orden
```bash
curl -X POST http://localhost:8080/orden \
  -H "Content-Type: application/json" \
  -H "X-Usuario: operador1" \
  -d '{
    "idCliente": 1,
    "tipoOrden": "INSTALACION",
    "descripcion": "Instalación de nueva acometida eléctrica monofásica",
    "direccionServicio": "Cra 7 # 45-10, Bogotá",
    "prioridad": "ALTA"
  }'
```

### 2. Consultar Orden por ID
```bash
curl -X GET http://localhost:8080/orden/1 \
  -H "X-Usuario: supervisor"
```

### 3. Actualizar Estado (Transición válida: REGISTRADA -> ASIGNADA)
```bash
curl -X PUT http://localhost:8080/orden/1/estado \
  -H "Content-Type: application/json" \
  -H "X-Usuario: despachador" \
  -d '{
    "estado": "ASIGNADA",
    "observacion": "Asignada a cuadrilla operativa en terreno",
    "version": 0
  }'
```

### 4. Transición No Permitida (Ej: ASIGNADA -> CERRADA, retorna 422)
```bash
curl -i -X PUT http://localhost:8080/orden/1/estado \
  -H "Content-Type: application/json" \
  -H "X-Usuario: despachador" \
  -d '{
    "estado": "CERRADA",
    "observacion": "Cierre directo",
    "version": 1
  }'
```

### 5. Conflicto de Concurrencia (Versión desactualizada, retorna 409)
```bash
curl -i -X PUT http://localhost:8080/orden/1/estado \
  -H "Content-Type: application/json" \
  -H "X-Usuario: despachador" \
  -d '{
    "estado": "EN_EJECUCION",
    "observacion": "Inicio de labores",
    "version": 0
  }'
```

### 6. Listar Órdenes con Filtros y Paginación
```bash
curl -X GET "http://localhost:8080/orden?estado=ASIGNADA&fechaInicio=2026-10-01&fechaFin=2026-10-31&page=0&size=10"
```

---

## 7. Pruebas Unitarias

El proyecto incluye pruebas unitarias para el controlador, el servicio y el traductor de excepciones Oracle. Se ejecutan automáticamente durante la construcción de la imagen Docker o localmente mediante:

```bash
mvn test
```
