const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const BACKEND_URL = process.env.BACKEND_URL || 'http://orden-service:8080';

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Helper para reenviar peticiones al servicio backend
async function proxyToBackend(req, res, targetPath, options = {}) {
    const url = new URL(targetPath, BACKEND_URL);
    if (req.query && Object.keys(req.query).length > 0) {
        Object.entries(req.query).forEach(([k, v]) => {
            if (v !== undefined && v !== null && v !== '') {
                url.searchParams.set(k, v);
            }
        });
    }

    const headers = {
        'Accept': 'application/json',
        'X-Usuario': req.headers['x-usuario'] || 'portal_web',
        'X-Correlation-Id': req.headers['x-correlation-id'] || `web-${Date.now()}`
    };

    if (options.body) {
        headers['Content-Type'] = 'application/json';
    }

    try {
        const backendRes = await fetch(url.toString(), {
            method: options.method || 'GET',
            headers,
            body: options.body ? JSON.stringify(options.body) : undefined
        });

        const contentType = backendRes.headers.get('content-type') || '';
        const data = contentType.includes('application/json')
            ? await backendRes.json()
            : await backendRes.text();

        res.status(backendRes.status).json(data);
    } catch (err) {
        console.error(`Error conectando con backend (${url}):`, err.message);
        res.status(503).json({
            status: 503,
            codigo: 'SERVICIO_NO_DISPONIBLE',
            mensaje: 'No fue posible comunicarse con el servicio central. Verifique que los contenedores estén activos.'
        });
    }
}

// Estado del backend
app.get('/api/estado-servicio', (req, res) => {
    proxyToBackend(req, res, '/actuator/health');
});

// Registrar nueva orden
app.post('/api/ordenes', (req, res) => {
    proxyToBackend(req, res, '/orden', {
        method: 'POST',
        body: req.body
    });
});

// Consultar orden por identificador
app.get('/api/ordenes/:id', (req, res) => {
    proxyToBackend(req, res, `/orden/${encodeURIComponent(req.params.id)}`);
});

// Modificar estado operativo
app.put('/api/ordenes/:id/estado', (req, res) => {
    proxyToBackend(req, res, `/orden/${encodeURIComponent(req.params.id)}/estado`, {
        method: 'PUT',
        body: req.body
    });
});

// Búsqueda y listado paginado
app.get('/api/ordenes', (req, res) => {
    proxyToBackend(req, res, '/orden');
});

// Fallback SPA
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Portal de Órdenes escuchando en http://0.0.0.0:${PORT}`);
    console.log(`Conectado al backend: ${BACKEND_URL}`);
});
