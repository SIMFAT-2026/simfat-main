# SIMFAT Backend (MVP + OpenEO Sync)

Backend de **SIMFAT** (Spring Boot + PostgreSQL + MongoDB) para monitoreo forestal y dashboard.  
El frontend `simfat-web` consume solo este backend; la integración con openEO se hace internamente via `openeo-service`.

## Arquitectura de integración

Flujo principal:

1. `@Scheduled` (o trigger manual) ejecuta sync por regiones.
2. Se consulta `openeo-service` por indicador con `POST /openeo/indicators/latest/{indicator}`.
3. Se registra traza en `openeo_job_runs`.
4. Si llega valor real, se hace upsert en `openeo_indicator_observations`.
5. Se recalcula `dashboard_region_snapshots`.
6. Endpoints dashboard leen desde Mongo (observations/snapshots), sin consultas live a proveedor por request.

Persistencia actual:

- PostgreSQL: autenticacion (`app_users`, `refresh_tokens`, `password_reset_tokens`) con migraciones Flyway.
- MongoDB: dashboard y observaciones openEO.

Colecciones nuevas:

- `openeo_job_runs`
- `openeo_indicator_observations`
- `dashboard_region_snapshots`

Indices aplicados:

- `openeo_job_runs`: `jobId` unico, `status + updatedAt(desc)`
- `openeo_indicator_observations`: `regionId + indicator + observedAt(desc)` y unico compuesto `regionId + indicator + observedAt`
- `dashboard_region_snapshots`: `regionId` unico, `computedAt(desc)`

## Variables de entorno

Usa `.env.example` como referencia.

Base:

- `POSTGRES_URI`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `MONGODB_URI`
- `SERVER_PORT`
- `FRONTEND_URL`
- `DEFAULT_REFERENCE_HECTARES`
- `DEFAULT_LOSS_THRESHOLD`
- `DEFAULT_HEAT_EVENTS_THRESHOLD`

Integracion openEO:

- `OPENEO_SERVICE_BASE_URL`
- `OPENEO_SERVICE_TIMEOUT_MS` (default `8000`)
- `OPENEO_SYNC_ENABLED` (default `true`)
- `OPENEO_SYNC_CRON` (default cada 15 min: `0 */15 * * * *`)
- `OPENEO_AOI_BBOX_MAP` (mapa `regionCode -> bbox`, formato `CL-15:-70.8,-19.2,-69.2,-18.1;...`)
- `OPENEO_SYNC_PLACEHOLDER_VALUE_ENABLED` (default `false`; solo testing/demo)
- `OPENEO_SYNC_MIN_REQUEST_INTERVAL_MINUTES` (default `0`; evita llamadas repetidas a openEO dentro de una ventana)
- `OPENEO_INGEST_AUTH_TOKEN` (opcional; protege `POST /api/indicators/measurements` para ingesta interna desde `openeo-service`)

Autenticacion y seguridad:

- `AUTH_JWT_SECRET` (obligatoria, minimo 32 bytes)
- `AUTH_JWT_ACCESS_TTL_MINUTES` (10-15 recomendado, default `15`)
- `AUTH_JWT_REFRESH_TTL_DAYS` (default `14`)
- `AUTH_JWT_ISSUER` (default `simfat-backend`)
- `AUTH_TURNSTILE_ENABLED` (default `false`)
- `AUTH_TURNSTILE_SECRET_KEY` (obligatoria si `AUTH_TURNSTILE_ENABLED=true`)
- `AUTH_TURNSTILE_VERIFY_URL` (default Cloudflare Turnstile)
- `AUTH_RATE_LIMIT_LOGIN_MAX_ATTEMPTS` (default `5`)
- `AUTH_RATE_LIMIT_LOGIN_WINDOW_SECONDS` (default `300`)
- `AUTH_RATE_LIMIT_FORGOT_MAX_ATTEMPTS` (default `5`)
- `AUTH_RATE_LIMIT_FORGOT_WINDOW_SECONDS` (default `600`)
- `AUTH_PASSWORD_RESET_TTL_MINUTES` (default `30`)

## Ejecutar localmente

1. Configura variables de entorno.
2. Compila y prueba:
   - `mvn clean test`
3. Ejecuta:
   - `mvn spring-boot:run`

API por defecto: `http://localhost:8080`.

### Entornos local vs remoto

El proyecto incluye un script para cargar variables desde archivo:

- `scripts/start-local.ps1 -EnvFile ".env.local"`: levanta con DBs locales.
- `scripts/start-local.ps1 -EnvFile ".env.remote"`: levanta con Supabase + Atlas.

Nota: `mvn spring-boot:run` no siempre carga `.env` automaticamente en PowerShell.
Por eso se recomienda usar el script anterior para desarrollo.

## Endpoints dashboard

Compatibles existentes:

- `GET /api/dashboard/summary`
- `GET /api/dashboard/critical-regions`
- `GET /api/dashboard/loss-trend`
- `GET /api/dashboard/alerts-summary`

Nuevos MVP:

- `POST /api/dashboard/sync/run?regionId={id}&from=YYYY-MM-DD&to=YYYY-MM-DD` (`regionId/from/to` opcionales)
- `GET /api/dashboard/indicators/latest?regionId={id}&indicator=NDVI|NDMI`
- `GET /api/dashboard/indicators/series?regionId={id}&indicator=NDVI|NDMI&from=YYYY-MM-DD&to=YYYY-MM-DD&granularity=day|week|month` (`from/to` opcionales, default ultimos 30 dias)
- `GET /api/dashboard/indicators/map?indicator=NDVI|NDMI&from=YYYY-MM-DD&to=YYYY-MM-DD&limit=500`
- `GET /api/dashboard/data-freshness?regionId={id}`
- `POST /api/indicators/measurements` (ingesta interna para `openeo-service`; usa `Authorization: Bearer <OPENEO_INGEST_AUTH_TOKEN>` o `X-OpenEO-Ingest-Token` si el token esta configurado)

Endpoints AOI de regiones:

- `GET /api/regions/aoi/coverage` (estado de cobertura por region: `aoi_db|aoi_env|missing`)
- `PATCH /api/regions/{id}/aoi` body `{ "aoiBbox": [west,south,east,north] }`

Todas las respuestas usan `ApiResponse<T>`.

## Endpoints auth

Implementados y compatibles con `simfat-web`:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `POST /api/auth/dev/seed-users` (solo perfiles `dev` o `local`)

Extra recomendado:

- `POST /api/auth/refresh` (rotacion de refresh token)

Formato de respuesta en login/register/refresh:

- `data.user`
- `data.accessToken`
- `data.refreshToken`
- `data.expiresAt`

Politicas aplicadas:

- Password hashing: `BCrypt`
- Access token JWT corto: `10-15` min (default `15`)
- Refresh token rotatorio con revocacion server-side
- `forgot-password` no revela si el correo existe
- Rate limiting para login y recuperacion
- Turnstile opcional por feature flag
- Indices SQL en `app_users`, `refresh_tokens`, `password_reset_tokens` (Flyway `V1__create_auth_tables.sql`)

Contratos de respuesta dashboard MVP:

- `latest`: `regionId`, `indicator`, `value`, `observedAt`, `source`, `cached`
- `series.points[]`: `ts`, `value`
- `data-freshness`: `regionId`, `lastUpdate`, `ageSeconds`, `status` (`FRESH|STALE|EMPTY`)

## Ejemplos de uso

Trigger manual de sync:

```bash
curl -X POST "http://localhost:8080/api/dashboard/sync/run?regionId=REGION_ID"
```

Trigger manual con ventana explicita:

```bash
curl -X POST "http://localhost:8080/api/dashboard/sync/run?regionId=REGION_ID&from=2026-04-01&to=2026-04-05"
```

Ultimo indicador:

```bash
curl "http://localhost:8080/api/dashboard/indicators/latest?regionId=REGION_ID&indicator=NDVI"
```

Serie semanal:

```bash
curl "http://localhost:8080/api/dashboard/indicators/series?regionId=REGION_ID&indicator=NDMI&from=2026-03-01&to=2026-03-31&granularity=week"
```

Serie diaria (default ultimos 30 dias):

```bash
curl "http://localhost:8080/api/dashboard/indicators/series?regionId=REGION_ID&indicator=NDVI"
```

Registro:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName":"Ana Perez",
    "email":"ana@example.com",
    "password":"StrongPass!123"
  }'
```

Login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email":"ana@example.com",
    "password":"StrongPass!123"
  }'
```

Perfil actual (`ACCESS_TOKEN`):

```bash
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

Renovar tokens:

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"REFRESH_TOKEN"}'
```

Recuperacion (respuesta siempre neutra):

```bash
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@example.com"}'
```

Reset password:

```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token":"RESET_TOKEN",
    "newPassword":"MyN3wPass!456"
  }'
```

Seed de usuarios (solo `dev/local`):

```bash
curl -X POST http://localhost:8080/api/auth/dev/seed-users \
  -H "Content-Type: application/json" \
  -d '{"count":5}'
```

## Rendimiento y observabilidad

- Lecturas frecuentes con cache local TTL corto (30-90s).
- Payloads de dashboard acotados para UI.
- `map` con limite y tope maximo (`500`).
- Logs de sync y cache con campos de trazabilidad (region, indicador, status, latencia, hit/miss).
- Guardrail de consumo openEO configurable con `OPENEO_SYNC_MIN_REQUEST_INTERVAL_MINUTES` para reusar observaciones recientes y reducir costo/tokens.

## Mapa de contratos entre repos

`simfat-web` (frontend) -> `Simfat-backend`:
- `POST /api/dashboard/sync/run`
- `GET /api/dashboard/indicators/latest`
- `GET /api/dashboard/indicators/series`
- `GET /api/dashboard/indicators/map`
- `GET /api/dashboard/data-freshness`

`Simfat-backend` -> `openeo-service`:
- `POST /openeo/indicators/latest/{indicator}` (flujo principal pull)
- `GET /openeo/capabilities` y `GET /openeo/collections` (probe/debug)

`openeo-service` -> `Simfat-backend`:
- `POST /api/indicators/measurements` (flujo opcional push/callback para persistir mediciones y traza)

## AOI por region

El sync solo persiste datos reales cuando la region tiene AOI bbox configurado.
Prioridad de resolucion:

1. AOI guardado en `regions.aoiBbox` (MongoDB)
2. Fallback `OPENEO_AOI_BBOX_MAP`
3. Si no existe AOI valido -> `jobRun` con estado `aoi_missing` y no se llama a openEO

Formato de `OPENEO_AOI_BBOX_MAP`:

```env
OPENEO_AOI_BBOX_MAP=CL-15:-70.8,-19.2,-69.2,-18.1;CL-1:-70.5,-21.0,-69.8,-20.2
```

- clave: `region.codigo` (ej: `CL-15`)
- orden de coordenadas: `west,south,east,north`

Si falta AOI para una region:
- se registra warning de sync
- se guarda `jobRun` con estado de error controlado
- no se crea observacion fake

### Carga masiva de AOI en BD

1. Completa `scripts/aoi-bbox.sample.json` con codigos de region y bbox.
2. Ejecuta:

```powershell
.\scripts\regions-aoi-import.ps1 -BackendBaseUrl "http://localhost:8081" -MappingFile ".\scripts\aoi-bbox.sample.json"
```

3. Verifica:

```bash
curl http://localhost:8081/api/regions/aoi/coverage
```

### Seed oficial de regiones Chile (16) + AOI

Para poblar el backend con las 16 regiones administrativas y AOI `bbox` oficiales:

```powershell
.\scripts\regions-seed-chile-official.ps1 -BackendBaseUrl "http://localhost:8081" -MappingFile ".\scripts\chile-regions-aoi-official.json" -PruneLegacyRegions
```

Fuente oficial usada en el mapeo:
- DPA 2023 (IDE Chile / SUBDERE), descargable desde:
  - `https://www.geoportal.cl/geoportal/catalog/download/912598ad-ac92-35f6-8045-098f214bd9c2`

Metodo:
- `bbox` por region derivado agregando los poligonos comunales oficiales (`CUT_REG`) en `COMUNAS_v1` (SIRGAS-Chile).

## Datos reales vs fallback

- Modo recomendado (real): `OPENEO_SYNC_PLACEHOLDER_VALUE_ENABLED=false`
- Modo testing/demo: `OPENEO_SYNC_PLACEHOLDER_VALUE_ENABLED=true`

Con fallback desactivado, nunca se inventa `value` ni `quality: estimated`.

## Testing

Incluye:

- Unit tests:
  - cliente `openeo-service` con `MockWebServer`
  - sync service
  - snapshot service
- Integration tests:
  - nuevos endpoints dashboard (MockMvc)
  - repositorios e indices de colecciones nuevas
  - repositorios auth (unicidad de indices)

## Tabla de amenazas y mitigaciones

| Amenaza | Riesgo | Mitigacion aplicada |
|---|---|---|
| Robo de password en DB | Compromiso de cuentas | Hash `BCrypt` (no password en claro) |
| Access token robado | Suplantacion temporal | TTL corto (10-15 min) + validacion JWT firmada |
| Reuso de refresh token | Sesion persistente indebida | Rotacion por uso + revocacion server-side |
| Fuerza bruta en login | Toma de cuentas | Rate limiting por IP+email |
| Enumeracion de correos | Filtrado de usuarios validos | `forgot-password` con respuesta neutra |
| Replay de reset token | Cambio de password no autorizado | Token hasheado, TTL y consumo unico |
| Bot abuse en auth | Spam/abuso endpoint | Turnstile opcional por feature flag |
| Exposicion por endpoint dev | Riesgo en produccion | `dev/seed-users` bloqueado fuera de perfiles `dev/local` |

## Checklist de hardening pendiente (produccion)

- [ ] Almacenar `AUTH_JWT_SECRET` en secret manager (no `.env` en servidor).
- [ ] Configurar rotacion periodica de `AUTH_JWT_SECRET`.
- [ ] Implementar envio real de email para reset (proveedor transaccional + plantillas).
- [ ] Agregar auditoria de eventos auth (login ok/fail, refresh, reset).
- [ ] Activar bloqueo progresivo de cuenta ante multiples intentos fallidos.
- [ ] Configurar HTTPS obligatorio y cookies `Secure`/`HttpOnly` si se migra a cookie auth.
- [ ] Agregar deteccion de reuse sospechoso de refresh token y respuesta de incidente.
- [ ] Integrar observabilidad (metricas de 401/429, alertas, trazabilidad).

## Documento de avance

- `docs/avance-auth-postgres-2026-04-15.md`
