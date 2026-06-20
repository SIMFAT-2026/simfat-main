# Manual de Usuario — Rol: Analista de Monitoreo Forestal / Operador Regional

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
| 3.0 | 20/06/2026 | Corrección del formato real de `OPENEO_AOI_BBOX_MAP` tras auditoría contra código fuente | David Vásquez |

---

## 1. Objetivo

Estandarizar los procedimientos operativos para el Analista de Monitoreo Forestal y el Operador Regional dentro del ecosistema SIMFAT-2026, garantizando precisión en la interpretación de índices biofísicos e integridad en la sincronización de datos geofísicos regionales.

El ecosistema SIMFAT se compone de tres módulos técnicos integrados:

- **simfat-web** (React + Vite): interfaz para visualización de indicadores y gestión de alertas.
- **simfat-backend** (Spring Boot): núcleo de servicios, gestión de identidades y persistencia de datos.
- **openeo-service** (FastAPI): microservicio de procesamiento de datos satelitales vía openEO y Copernicus.

## 2. Roles y responsabilidades

**Analista de Monitoreo Forestal**

- Análisis de tendencias globales: series temporales para identificar patrones de degradación forestal a gran escala.
- Generación de reportes: consolidación de datos de pérdida forestal para la toma de decisiones.
- Supervisión del Dashboard Nacional: KPIs nacionales y Alerts Summary.

**Operador Regional**

- Validación local de alertas: verificación técnica de incidentes en su jurisdicción administrativa, según la DPA 2023 (división político-administrativa).
- Sincronización regional: ejecución manual de actualización de datos filtrados por `regionId`.
- Gestión de AOI (Area of Interest): configuración de perímetros geográficos (Bbox) basados en SIRGAS-Chile.

## 3. Consideraciones técnicas y seguridad

### Conectividad y seguridad

- **Autenticación JWT:** JSON Web Tokens firmados para autorización.
- **Ciclo de vida del token:** Access Token con TTL de 15 minutos; Refresh Token con validez de 14 días.
- **Cifrado en tránsito:** HTTPS obligatorio, cookies `Secure`/`HttpOnly` en producción.
- **Integridad de secretos:** `AUTH_JWT_SECRET` debe gestionarse mediante un Secret Manager, evitando archivos `.env` en texto plano.

### Infraestructura de persistencia

- **PostgreSQL:** datos relacionales (usuarios, perfiles, auditoría de acceso).
- **MongoDB:** observaciones satelitales, snapshots del dashboard, indicadores de vigor vegetal.

## 4. Acceso y gestión de credenciales

**Procedimiento de autenticación:**

1. Ingresar con Correo Electrónico y Contraseña (hashing BCrypt).
2. Completar el desafío Cloudflare Turnstile si el sistema lo solicita.

**Recuperación de contraseña:** el enlace de restablecimiento tiene un TTL de 30 minutos y es de un solo uso.

**Protección contra abuso (Rate Limiting):**

- Login: máximo 5 intentos en 300 segundos.
- Recuperación: máximo 5 intentos en 600 segundos.

## 5. Operación del Dashboard de Monitoreo (Rol: Analista)

El Analista supervisa cuatro módulos del Frontend:

1. **Summary:** resumen ejecutivo de indicadores clave.
2. **Critical Regions:** áreas con mayor degradación detectada.
3. **Loss Trend:** evolución histórica de la pérdida de masa boscosa.
4. **Alerts Summary:** alertas activas generadas por el motor de análisis.

**Visualización geográfica:**

- Endpoint de origen: `GET /api/dashboard/indicators/map`.
- Renderiza un máximo de 500 registros georeferenciados.
- Heatmap para identificar puntos calientes de estrés hídrico o pérdida de vigor.

**Análisis de series temporales:**

`GET /api/dashboard/indicators/series?regionId={id}&indicator={type}&granularity={period}`

- `regionId` e `indicator` (NDVI/NDMI) son obligatorios.
- `granularity`: día, semana o mes.

## 6. Gestión de Sincronización y Datos Regionales (Rol: Operador)

**Sincronización de datos (Pull & Push):**

1. **Sincronización manual (Pull):** `POST /api/dashboard/sync/run`, con parámetros opcionales `regionId`, `from`, `to`.
2. **Ingesta interna (Push):** el openeo-service envía mediciones al backend vía `POST /api/indicators/measurements`, autenticado con `OPENEO_INGEST_AUTH_TOKEN`.

**Configuración de Áreas de Interés (AOI):**

La resolución de coordenadas Bbox sigue este orden de prioridad:

1. **MongoDB:** coordenadas persistidas en la colección de regiones.
2. **`OPENEO_AOI_BBOX_MAP`:** variable de entorno con pares `zona:west,south,east,north` separados por `;`. El valor por defecto configurado en el sistema es:
   ```
   BIOBIO:-73.97359,-38.492447,-70.98298,-36.44324;ARAUCANIA:-73.52026,-39.63724,-70.826457,-37.581726;NUBLE:-72.884703,-37.19847,-71.007007,-36.005384
   ```
   El identificador usado en esta variable es el **nombre de la zona** (`BIOBIO`, `ARAUCANIA`, `NUBLE`), no el código ISO de región (`CL-8`, `CL-9`, `CL-16`).

> **Nota técnica:** si no se encuentra un AOI válido, el sistema genera un warning de sync y registra un estado `aoi_missing` en los logs.

**Estado de "Freshness":** `GET /api/dashboard/data-freshness?regionId={id}` (el parámetro de región es obligatorio):

- **FRESH:** datos actualizados y vigentes.
- **STALE:** datos fuera del umbral de tiempo recomendado.
- **EMPTY:** sin registros de observación previos.

## 7. Interpretación de Indicadores Satelitales

Todos los datos se procesan desde la constelación Sentinel-2 L2A.

| Indicador | Descripción Técnica | Endpoint de Consumo |
|---|---|---|
| NDVI | Índice de Vegetación de Diferencia Normalizada. Mide densidad y vigor vegetal. | latest / series |
| NDMI | Índice de Humedad de Diferencia Normalizada. Detecta estrés hídrico. | latest / series |

Los resultados son normalizados por el openeo-service para que sean interpretables directamente en el Dashboard.

## 8. Monitoreo de Tareas (Jobs) y Logs

Cada interacción con Copernicus se registra en `openeo_job_runs`. El Operador debe supervisar:

- **Latencia de Procesamiento:** tiempo de respuesta del servidor openEO.
- **Estado de Error:** fallos por falta de tokens o coordenadas (`aoi_missing`).

**Optimización de recursos:** `OPENEO_SYNC_MIN_REQUEST_INTERVAL_MINUTES` impide solicitudes redundantes a openEO si ya existe una observación reciente que cumpla los criterios de tiempo definidos.

## 9. Cierre de Sesión y Buenas Prácticas

**Procedimiento de salida** — la función Cerrar Sesión debe:

1. Invalidar el Access Token en el servidor.
2. Eliminar el Refresh Token de la persistencia de sesión.
3. Limpiar el almacenamiento local del navegador.

**Checklist de hardening:**

- Rotación periódica del secreto de firma JWT.
- Bloqueo progresivo de cuentas tras múltiples intentos fallidos.
- Auditoría de logs de autenticación (login, refresh, reset).
- `VITE_AUTH_DEV_TOOLS_ENABLED` desactivado en producción.
