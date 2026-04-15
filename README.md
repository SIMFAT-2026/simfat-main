# SIMFAT Frontend

Frontend del proyecto **SIMFAT** (Sistema Integrado de Monitoreo y Alerta Temprana Forestal).

- Nombre tecnico: `simfat-frontend`
- Stack: React + Vite + JavaScript + Axios + React Router DOM + Recharts
- Objetivo: visualizacion, gestion de datos y alertas tempranas conectadas a `simfat-backend`

## Estado Actual del Desarrollo

**Fecha de actualizacion:** 05-04-2026  
**Estado general:** Base MVP funcional y conectada a backend real.

### Avance implementado

- Estructura profesional del proyecto (`api`, `services`, `pages`, `components`, `layouts`, `hooks`, `utils`, `styles`).
- Navegacion completa con React Router.
- Layout principal con Navbar, Sidebar y Footer.
- CRUD funcional para:
  - Regiones
  - Perdida forestal
  - Alertas
  - Reglas
- Dashboard conectado a endpoints reales:
  - Summary
  - Critical regions
  - Loss trend
  - Alerts summary
- Manejo de errores robusto con normalizacion de contrato backend.
- Soporte para `validationErrors` por campo en formularios.
- Adaptador de compatibilidad para respuestas envueltas y payload plano.
- Build y lint verificados.

### Estado de ramas remotas

- `main` publicada
- `develop` publicada
- Commit base actual: `be09cbb`

## Requisitos

- Node.js 18+ (recomendado Node.js 20 LTS)
- npm 9+
- Backend `simfat-backend` ejecutandose en `http://localhost:8080`

## Variables de entorno

Crear archivo `.env` (puedes copiar `.env.example`) con:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
VITE_API_URL=http://localhost:8080
```

Notas:
- `NEXT_PUBLIC_API_BASE_URL` es la variable principal usada por el dashboard satelital MVP.
- `VITE_API_URL` se mantiene como fallback de compatibilidad.

## Ejecutar en modo desarrollo

1. Instalar dependencias:

```bash
npm install
```

2. Levantar servidor de desarrollo:

```bash
npm run dev
```

3. Abrir en navegador:

```text
http://localhost:5173
```

## Ejecutar en modo produccion (local)

1. Generar build optimizado:

```bash
npm run build
```

2. Servir build localmente:

```bash
npm run preview
```

3. Abrir URL que muestra Vite Preview (normalmente):

```text
http://localhost:4173
```

## Scripts disponibles

```bash
npm run dev      # desarrollo
npm run build    # build produccion
npm run preview  # servir build local
npm run lint     # revision de codigo
```

## Contrato de backend esperado

Base URL:

```text
http://localhost:8080
```

Formato exito:

```json
{ "success": true, "message": "...", "data": {}, "timestamp": "..." }
```

Formato error:

```json
{
  "success": false,
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/api/...",
  "timestamp": "...",
  "validationErrors": [{ "field": "nombre", "message": "..." }]
}
```

## Despliegue en Vercel

El proyecto ya incluye configuracion SPA para rutas de React Router en `vercel.json`.

Pasos recomendados:

1. Importar repo en Vercel.
2. Configurar variable de entorno `VITE_API_URL` con la URL real del backend.
3. Deploy de `main` (produccion) o `develop` (entorno dev/preview segun flujo).

## Estructura resumida

```text
src/
  api/
  components/
  hooks/
  layouts/
  pages/
  router/
  services/
  styles/
  utils/
```

## Flujo sugerido de trabajo

- Features nuevas en ramas `feature/*` desde `develop`.
- Merge de feature -> `develop` para pruebas integradas.
- Merge de `develop` -> `main` para releases.

---

Si necesitas, en la siguiente iteracion dejamos tambien una guia de contribucion (`CONTRIBUTING.md`) y convenciones de commits para el equipo.
