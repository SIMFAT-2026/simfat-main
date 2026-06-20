# Arquitectura Integrada — SIMFAT Sistema Completo v2

Fecha: 2026-06-02
Estado: vigente

---

## 1. Visión general del sistema

SIMFAT es un sistema de monitoreo y alerta temprana forestal compuesto por tres componentes desplegados independientemente y una capa de integraciones externas.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        USUARIOS / ROLES                             │
│  Coordinador territorial · Moderador · Admin · Super Admin          │
└───────────────────┬─────────────────────────────────────────────────┘
                    │ HTTPS
         ┌──────────▼──────────┐
         │   FRONTEND (Vercel) │
         │   React 18 + Vite   │
         │   react-leaflet     │
         │   Recharts          │
         └──────────┬──────────┘
                    │ HTTPS / JWT Bearer
         ┌──────────▼──────────────┐
         │  BACKEND (Railway)      │
         │  Spring Boot 3 / Java   │
         │  ├─ PostgreSQL (Supabase)│  ← Auth / RBAC
         │  └─ MongoDB (Atlas)     │  ← Datos operacionales
         └──────┬──────────┬───────┘
                │           │
      ┌─────────▼─┐    ┌────▼──────────────────────────┐
      │ openeo-   │    │  APIs externas                 │
      │ service   │    │  ├─ NASA FIRMS (focos activos) │
      │ (Railway) │    │  ├─ Open-Meteo (FWI/clima)     │
      │ FastAPI   │    │  └─ Supabase Storage (fotos)   │
      └─────┬─────┘    └───────────────────────────────┘
            │
      ┌─────▼──────────────┐
      │ Copernicus CDSE     │
      │ (OpenEO protocol)   │
      │ Sentinel-2 NDVI/NDMI│
      └─────────────────────┘
```

---

## 2. Capas del backend Spring Boot

```
com.simfat.backend
├── controller/          ← Capa REST (HTTP in/out)
│   ├── AuthController
│   ├── RegionController
│   ├── TerritoryController     ← Módulo territorial principal
│   ├── AlertRuleController
│   ├── HeatAlertController
│   ├── CommunityController
│   ├── CitizenReportController
│   ├── ForestLossController
│   ├── DashboardController
│   └── OpenEoIngestController
│
├── service/             ← Lógica de negocio (interfaces)
│   ├── AuthService
│   ├── RegionService
│   ├── TerritoryRiskService    ← Score de riesgo por región
│   ├── ComunaRiskService       ← Score WLC por comuna (STANDARD/ENHANCED)
│   ├── NasaFirmsService        ← Sync focos activos cada 12h
│   ├── OpenWeatherFwiService   ← Sync FWI cada 12h
│   ├── OpenEoSyncService       ← Sync Copernicus diario
│   ├── OpenEoIngestService
│   ├── AlertRuleService
│   ├── HeatAlertService
│   ├── CitizenReportService
│   ├── CommunityService
│   ├── ForestLossService
│   ├── DashboardService
│   ├── DashboardIndicatorService
│   ├── CommunityChatService
│   ├── AccessAdminService
│   ├── SupabaseStorageService
│   ├── TurnstileService
│   └── RateLimiterService
│
├── service/impl/        ← Implementaciones
│
├── model/               ← Documentos MongoDB + Entidades JPA
│
├── repository/          ← Spring Data (MongoDB + JPA)
│
├── dto/                 ← Objetos de transferencia (50+)
│
├── config/              ← Configuración Spring
│   ├── MonitoredComunasConfig  ← Seed comunas + regiones piloto al arranque
│   ├── DataSeederConfig        ← Seed datos de prueba
│   ├── OpenEoProperties
│   ├── SecurityIntegrationConfig
│   └── StaticResourceConfig    ← Servicio GeoJSON estáticos
│
├── integration/openeo/  ← Cliente HTTP hacia openeo-service
│
└── exception/           ← Manejo global de errores
```

---

## 3. Flujo de sincronización de datos

```
CICLO DE SYNC (cada 12h: 00:00 y 12:00 UTC)
┌──────────────────────────────────────────────────────────┐
│ 00:00 UTC  NasaFirmsService @Scheduled                   │
│   → GET firms.modaps.eosdis.nasa.gov/api (VIIRS_NOAA20)  │
│   → Filtra focos con confidence = nominal|high           │
│   → Persiste HeatAlertEvent en MongoDB                   │
│                                                          │
│ 00:30 UTC  OpenWeatherFwiService @Scheduled              │
│   → GET api.open-meteo.com (FWI por centroide comunal)   │
│   → Persiste TerritoryWeatherObservation en MongoDB      │
│                                                          │
│ 00:00 UTC  OpenEoSyncService @Scheduled (diario)         │
│   → POST openeo-service/openeo/indicators/latest/NDVI    │
│   → POST openeo-service/openeo/indicators/latest/NDMI    │
│   → openeo-service llama Copernicus CDSE                 │
│   → Persiste OpenEoIndicatorObservation en MongoDB       │
│                                                          │
│ 01:00 + 13:00 UTC  ComunaRiskService @Scheduled         │
│   → Para cada ComunaInfo (86 comunas):                   │
│     1. fwiService.syncFwiByRegion(centroide)             │
│     2. recomputeByComuna(gadmGid)                        │
│        ├─ Lee FWI, FIRMS, Reportes                       │
│        ├─ Score STANDARD = FWI(52%)+FIRMS(33%)+Rep(15%) │
│        ├─ Si score >= 0.50 AND Copernicus <= 6 días:     │
│        │   Score ENHANCED = FWI(38%)+NDMI(22%)+         │
│        │                    FIRMS(18%)+Loss(10%)+        │
│        │                    NDVI(8%)+Rep(4%)             │
│        └─ Persiste ComunaRiskSnapshot                    │
└──────────────────────────────────────────────────────────┘
```

---

## 4. Diagrama de módulos frontend

```
src/
├── app/                 ← App root, router, context providers
├── api/
│   ├── axiosClient.js   ← HTTP client con interceptor JWT + refresh
│   └── endpoints.js
│
├── auth/
│   ├── AuthContext.jsx  ← Estado global de autenticación
│   ├── ProtectedRoute.jsx
│   └── tokenStorage.js
│
├── features/
│   ├── territory/       ← Módulo territorial (principal)
│   │   ├── components/
│   │   │   ├── TerritoryMapPanel.jsx    ← Orquestador del mapa
│   │   │   ├── ComunaRiskPanel.jsx      ← Panel lateral por comuna
│   │   │   └── (ComunaChoropleth,       ← Inline en TerritoryMapPanel
│   │   │       ComunaTooltip,
│   │   │       RiskScoreBadge)
│   │   ├── hooks/
│   │   │   └── useTerritoryLayers.js    ← Estado + cache 120s
│   │   └── services/
│   │       └── territoryApiService.js
│   │
│   ├── community/       ← Módulo comunidad
│   │   ├── hooks/useCommunityData.js
│   │   └── chat/CommunityChatPanel.jsx
│   │
│   ├── alerts/          ← Módulo alertas
│   ├── reports/         ← Reportes ciudadanos
│   │   └── hooks/useCitizenReportsData.js
│   └── dashboard/       ← Dashboard general
│
├── pages/               ← Páginas top-level
│   ├── TerritoryPage.jsx
│   ├── CommunityPage.jsx
│   ├── AlertsPage.jsx
│   ├── CitizenReportsPage.jsx
│   ├── DashboardPage.jsx
│   ├── ForestLossPage.jsx
│   ├── RegionsPage.jsx
│   ├── RulesPage.jsx
│   └── AccessControlPage.jsx
│
└── components/          ← Reutilizables
    ├── AlertBadge, DataTable, EmptyState
    ├── ErrorMessage, LoadingSpinner
    ├── FilterBar, MetricCard, ConfirmModal
    └── Layout (Navbar, Sidebar, Footer)
```

---

## 5. Arquitectura de seguridad

```
REQUEST
   │
   ▼
[JwtAuthFilter]
   │ extrae Bearer token
   ├── token inválido → 401
   └── token válido
          │
          ▼
   [Spring Security]
          │ @PreAuthorize("hasAuthority('...')")
          │
          ├── ROLE_SUPER_ADMIN  → acceso total
          ├── ROLE_ADMIN        → gestión + sync
          ├── ROLE_MODERATOR    → moderación comunidad/reportes
          ├── ROLE_VERIFIED_USER → operaciones territoriales + chat
          └── (sin rol)         → solo endpoints públicos

PERMISOS GRANULARES (vía RBAC moderno):
  PERM_REGION_MANAGE       → CRUD regiones + alertas
  PERM_ALERT_RULE_MANAGE   → CRUD reglas de alerta
  PERM_COMMUNITY_BOARD_MANAGE → gestión tablón
  PERM_COMMUNITY_RESOURCE_MANAGE → gestión recursos/contactos
  PERM_REPORT_CREATE       → crear reporte ciudadano
  PERM_REPORT_MODERATE     → moderar reportes
  PERM_DASHBOARD_SYNC_RUN  → ejecutar sync manual

TOKENS:
  Access token:  JWT · TTL 15 min
  Refresh token: hash en PostgreSQL · TTL 14 días · rotación
```

---

## 6. Bases de datos — asignación de responsabilidades

```
PostgreSQL (Supabase)
└── Dominio: Identidad y acceso
    ├── app_users           ← Cuentas de usuario
    ├── refresh_tokens      ← Gestión de sesiones
    ├── password_reset_tokens
    ├── roles               ← Definición de roles RBAC
    ├── permissions         ← Permisos granulares
    ├── role_permissions    ← Asignación permiso↔rol
    ├── user_roles          ← Asignación usuario↔rol
    ├── user_verification   ← Estado de verificación
    ├── verification_events ← Historial de verificación
    ├── user_community_profiles ← Perfil + región primaria
    └── community_chat_room_access ← Acceso a salas de chat

MongoDB (Atlas)
└── Dominio: Datos operacionales y territoriales
    ├── regions             ← Regiones monitoreadas + aoiBbox
    ├── comunas             ← ComunaInfo: 86 comunas GADM 4.1
    ├── comuna_risk_snapshots   ← Score WLC por comuna (STANDARD/ENHANCED)
    ├── territory_risk_snapshots ← Score WLC por región
    ├── territory_weather_observations ← FWI y componentes por punto
    ├── heat_alert_events   ← Focos FIRMS + alertas de calor
    ├── openeo_indicator_observations ← NDVI/NDMI Copernicus
    ├── alert_rules         ← Reglas de alerta por región
    ├── citizen_reports     ← Reportes ciudadanos geolocalizados
    ├── forest_loss_records ← Pérdida forestal histórica por región/año
    ├── community_board_posts ← Avisos del tablón comunitario
    ├── community_resources ← Recursos comunitarios
    ├── community_contacts  ← Contactos de emergencia
    ├── community_chat_rooms ← Salas de chat (REGIONAL_PUBLIC/TOPIC/CRISIS)
    ├── community_chat_messages ← Mensajes con moderación
    ├── community_chat_presences ← Estado online/away/offline
    ├── community_chat_moderation_events ← Log de moderación
    └── dashboard_region_snapshots ← Snapshots del dashboard
```

---

## 7. Assets estáticos GeoJSON (Spring Boot classpath)

```
src/main/resources/static/geojson/
├── comunas-biobio.geojson     ← 33 comunas · 238 KB · GADM 4.1 Level 3
└── comunas-araucania.geojson  ← 32 comunas · 160 KB · GADM 4.1 Level 3

Servidos en: GET /geojson/{filename}
CORS habilitado para /geojson/**
Licencia: GADM — libre uso académico/no-comercial
```

---

## 8. Deploy y configuración de entornos

```
                  ┌─────────────────────────────┐
                  │  GitHub (SIMFAT-2026/main)   │
                  │  branch: main                │
                  └────────────┬────────────────┘
                               │ push
              ┌────────────────┴──────────────────┐
              │                                   │
    ┌─────────▼──────────┐            ┌───────────▼──────────┐
    │  Railway            │            │  Vercel               │
    │  ├─ simfat-backend  │            │  simfat-web           │
    │  └─ openeo-service  │            │  (React/Vite build)   │
    └─────────────────────┘            └───────────────────────┘
          │ Env vars: Railway              │ Env vars: Vercel
          ├─ POSTGRES_URI                  └─ VITE_API_URL
          ├─ MONGODB_URI
          ├─ AUTH_JWT_SECRET
          ├─ FIRMS_MAP_KEY
          ├─ OPENEO_SERVICE_BASE_URL
          └─ OPENEO_AOI_BBOX_MAP (opcional)
```

---

## 9. Diagrama de secuencia — Carga del mapa territorial

```
Browser          Frontend          Backend           MongoDB
  │                 │                 │                 │
  │ GET /territory  │                 │                 │
  │────────────────▶│                 │                 │
  │                 │ Promise.all(5)  │                 │
  │                 │────────────────▶│ /bounds         │
  │                 │────────────────▶│ /layers         │
  │                 │────────────────▶│ /risk-score     │
  │                 │────────────────▶│ /risk-score/comunas
  │                 │                 │────────────────▶│ findAll comunas
  │                 │ GET /geojson/   │                 │◀────────────────
  │                 │ comunas-*.geojson (static asset)  │
  │                 │◀────────────────│                 │
  │                 │                 │◀────────────────│ snapshots
  │                 │◀────────────────│                 │
  │ render choropleth│                │                 │
  │◀────────────────│                 │                 │
  │                 │                 │                 │
  │ [hover comuna]  │                 │                 │
  │─────────────────▶ ComunaTooltip  │                 │
  │◀───────────────── (datos en cache)│                 │
  │                 │                 │                 │
  │ [click comuna]  │                 │                 │
  │─────────────────▶ ComunaRiskPanel │                 │
  │                 │────────────────▶│ /history?days=30│
  │                 │                 │────────────────▶│
  │                 │◀────────────────│◀────────────────│
  │◀───────────────── panel + chart   │                 │
```
