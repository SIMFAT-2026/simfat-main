# SIMFAT Frontend

Frontend del proyecto **SIMFAT** (Sistema Integrado de Monitoreo y Alerta Temprana Forestal).

- Nombre tecnico: `simfat-frontend`
- Stack: React + Vite + JavaScript + Axios + React Router DOM + Recharts
- Objetivo: visualizacion, gestion de datos y alertas tempranas conectadas a `simfat-backend`

## Estado Actual del Desarrollo

**Fecha de actualizacion:** 15-04-2026  
**Estado general:** MVP funcional con autenticacion JWT integrada a backend.

### Avance implementado

- Estructura profesional del proyecto (`api`, `services`, `pages`, `components`, `layouts`, `hooks`, `utils`, `styles`).
- Navegacion con React Router y proteccion de rutas por autenticacion.
- Layout principal con Navbar, Sidebar y Footer.
- Dashboard conectado a endpoints reales:
  - Summary
  - Critical regions
  - Loss trend
  - Alerts summary
- Manejo de errores robusto con normalizacion de contrato backend (`ApiResponse<T>` y `validationErrors`).
- Flujo auth completo en frontend:
  - Login
  - Registro
  - Recuperacion de contraseña
  - Reset de contraseña
  - Logout
- Soporte JWT:
  - `Authorization: Bearer <accessToken>` en Axios
  - Refresh automatico de token con `POST /api/auth/refresh` cuando expira access token
  - Limpieza de sesion si refresh falla
- UX de autenticacion mejorada:
  - Requisitos visibles de password
  - Indicador de seguridad de contraseña
  - Mostrar/ocultar contraseña en login y registro
  - Recordar usuario (persistencia de correo local)
- Turnstile de Cloudflare habilitable por variable de entorno.

## Requisitos

- Node.js 18+ (recomendado Node.js 20 LTS)
- npm 9+
- Backend `simfat-backend` ejecutandose en `http://localhost:8081`

## Variables de entorno

Crear archivo `.env` (puedes copiar `.env.example`) con:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081
VITE_API_URL=http://localhost:8081
VITE_AUTH_TURNSTILE_ENABLED=true
VITE_TURNSTILE_SITE_KEY=1x00000000000000000000AA
VITE_AUTH_DEV_TOOLS_ENABLED=true
```

Notas:
- `NEXT_PUBLIC_API_BASE_URL` / `VITE_API_URL`: base URL backend.
- `VITE_AUTH_TURNSTILE_ENABLED`: activa/desactiva captcha en formularios auth.
- `VITE_TURNSTILE_SITE_KEY`: clave publica de Turnstile (usar la del ambiente).
- `VITE_AUTH_DEV_TOOLS_ENABLED`: habilita utilidades de desarrollo (seed users).

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

## Ejecutar en modo preview (build local)

1. Generar build optimizado:

```bash
npm run build
```

2. Servir build localmente:

```bash
npm run preview
```

3. Abrir URL de preview (normalmente):

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
http://localhost:8081
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

## Endpoints de autenticacion usados por frontend

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `POST /api/auth/dev/seed-users` (solo dev)

## Despliegue en Vercel

El proyecto incluye configuracion SPA para rutas de React Router en `vercel.json`.

Pasos recomendados:

1. Importar repo en Vercel.
2. Configurar variables de entorno del frontend.
3. Deploy de `main` (produccion) o `develop` (preview/dev).

## Estructura resumida

```text
src/
  api/
  auth/
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
