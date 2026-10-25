// =============================================================================
// GESTIÓN OPERATIVA DE ÓRDENES - LÓGICA DE INTERFAZ MINIMALISTA
// =============================================================================

const state = {
    paginaActual: 0,
    tamanoPagina: 10,
    totalPaginas: 1,
    ordenSeleccionada: null,
    filtros: {
        estado: '',
        fechaInicio: '',
        fechaFin: ''
    }
};

// Inicialización al cargar el DOM
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initTabs();
    initEventos();
    verificarEstadoServicio();
    setInterval(verificarEstadoServicio, 15000);
    cargarOrdenes();
});

// -----------------------------------------------------------------------------
// CONTROL DE TEMA
// -----------------------------------------------------------------------------
function initTheme() {
    const savedTheme = localStorage.getItem('theme-mode') || 'dark';
    aplicarTema(savedTheme);

    const btn = document.getElementById('btnThemeToggle');
    if (btn) {
        btn.addEventListener('click', () => {
            const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';
            const nextTheme = currentTheme === 'dark' ? 'light' : 'dark';
            aplicarTema(nextTheme);
        });
    }
}

function aplicarTema(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme-mode', theme);
}

// -----------------------------------------------------------------------------
// NAVEGACIÓN ENTRE PESTAÑAS
// -----------------------------------------------------------------------------
function initTabs() {
    const tabs = document.querySelectorAll('.nav-btn, .nav-tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const targetId = tab.dataset.tab;
            activarTab(targetId);
        });
    });
}

function activarTab(tabId) {
    document.querySelectorAll('.nav-btn, .nav-tab').forEach(t => {
        t.classList.toggle('active', t.dataset.tab === tabId);
    });
    document.querySelectorAll('.tab-pane').forEach(p => {
        p.classList.toggle('active', p.id === tabId);
    });
}

// -----------------------------------------------------------------------------
// EVENTOS Y FORMULARIOS
// -----------------------------------------------------------------------------
function initEventos() {
    // Filtros de listado
    document.getElementById('formFiltros').addEventListener('submit', (e) => {
        e.preventDefault();
        state.filtros.estado = document.getElementById('filtroEstado').value;
        state.filtros.fechaInicio = document.getElementById('filtroFechaInicio').value;
        state.filtros.fechaFin = document.getElementById('filtroFechaFin').value;
        state.paginaActual = 0;
        cargarOrdenes();
    });

    document.getElementById('btnLimpiarFiltros').addEventListener('click', () => {
        document.getElementById('formFiltros').reset();
        state.filtros = { estado: '', fechaInicio: '', fechaFin: '' };
        state.paginaActual = 0;
        cargarOrdenes();
    });

    document.getElementById('btnRefrescarTabla').addEventListener('click', () => {
        cargarOrdenes();
    });

    // Paginación
    document.getElementById('btnPagAnterior').addEventListener('click', () => {
        if (state.paginaActual > 0) {
            state.paginaActual--;
            cargarOrdenes();
        }
    });

    document.getElementById('btnPagSiguiente').addEventListener('click', () => {
        if (state.paginaActual < state.totalPaginas - 1) {
            state.paginaActual++;
            cargarOrdenes();
        }
    });

    // Crear orden
    document.getElementById('formCrearOrden').addEventListener('submit', async (e) => {
        e.preventDefault();
        await registrarNuevaOrden();
    });

    // Buscador directo por ID
    document.getElementById('formBuscarOrdenDirecta').addEventListener('submit', (e) => {
        e.preventDefault();
        const id = document.getElementById('inputBuscarId').value.trim();
        if (id) {
            consultarOrdenPorId(id);
        }
    });

    // Modal de cambio de estado
    document.getElementById('btnAbrirCambioEstado').addEventListener('click', () => {
        if (state.ordenSeleccionada) {
            abrirModalCambioEstado(state.ordenSeleccionada);
        }
    });

    document.getElementById('btnCerrarModal').addEventListener('click', cerrarModalCambioEstado);
    document.getElementById('btnCancelarModal').addEventListener('click', cerrarModalCambioEstado);

    document.getElementById('formActualizarEstado').addEventListener('submit', async (e) => {
        e.preventDefault();
        await ejecutarCambioEstado();
    });
}

// -----------------------------------------------------------------------------
// ESTADO DEL SERVICIO
// -----------------------------------------------------------------------------
async function verificarEstadoServicio() {
    const statusWrap = document.getElementById('systemStatus');
    const statusText = statusWrap.querySelector('.status-label, .status-text');
    try {
        const res = await fetch('/api/estado-servicio');
        if (res.ok) {
            const data = await res.json();
            if (data.status === 'UP') {
                statusWrap.classList.add('online');
                if (statusText) statusText.textContent = 'En línea';
                return;
            }
        }
        statusWrap.classList.remove('online');
        if (statusText) statusText.textContent = 'Degradado';
    } catch {
        statusWrap.classList.remove('online');
        if (statusText) statusText.textContent = 'Desconectado';
    }
}

// -----------------------------------------------------------------------------
// 1. BANDEJA DE ÓRDENES
// -----------------------------------------------------------------------------
async function cargarOrdenes() {
    const tbody = document.getElementById('tablaOrdenesBody');
    tbody.innerHTML = '<tr><td colspan="7" class="empty-cell">Cargando órdenes operativas...</td></tr>';

    const params = new URLSearchParams({
        page: state.paginaActual,
        size: state.tamanoPagina
    });

    if (state.filtros.estado) params.set('estado', state.filtros.estado);
    if (state.filtros.fechaInicio) params.set('fechaInicio', state.filtros.fechaInicio);
    if (state.filtros.fechaFin) params.set('fechaFin', state.filtros.fechaFin);

    try {
        const res = await fetch(`/api/ordenes?${params.toString()}`);
        if (!res.ok) {
            const err = await res.json();
            mostrarToast('error', 'Error al consultar', err.mensaje || 'Solicitud no procesada');
            tbody.innerHTML = `<tr><td colspan="7" class="empty-cell">${escapeHtml(err.mensaje || 'Error al consultar')}</td></tr>`;
            return;
        }

        const data = await res.json();
        renderizarTablaOrdenes(data);
    } catch (err) {
        mostrarToast('error', 'Fallo de conexión', 'No fue posible obtener las órdenes');
        tbody.innerHTML = '<tr><td colspan="7" class="empty-cell">Error de conexión con el servicio</td></tr>';
    }
}

function renderizarTablaOrdenes(data) {
    const tbody = document.getElementById('tablaOrdenesBody');
    const totalBadge = document.getElementById('totalRegistrosBadge');
    const pagInfo = document.getElementById('paginacionInfo');
    const btnAnt = document.getElementById('btnPagAnterior');
    const btnSig = document.getElementById('btnPagSiguiente');

    state.totalPaginas = Math.max(data.totalPaginas || 1, 1);
    totalBadge.textContent = `${data.totalElementos} ${data.totalElementos === 1 ? 'registro' : 'registros'}`;
    pagInfo.textContent = `Página ${data.pagina + 1} de ${state.totalPaginas}`;

    btnAnt.disabled = data.pagina === 0;
    btnSig.disabled = data.ultima || data.pagina >= state.totalPaginas - 1;

    if (!data.contenido || data.contenido.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="empty-cell">No se encontraron órdenes para los filtros aplicados.</td></tr>';
        return;
    }

    tbody.innerHTML = data.contenido.map(o => `
        <tr>
            <td><strong>#${o.id}</strong></td>
            <td>${escapeHtml(o.nombreCliente)}</td>
            <td><span class="sub">${escapeHtml(o.tipoOrden)}</span></td>
            <td><span class="badge-prioridad prio-${o.prioridad}">${o.prioridad}</span></td>
            <td><span class="badge-status badge-${o.estado}">${o.estado.replace('_', ' ')}</span></td>
            <td class="sub">${formatearFechaCorta(o.fechaEstado)}</td>
            <td class="text-right">
                <button class="btn btn-sm btn-outline" onclick="consultarOrdenPorId(${o.id})">
                    Detalle
                </button>
            </td>
        </tr>
    `).join('');
}

// -----------------------------------------------------------------------------
// 2. CREAR NUEVA ORDEN
// -----------------------------------------------------------------------------
async function registrarNuevaOrden() {
    const btnGuardar = document.getElementById('btnGuardarOrden');
    btnGuardar.disabled = true;
    btnGuardar.textContent = 'Registrando...';

    const payload = {
        idCliente: parseInt(document.getElementById('nuevoCliente').value, 10),
        tipoOrden: document.getElementById('nuevoTipo').value,
        prioridad: document.getElementById('nuevaPrioridad').value,
        direccionServicio: document.getElementById('nuevaDireccion').value.trim() || null,
        descripcion: document.getElementById('nuevaDescripcion').value.trim()
    };

    try {
        const res = await fetch('/api/ordenes', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Usuario': 'operador_central'
            },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (res.status === 201) {
            mostrarToast('success', 'Orden Registrada', `Se generó la orden #${data.id} con éxito.`);
            document.getElementById('formCrearOrden').reset();
            mostrarDetalleOrden(data);
            activarTab('tab-detalle');
            cargarOrdenes();
        } else {
            const detalle = data.detalles && data.detalles.length > 0
                ? data.detalles.map(d => `${d.campo}: ${d.mensaje}`).join(', ')
                : data.mensaje;
            mostrarToast('error', 'No se pudo crear la orden', detalle || 'Datos inválidos');
        }
    } catch {
        mostrarToast('error', 'Error de red', 'No se pudo emitir la orden');
    } finally {
        btnGuardar.disabled = false;
        btnGuardar.textContent = 'Guardar y Emitir Orden';
    }
}

// -----------------------------------------------------------------------------
// 3. CONSULTAR DETALLE Y TRAZABILIDAD
// -----------------------------------------------------------------------------
async function consultarOrdenPorId(id) {
    activarTab('tab-detalle');
    document.getElementById('inputBuscarId').value = id;

    try {
        const res = await fetch(`/api/ordenes/${id}`);
        if (!res.ok) {
            const err = await res.json();
            mostrarToast('warning', 'No encontrada', err.mensaje || `La orden #${id} no existe`);
            ocultarDetalleOrden();
            return;
        }

        const data = await res.json();
        mostrarDetalleOrden(data);
    } catch {
        mostrarToast('error', 'Error de conexión', 'No fue posible consultar la orden');
        ocultarDetalleOrden();
    }
}

function mostrarDetalleOrden(o) {
    state.ordenSeleccionada = o;

    document.getElementById('detalleVacio').style.display = 'none';
    document.getElementById('detalleContenedor').style.display = 'grid';

    document.getElementById('detId').textContent = `Orden #${o.id}`;
    document.getElementById('detEstadoBadge').innerHTML = `
        <span class="badge-status badge-${o.estado}">${o.estado.replace('_', ' ')}</span>
    `;

    document.getElementById('detClienteNombre').textContent = o.cliente ? o.cliente.nombre : '--';
    document.getElementById('detClienteDoc').textContent = o.cliente ? `${o.cliente.tipoDocumento} ${o.cliente.numeroDocumento}` : '--';

    document.getElementById('detTipoCodigo').textContent = o.tipoOrden ? o.tipoOrden.codigo : '--';
    document.getElementById('detTipoSla').textContent = o.tipoOrden ? `${o.tipoOrden.descripcion} (${o.tipoOrden.slaHoras}h)` : '--';

    document.getElementById('detPrioridad').innerHTML = `<span class="badge-prioridad prio-${o.prioridad}">${o.prioridad}</span>`;
    document.getElementById('detVersion').textContent = `v${o.version}`;
    document.getElementById('detDireccion').textContent = o.direccionServicio || 'No indicada';
    document.getElementById('detDescripcion').textContent = o.descripcion || '--';
    document.getElementById('detFechaCreacion').textContent = `${formatearFecha(o.fechaCreacion)} (${escapeHtml(o.creadoPor || 'sistema')})`;
    document.getElementById('detFechaEstado').textContent = `${formatearFecha(o.fechaEstado)}${o.modificadoPor ? ' (' + escapeHtml(o.modificadoPor) + ')' : ''}`;

    renderizarTimeline(o.historial || []);
}

function ocultarDetalleOrden() {
    state.ordenSeleccionada = null;
    document.getElementById('detalleContenedor').style.display = 'none';
    document.getElementById('detalleVacio').style.display = 'block';
}

function renderizarTimeline(historial) {
    const container = document.getElementById('timelineContainer');
    if (!historial || historial.length === 0) {
        container.innerHTML = '<p class="sub">Sin registros históricos.</p>';
        return;
    }

    container.innerHTML = historial.map(h => {
        const transicion = h.estadoAnterior
            ? `${h.estadoAnterior.replace('_', ' ')} ➔ ${h.estadoNuevo.replace('_', ' ')}`
            : `Registro (${h.estadoNuevo.replace('_', ' ')})`;

        return `
            <div class="timeline-item">
                <div class="timeline-dot"></div>
                <div class="timeline-header">
                    <span class="timeline-transition">${escapeHtml(transicion)}</span>
                    <span class="timeline-date">${formatearFecha(h.fechaCambio)}</span>
                </div>
                <div class="timeline-body">
                    <div class="timeline-obs">${escapeHtml(h.observacion || 'Sin observaciones.')}</div>
                    <div class="timeline-meta">
                        <span><strong>Usuario:</strong> ${escapeHtml(h.usuario)}</span>
                        <span><strong>Versión:</strong> v${h.versionOrden}</span>
                        ${h.idTransaccion ? `<span><strong>Tx:</strong> ${escapeHtml(h.idTransaccion)}</span>` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

// -----------------------------------------------------------------------------
// 4. CAMBIO DE ESTADO
// -----------------------------------------------------------------------------
function abrirModalCambioEstado(orden) {
    document.getElementById('modalOrdenSub').textContent = `Orden #${orden.id} - ${orden.tipoOrden ? orden.tipoOrden.codigo : ''}`;
    document.getElementById('modalEstadoActualPill').innerHTML = `
        <span class="badge-status badge-${orden.estado}">${orden.estado.replace('_', ' ')}</span>
    `;

    document.getElementById('modalNuevoEstado').value = '';
    document.getElementById('modalObservacion').value = '';
    document.getElementById('modalVersion').value = orden.version;

    document.getElementById('modalCambioEstado').classList.add('open');
}

function cerrarModalCambioEstado() {
    document.getElementById('modalCambioEstado').classList.remove('open');
}

async function ejecutarCambioEstado() {
    if (!state.ordenSeleccionada) return;

    const btn = document.getElementById('btnConfirmarCambio');
    btn.disabled = true;
    btn.textContent = 'Guardando...';

    const nuevoEstado = document.getElementById('modalNuevoEstado').value;
    const observacion = document.getElementById('modalObservacion').value.trim();
    const version = parseInt(document.getElementById('modalVersion').value, 10);

    const payload = {
        estado: nuevoEstado,
        observacion: observacion || null,
        version: isNaN(version) ? null : version
    };

    try {
        const res = await fetch(`/api/ordenes/${state.ordenSeleccionada.id}/estado`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'X-Usuario': 'despachador_central'
            },
            body: JSON.stringify(payload)
        });

        const data = await res.json();

        if (res.ok) {
            mostrarToast('success', 'Estado Actualizado', `La orden #${data.id} ahora está en ${data.estado}.`);
            cerrarModalCambioEstado();
            mostrarDetalleOrden(data);
            cargarOrdenes();
        } else {
            if (res.status === 409) {
                mostrarToast('error', 'Conflicto', data.mensaje || 'La orden fue modificada por otro usuario.');
            } else if (res.status === 422) {
                mostrarToast('warning', 'No permitido', data.mensaje || 'Transición de estado no autorizada.');
            } else {
                mostrarToast('error', 'Error al actualizar', data.mensaje || 'Solicitud rechazada');
            }
        }
    } catch {
        mostrarToast('error', 'Error de red', 'No fue posible registrar el cambio de estado');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Guardar Cambio';
    }
}

// -----------------------------------------------------------------------------
// FORMATO Y NOTIFICACIONES
// -----------------------------------------------------------------------------
function formatearFecha(isoString) {
    if (!isoString) return '--';
    try {
        const d = new Date(isoString + (isoString.endsWith('Z') ? '' : 'Z'));
        return d.toLocaleString('es-CO', {
            year: 'numeric',
            month: 'short',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch {
        return isoString;
    }
}

function formatearFechaCorta(isoString) {
    if (!isoString) return '--';
    try {
        const d = new Date(isoString + (isoString.endsWith('Z') ? '' : 'Z'));
        return d.toLocaleDateString('es-CO', {
            month: 'short',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch {
        return isoString;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function mostrarToast(tipo, titulo, mensaje) {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast toast-${tipo}`;
    toast.innerHTML = `
        <div>
            <div class="toast-title">${escapeHtml(titulo)}</div>
            <div class="toast-desc">${escapeHtml(mensaje)}</div>
        </div>
    `;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.25s ease';
        setTimeout(() => toast.remove(), 250);
    }, 4000);
}
