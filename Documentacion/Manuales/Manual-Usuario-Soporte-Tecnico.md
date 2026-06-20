# Manual de Usuario — Rol: Soporte Técnico

**Proyecto:** SIMFAT-2026 — Sistema de Monitoreo y Alerta Temprana Forestal
**Proyecto de título — Duoc UC**, Escuela de Informática y Telecomunicaciones, Sección 002D
**Docente guía:** Arturo Vargas

| Rut | Nombre | Correo |
|---|---|---|
| 18.239.964-7 | Andrés Ibáñez Rojas | and.ibanezr@duocuc.cl |
| 18.832.438-k | David Vásquez Ovalle | davi.vasquezo@duocuc.cl |

## Histórico de revisiones

| Versión | Fecha | Descripción/cambio | Autor |
|---|---|---|---|
| 1.0 | 10/06/2026 | Manual de usuario inicial | Andrés Ibáñez |
| 2.0 | 20/06/2026 | Actualización del manual | Andrés Ibáñez |
| 3.0 | 20/06/2026 | Corrección de ruta del script de seed y del cron real de OPENEO_SYNC_CRON tras auditoría contra código fuente | David Vásquez |

---

## 1. Arquitectura del Ecosistema

SIMFAT-2026 es un sistema distribuido de tres capas:

1. **Frontend (simfat-web):** interfaz en React que consume el backend principal.
2. **Backend (simfat-backend):** lógica de negocio (Spring Boot), persistencia en PostgreSQL (Auth) y MongoDB (Dashboard).
3. **Microservicio openEO (openeo-service):** Python/FastAPI, puente con los satélites Copernicus.

## 2. Requisitos del Entorno

- **Java 17+ y Maven:** para el backend de Spring Boot.
- **Node.js 18+ (20 LTS recomendado) y npm 9+:** para la interfaz React.
- **Python 3.11+:** para el microservicio openEO.
- **Motores de Base de Datos:** PostgreSQL (relacional) y MongoDB (documental).

## 3. Configuración de Variables de Entorno (.env)

### Backend Principal

- `POSTGRES_URI`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `MONGODB_URI`: credenciales y cadenas de conexión.
- `AUTH_JWT_SECRET`: secreto de mínimo 32 bytes para firmar tokens.
- `OPENEO_SERVICE_BASE_URL`: dirección del microservicio Python (ej. `http://localhost:8000`).
- `OPENEO_SYNC_CRON`: frecuencia de sincronización automática. **Default: diario a las 00:00 (`0 0 0 * * *`)**, configurable vía esta variable.

### Microservicio openEO

- `OPENEO_BASE_URL`, `OPENEO_CLIENT_ID`, `OPENEO_CLIENT_SECRET`: credenciales de Copernicus/CDSE.
- `SIMFAT_BACKEND_URL`: dirección del backend Spring Boot para reportar resultados (ej. `http://localhost:8081`).

### Frontend

- `VITE_API_URL`: apuntando al backend Spring Boot.
- `VITE_AUTH_TURNSTILE_ENABLED`: `true`/`false` para activar el captcha de Cloudflare.

## 4. Guía de Despliegue Paso a Paso

### Paso 1: Preparación de Datos (Backend)

1. Ejecutar el backend para que Flyway cree automáticamente las tablas en PostgreSQL.
2. **Carga masiva de regiones:** desde `Producto/backend/simfat-backend/`, ejecutar:
   ```powershell
   .\scripts\regions-seed-chile-official.ps1
   ```
   Este script puebla las **16 regiones oficiales de Chile** y sus coordenadas Bbox (AOI), leyendo `scripts/chile-regions-aoi-official.json`.

### Paso 2: Lanzamiento de Microservicios

1. **openEO Service:** `pip install -r requirements.txt` e iniciar con `uvicorn app.main:app --port 8000`.
2. **Backend:** compilar con `mvn clean install`, ejecutar con `mvn spring-boot:run` (puerto 8081).
3. **Frontend:** `npm install`, `npm run build` para producción o `npm run dev` (puerto 5173) para desarrollo.

## 5. Soporte y Mantenimiento de Operaciones

### Monitoreo de Sincronización

Si una región muestra estado `STALE` o `EMPTY`, forzar actualización manual:

- `POST /api/dashboard/sync/run?regionId={ID}`
- Revisar logs de trazabilidad para detectar fallos de latencia o hits/miss de caché.

### Resolución de Problemas Comunes

- **Error 401 / Expired JWT:** verificar que `AUTH_JWT_SECRET` sea idéntico en todas las instancias del servidor y que el reloj del sistema esté sincronizado.
- **Datos en 0 o `aoi_missing`:** verificar que la región tenga un Bbox válido en MongoDB o en `OPENEO_AOI_BBOX_MAP` (formato `zona:west,south,east,north`, ej. `BIOBIO:-73.97359,-38.492447,-70.98298,-36.44324`).
- **Fallo de Conexión openEO:** usar `GET /config/check` en el microservicio Python para validar credenciales satelitales.

### Hardening y Seguridad (Producción)

- Mover todos los secretos de archivos `.env` a un Secret Manager.
- Configurar HTTPS obligatorio y políticas de cookies `Secure`/`HttpOnly`.
- Activar el Rate Limiting del backend para prevenir fuerza bruta en el login (5 intentos / 300 s).

**Nota para el equipo de soporte:** en caso de fallos críticos en el despliegue web, `vercel.json` ya contiene la configuración de SPA necesaria para manejar las rutas de React Router en entornos de nube.
