# Informe de Sprint — Clima/Viento, Persistencia Comunal y Fixes de Dashboard

Fecha: 2026-06-19
Sprint: continuacion post Semana 16 (cierre Copernicus + nueva capa de viento)
Participantes: David Vasquez (dev), Claude (asistencia dev), Codex (mejoras UI complementarias)
Estado: vigente

---

## 1. Resumen ejecutivo

Este sprint cerro un bug critico de integracion Copernicus que dejaba comunas de La Araucania
en modo STANDARD (sin NDVI/NDMI), corrigio una metrica de dashboard que no reflejaba el modelo
de riesgo real, y agrego una capa nueva de direccion de viento con ventana horaria de 48h sobre
el mapa territorial. En paralelo, Codex realizo mejoras visuales independientes sobre los
controles del mapa y el popup de detecciones FIRMS.

| Area | Cambios | Estado |
|---|---|---|
| Integracion Copernicus (CDSE) | Fix de timeout/red privada Railway | Cerrado, validado en prod |
| CU02 — Dashboard `totalAlertas` | Cuenta comunas ALTO/CRITICO en vez de historico | Cerrado |
| Panel comunal (UI) | Fix de persistencia de score al cambiar de comuna | Cerrado |
| Capa de viento (nueva) | Direccion + slider horario 48h sobre el mapa | Cerrado, validado en prod con datos reales |
| Botonera ADMIN | Sync manual de clima/viento sin necesitar curl+token | Cerrado |
| FIRMS popup (Codex) | FRP, confianza NASA, recencia, ayuda en lenguaje simple | Cerrado |
| Accesibilidad colorblind | Criterio documentado, implementacion delegada a Codex | **Pendiente — ver seccion 7** |

---

## 2. Fixes de backend

### 2.1 Sync Copernicus via red privada de Railway (bug critico)

**Sintoma:** comunas de La Araucania quedaban en `mode=STANDARD` (sin NDVI/NDMI) con error
`openeo_client status=decode_error ... content type [application/octet-stream]`.

**Causa raiz:** el proxy publico de Railway (Hikari) corta conexiones a los ~130-140s; CDSE
tarda 130-160s en AOIs grandes. Railway devolvia `502 text/plain`, que Spring interpretaba como
`application/octet-stream` al no poder deserializarlo como JSON.

**Fix:**
- `OPENEO_SERVICE_BASE_URL` → `http://openeo-service-production.railway.internal:8080` (red
  privada, sin pasar por el proxy publico)
- `OPENEO_SERVICE_TIMEOUT_MS` → `220000` (antes 30000)
- Timeout de httpx en `openeo_client.py` → 200s (antes 120s)

**Validado:** `dashboard_sync ... status=finished quality=measured durationMs=99819` para NDVI y
NDMI en produccion.

### 2.2 `totalAlertas` del dashboard (CU02)

**Antes:** `heatAlertRepository.count()` — contaba TODOS los eventos de calor historicos jamas
registrados, sin relacion con el estado de riesgo actual.

**Ahora:** `ComunaRiskSnapshotRepository.countComunasWithHighOrCriticalAlertLevel()` — agregacion
Mongo que toma el **ultimo** snapshot por comuna (`$sort` + `$group` por `comunaId`) y cuenta solo
las que estan en `ALTO` o `CRITICO`.

Archivos: `DashboardServiceImpl.java`, `ComunaRiskSnapshotRepository.java`.

### 2.3 Direccion de viento horaria (Open-Meteo)

Se extendio la sincronizacion por comuna que ya existia (`ComunaRiskServiceImpl` →
`OpenWeatherFwiServiceImpl.syncFwiByRegion`, usa centroide de cada comuna) para traer:

- `winddirection_10m_dominant` (diario) — direccion dominante del dia
- `windspeed_10m` / `winddirection_10m` (horario) con `past_hours=24` + `forecast_hours=24`

**Bug encontrado y corregido en este mismo sprint:** `forecast_days=1` solo acota el bloque
`daily`; combinado con `past_hours`, el bloque `hourly` ignoraba ese limite y caia al horizonte
default de Open-Meteo (~16 dias → 408 puntos horarios en vez de 48). El parametro correcto para
acotar el horizonte horario hacia adelante es `forecast_hours`. Verificado contra la API real:
con `forecast_hours=24` el array queda en exactamente 48 puntos (24 pasado + 24 futuro).

Persistencia: `TerritoryWeatherObservation` ahora guarda `windDirection`,
`hourlyTimestamps`, `hourlyWindSpeed`, `hourlyWindDirection` (ver MER
`2026-06-19_mer_clima_viento_horario_v1.md`).

Exposicion: `TerritoryController.windValueMap()` agrega `direction` y `hourly[]` (timestamp,
speed, direction) a la respuesta existente de `/api/territory/layers?indicators=WIND`.

### 2.4 Endpoint de sync sin consumidor en UI (hallazgo, no bug nuevo)

`POST /api/territory/sync` (ROLE_ADMIN) ya existia y disparaba FIRMS + FWI regional + recompute
comunal en background, pero ningun componente de la UI lo llamaba — solo se podia ejecutar via
curl con un token. Se agrego un boton ADMIN-only (`WeatherSyncButton`) en el Dashboard. Ver
seccion 4.

**Nota para CU09:** el endpoint usa `regionId` en minuscula (`araucania`), mientras que
`/api/territory/layers` acepta ambas formas. Revisar esta inconsistencia de casing como posible
causa parcial del HTTP 500 reportado en CU09.

---

## 3. Fixes de frontend

### 3.1 Persistencia de score al cambiar de comuna (`ComunaRiskPanel` / `TerritoryMapPanel`)

**Sintoma:** al confirmar lectura Copernicus para una comuna, cambiar a otra y volver, la lectura
ENHANCED desaparecia y volvia a mostrar STANDARD.

**Causa:** dos bugs combinados:
1. `ComunaRiskPanel` no tenia `key={comunaId}` — el componente se reutilizaba al cambiar de
   comuna y el `syncState` (con el resultado ENHANCED de la comuna anterior) persistia.
2. El `score` pasado al abrir el panel venia de un cierre (`onEachFeature` del choropleth de
   Leaflet) capturado en el momento del montaje de la capa — no se actualizaba cuando
   `scoreOverrides` cambiaba.

**Fix:** `key={selectedComuna.comunaId}` + leer `score` desde `effectiveComunalScores` (que si
incluye los overrides) en vez del valor capturado en el click.

### 3.2 Capa de flechas de viento + slider horario (`WindArrowLayer`)

Nuevo componente que dibuja una flecha rotada por centroide de comuna, coloreada/escalada segun
velocidad (reusa `CLIMATE_SCALES.WIND` existente). La rotacion visual usa `direction + 180°`
porque el dato crudo es "de donde viene" el viento (convencion meteorologica) y la flecha debe
mostrar "hacia donde va".

Slider horizontal (`wind-hour-slider`) permite recorrer las ~48 horas disponibles; el label
incluye fecha (no solo hora) cuando la ventana supera 24 entradas, para evitar ambiguedad de dia.

### 3.3 Boton de sync de clima (ADMIN)

`WeatherSyncButton.tsx` en el Dashboard, gateado a `ROLE_ADMIN`/`ROLE_SUPER_ADMIN` (mismo check
que el backend), junto al `SyncNowButton` existente.

### 3.4 Mejoras de Codex (sin participacion de Claude en el diseño)

- Popup de detecciones FIRMS enriquecido: FRP, nivel de confianza NASA con explicacion en
  lenguaje simple, bucket de recencia (hoy/reciente), timestamp de deteccion.
- Reflow de la barra de controles del mapa a grid responsive.
- Refinamiento de sidebar/layout general (`Sidebar.jsx`, `MainLayout.jsx`, `layout.css`,
  `global.css`) y compactacion del layout de monitorizacion territorial.

---

## 4. Cambios de base de datos

**MongoDB unicamente — sin migraciones Postgres este sprint** (ultimo Flyway: `V6__notifications.sql`,
2026-06-05, sin cambios).

Ver detalle completo en `Documentacion/MER/2026-06-19_mer_clima_viento_horario_v1.md`:
`TerritoryWeatherObservation` agrega 4 campos nuevos (schema evolution, sin migracion porque
Mongo es schemaless). No hay colecciones nuevas.

---

## 5. Tests agregados

| Archivo | Tipo | Cubre |
|---|---|---|
| `OpenWeatherFwiServiceImplTest` (+2 tests) | Unitario (MockWebServer, sin DB) | `past_hours=24&forecast_hours=24` en la URL; parseo de `windDirection`/series horarias |
| `DashboardServiceImplTest` (nuevo) | Unitario (Mockito, sin DB) | `totalAlertas` cuenta agregacion de comunas, default a 0 si la agregacion devuelve null |
| `TerritoryControllerClimateIntegrationTest` (+1 test) | Integracion (requiere Mongo local/CI) | `/api/territory/layers` expone `direction` y `hourly[]` en WIND |
| `ComunaRiskSnapshotRepositoryIntegrationTest` (nuevo) | Integracion (requiere Mongo local/CI) | Pipeline `@Aggregation` real: solo cuenta el **ultimo** snapshot por comuna, no historico |

**Ejecutados y verificados en esta sesion** (no requieren Mongo): `OpenWeatherFwiServiceImplTest`
(4/4 OK), `DashboardServiceImplTest` (2/2 OK).

**Escritos pero NO ejecutados en esta sesion** (requieren MongoDB local o CI; Docker Desktop no
estaba disponible en el entorno de desarrollo al momento de escribir el sprint):
`TerritoryControllerClimateIntegrationTest` (nuevo caso), `ComunaRiskSnapshotRepositoryIntegrationTest`.
**Accion pendiente:** correr `mvn test` con Mongo local/CI levantado antes del proximo merge a
main para confirmar que pasan.

---

## 6. Pruebas manuales de UI pendientes (Andres)

Ver checklist dedicado: `Documentacion/Evidencias/2026-06-19_plan_pruebas_manuales_ui_andres.md`.

---

## 7. Accesibilidad — colores para daltonismo (pendiente, delegado a Codex)

Jennifer (directora AIFBN) solicito revisar la paleta de colores del mapa para personas con
daltonismo. Criterio de aceptacion documentado en
`Documentacion/Informes/2026-06-19_criterio_accesibilidad_colorblind_ux.md` — **implementacion
no realizada en este sprint**, queda a cargo de Codex.

---

## 8. Acciones de seguimiento

1. Ejecutar `mvn test` con Mongo local/CI para validar los 2 tests de integracion nuevos.
2. Investigar el casing de `regionId` (mayuscula/minuscula) en `/api/territory/sync` como posible
   causa parcial de CU09 (HTTP 500).
3. Asignar a Codex: implementacion de paleta accesible para daltonismo (ver seccion 7).
4. Ejecutar checklist manual de UI con Andres (seccion 6) antes de cerrar el sprint.
5. Revisar las 16 vulnerabilidades de Dependabot reportadas por GitHub (11 high, 5 moderate) —
   no se investigaron en este sprint.
