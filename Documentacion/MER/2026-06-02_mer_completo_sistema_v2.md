# MER Completo — SIMFAT Sistema v2

Fecha: 2026-06-02
Estado: vigente

---

## 1. PostgreSQL — Dominio de identidad y acceso (Supabase)

### Diagrama relacional

```
app_users ──────────────────────────────────────────────────────────
  id PK │ email UNIQUE │ full_name │ password_hash │ enabled │ roles
        │              │           │               │         │
        │◄─────────────────────────────────────────┘         │
        │                                                     │
        │──── user_roles ────── roles ──── role_permissions ── permissions
        │     user_id FK        id PK      role_id FK          id PK
        │     role_id FK        code       permission_id FK    code
        │     assigned_by FK    name                           module
        │     assigned_at       is_system                      name
        │
        │──── refresh_tokens
        │     id PK · user_id FK · token_hash · issued_at · expires_at
        │     revoked_at · replaced_by_token_id
        │
        │──── password_reset_tokens
        │     id PK · user_id FK · token_hash · expires_at · consumed_at
        │
        │──── user_verification (1:1)
        │     user_id PK FK · status · email_verified_at
        │     identity_verified_at · organization_name
        │     trust_score · reputation_score
        │
        │──── verification_events (1:N)
        │     id PK · user_id FK · event_type · old_status · new_status
        │     reviewed_by FK · notes · created_at
        │
        │──── user_community_profiles (1:1)
        │     user_id PK FK · primary_region_id · updated_at
        │
        └──── community_chat_room_access (1:N)
              id PK · user_id FK · region_id · granted_by FK
              granted_at · revoked_at
```

### Tablas — detalle completo

#### app_users
| Columna | Tipo | Constraint |
|---|---|---|
| id | VARCHAR(36) | PK |
| email | VARCHAR(180) | NOT NULL UNIQUE |
| full_name | VARCHAR(120) | NOT NULL |
| password_hash | VARCHAR(100) | NOT NULL |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE |
| roles | VARCHAR(255) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

#### refresh_tokens
| Columna | Tipo | Constraint |
|---|---|---|
| id | VARCHAR(36) | PK |
| token_id | VARCHAR(64) | UNIQUE |
| user_id | VARCHAR(36) | FK → app_users |
| token_hash | VARCHAR(128) | UNIQUE |
| issued_at | TIMESTAMPTZ | NOT NULL |
| expires_at | TIMESTAMPTZ | NOT NULL |
| revoked_at | TIMESTAMPTZ | NULL |
| replaced_by_token_id | VARCHAR(64) | NULL |
| created_by_ip | VARCHAR(64) | |
| user_agent | VARCHAR(512) | |

**Índices:** user_id, revoked_at, expires_at

#### password_reset_tokens
| Columna | Tipo | Constraint |
|---|---|---|
| id | VARCHAR(36) | PK |
| token_hash | VARCHAR(128) | UNIQUE |
| user_id | VARCHAR(36) | FK → app_users |
| created_at | TIMESTAMPTZ | NOT NULL |
| expires_at | TIMESTAMPTZ | NOT NULL |
| consumed_at | TIMESTAMPTZ | NULL |

#### roles
| Columna | Tipo | Constraint |
|---|---|---|
| id | VARCHAR(36) | PK |
| code | VARCHAR(80) | UNIQUE |
| name | VARCHAR(120) | |
| description | VARCHAR(255) | |
| is_system | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

**Roles del sistema:** ROLE_SUPER_ADMIN, ROLE_ADMIN, ROLE_MODERATOR, ROLE_VERIFIED_USER

#### permissions
| Columna | Tipo | Constraint |
|---|---|---|
| id | VARCHAR(36) | PK |
| code | VARCHAR(120) | UNIQUE |
| name | VARCHAR(150) | |
| module | VARCHAR(80) | |
| description | VARCHAR(255) | |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

**Permisos del sistema:**

| Código | Módulo | Descripción |
|---|---|---|
| PERM_REGION_MANAGE | territory | CRUD regiones y alertas |
| PERM_ALERT_RULE_MANAGE | territory | CRUD reglas de alerta |
| PERM_COMMUNITY_BOARD_MANAGE | community | Gestión tablón comunitario |
| PERM_COMMUNITY_RESOURCE_MANAGE | community | Gestión recursos y contactos |
| PERM_REPORT_CREATE | reports | Crear reporte ciudadano |
| PERM_REPORT_MODERATE | reports | Moderar reportes |
| PERM_DASHBOARD_SYNC_RUN | dashboard | Ejecutar sync manual |

#### role_permissions
| Columna | Tipo | Constraint |
|---|---|---|
| role_id | VARCHAR(36) | PK, FK → roles |
| permission_id | VARCHAR(36) | PK, FK → permissions |
| created_at | TIMESTAMPTZ | NOT NULL |

#### user_roles
| Columna | Tipo | Constraint |
|---|---|---|
| user_id | VARCHAR(36) | PK, FK → app_users |
| role_id | VARCHAR(36) | PK, FK → roles |
| assigned_by | VARCHAR(36) | FK → app_users |
| assigned_at | TIMESTAMPTZ | NOT NULL |

#### user_verification
| Columna | Tipo | Constraint |
|---|---|---|
| user_id | VARCHAR(36) | PK, FK → app_users |
| status | VARCHAR(40) | |
| email_verified_at | TIMESTAMPTZ | NULL |
| phone_verified_at | TIMESTAMPTZ | NULL |
| identity_verified_at | TIMESTAMPTZ | NULL |
| organization_name | VARCHAR(120) | |
| organization_verified_at | TIMESTAMPTZ | NULL |
| trust_score | NUMERIC(5,2) | |
| reputation_score | INTEGER | |
| updated_at | TIMESTAMPTZ | NOT NULL |

#### user_community_profiles
| Columna | Tipo | Constraint |
|---|---|---|
| user_id | VARCHAR(36) | PK, FK → app_users |
| primary_region_id | VARCHAR(80) | |
| updated_at | TIMESTAMPTZ | NOT NULL |

#### community_chat_room_access
| Columna | Tipo | Constraint |
|---|---|---|
| id | VARCHAR(36) | PK |
| user_id | VARCHAR(36) | FK → app_users |
| region_id | VARCHAR(80) | |
| granted_by | VARCHAR(36) | FK → app_users |
| granted_at | TIMESTAMPTZ | NOT NULL |
| revoked_at | TIMESTAMPTZ | NULL |

**Índice único activo:** (user_id, region_id) WHERE revoked_at IS NULL

---

## 2. MongoDB — Dominio operacional territorial

### Diagrama de colecciones

```
regions ──────────────────────────────────────────────
  _id: String ("biobio" | "araucania" | ...)
  nombre · codigo · zona · hectareasBosqueReferencia
  aoiBbox: [west, south, east, north]
  │
  │◄── comunas (ComunaInfo)
  │    _id: gadmGid (ej. "CHL.6.2.1_1")
  │    nombre · provincia · regionId FK
  │    centerLat · centerLon · gadmGid
  │    │
  │    │◄── comuna_risk_snapshots
  │    │    _id · comunaId FK · regionId · nombreComuna
  │    │    computedAt · scoreComposite · alertLevel
  │    │    mode: STANDARD|ENHANCED
  │    │    qualityFlag: null|PARTIAL|COPERNICUS_UNAVAILABLE
  │    │    componentFwi · componentFirms · componentReports
  │    │    componentNdmi · componentNdvi · componentLoss
  │    │    openeoObservationId FK
  │    │    fwiRaw · firmsCount · firmsFrpMean
  │    │    reportsCount · ndmiRaw · ndviRaw
  │    │
  │    └── (referencias via region)
  │
  │◄── territory_risk_snapshots
  │    _id · regionId FK · computedAt
  │    scoreComposite · alertLevel · qualityFlag
  │    componentFwi · componentNdmi · componentFirms
  │    componentLoss · componentNdvi · componentReports
  │    fwiRaw · ndmiRaw · ndviRaw · firmsCount
  │    firmsFrpMean · lossRate · reportsCount
  │
  │◄── territory_weather_observations
  │    _id · regionId FK · observedAt · source
  │    fwi · ffmc · dmc · dc · isi · bui · dsr
  │    created_at
  │
  │◄── openeo_indicator_observations
  │    _id · regionId FK · indicator: NDVI|NDMI
  │    observedAt · value · unit · aoi
  │    quality · source · ingestedAt
  │    [UK: regionId + indicator + observedAt]
  │
  │◄── heat_alert_events
  │    _id · regionId FK · fechaEvento
  │    nivelRiesgo: BAJO|MEDIO|ALTO|CRITICO
  │    latitud · longitud · fuente · descripcion
  │    firmsConfidence: l|n|h
  │    firmsFrp (MW) · firmsSatellite · firmsSource
  │
  │◄── alert_rules
  │    _id · regionId FK · nombre · activa
  │    umbralPorcentajePerdida
  │    umbralEventosCalor
  │
  │◄── forest_loss_records
  │    _id · regionId FK · anio · hectareasPerdidas
  │    porcentajePerdida · fuente · fechaRegistro
  │
  │◄── citizen_reports
  │    _id · regionId FK · category: HUMO|FOCO|INCENDIO|OTRO
  │    description · latitude · longitude
  │    status: RECIBIDO|VALIDADO|DERIVADO|DESCARTADO
  │    photos: [String] · createdAt · updatedAt
  │
  │◄── community_board_posts
  │    _id · regionId FK · title · message
  │    priority: BAJA|MEDIA|ALTA · author · publishedAt
  │
  │◄── community_resources
  │    _id · regionId FK · title · category
  │    url · description · createdAt
  │
  │◄── community_contacts
  │    _id · regionId FK · name · organization
  │    phone · email · protocol · createdAt
  │
  │◄── community_chat_rooms
  │    _id · regionId FK · name · description
  │    type: REGIONAL_PUBLIC|TOPIC|CRISIS
  │    createdAt · updatedAt
  │    │
  │    └◄── community_chat_messages
  │         _id · roomId FK · userId · username
  │         message · createdAt
  │         status: SENT|MODERATED_HIDDEN|DELETED
  │
  │◄── community_chat_presences
       _id · userId · roomId FK
       state: ONLINE|AWAY|OFFLINE · lastSeenAt
```

### Colecciones — detalle completo

#### regions
| Campo | Tipo | Notas |
|---|---|---|
| _id | String | `"biobio"` \| `"araucania"` |
| nombre | String | max 120 chars |
| codigo | String | `"BIOBIO"` \| `"ARAUCANIA"` — clave para bbox fallback |
| zona | String | max 50 chars |
| hectareasBosqueReferencia | Double | > 0 |
| aoiBbox | List\<Double\> | `[west, south, east, north]` EPSG:4326 |

**Valores piloto:**
- biobio: aoiBbox = `[-73.6, -38.5, -71.0, -36.7]`, 1 500 000 ha
- araucania: aoiBbox = `[-73.6, -40.0, -70.8, -37.6]`, 1 750 000 ha

#### comunas (ComunaInfo)
| Campo | Tipo | Notas |
|---|---|---|
| _id | String | = gadmGid (ej. `"CHL.6.2.1_1"`) |
| nombre | String | Nombre oficial de la comuna |
| provincia | String | Provincia GADM |
| regionId | String | FK → regions._id |
| regionGadm | String | Nombre de región en GADM |
| gadmGid | String | GADM GID Level 3 |
| centerLat | Double | Latitud del centroide |
| centerLon | Double | Longitud del centroide |

**Índice:** regionId
**Origen:** GADM 4.1 Level 3 Chile — seeded desde GeoJSON en classpath al arranque

#### comuna_risk_snapshots
| Campo | Tipo | Notas |
|---|---|---|
| _id | String | UUID generado |
| comunaId | String | FK → comunas._id (gadmGid) |
| regionId | String | FK → regions._id |
| nombreComuna | String | Desnormalizado para queries |
| computedAt | LocalDateTime | UTC |
| scoreComposite | Double | [0.0, 1.0] |
| alertLevel | String | NORMAL \| PREVENTIVO \| ALTO \| CRITICO |
| mode | String | STANDARD \| ENHANCED |
| qualityFlag | String | null \| PARTIAL \| COPERNICUS_UNAVAILABLE |
| componentFwi | Double | Contribución normalizada FWI |
| componentFirms | Double | Contribución normalizada FIRMS |
| componentReports | Double | Contribución normalizada reportes |
| componentNdmi | Double | null si STANDARD |
| componentNdvi | Double | null si STANDARD |
| componentLoss | Double | null si STANDARD; 0.0 en fase piloto |
| openeoObservationId | String | FK → openeo_indicator_observations._id |
| fwiRaw | Double | Valor crudo FWI |
| firmsCount | Integer | Número de focos asignados |
| firmsFrpMean | Double | FRP promedio en MW |
| reportsCount | Integer | Reportes activos en 7 días |
| ndmiRaw | Double | Valor Sentinel-2 crudo |
| ndviRaw | Double | Valor Sentinel-2 crudo |

**Índices compuestos:**
- (comunaId, computedAt DESC)
- (regionId, computedAt DESC)

**Umbrales de alertLevel:**
| Nivel | Score compuesto | Overrides |
|---|---|---|
| NORMAL | < 0.50 | — |
| PREVENTIVO | >= 0.50 | FWI >= 20 |
| ALTO | >= 0.70 | FWI >= 30 \| cualquier foco FIRMS |
| CRITICO | >= 0.85 | FWI >= 45 \| foco confidence=high |

#### openeo_indicator_observations
| Campo | Tipo | Notas |
|---|---|---|
| _id | String | UUID |
| regionId | String | FK → regions._id |
| indicator | IndicatorType | NDVI \| NDMI |
| observedAt | LocalDateTime | Fecha de la imagen satelital |
| value | Double | Valor promedio en el AOI |
| unit | String | |
| aoi | String | `"west,south,east,north"` |
| quality | String | |
| source | String | `"copernicus_cdse"` |
| ingestedAt | LocalDateTime | Cuando se guardó |

**Índices compuestos:**
- (regionId, indicator, observedAt DESC)
- UK: (regionId, indicator, observedAt)

#### heat_alert_events
| Campo | Tipo | Notas |
|---|---|---|
| _id | String | UUID |
| regionId | String | FK → regions._id |
| fechaEvento | LocalDateTime | UTC |
| nivelRiesgo | RiskLevel | BAJO \| MEDIO \| ALTO \| CRITICO |
| latitud | Double | [-90, 90] |
| longitud | Double | [-180, 180] |
| fuente | String | `"NASA_FIRMS"` \| `"MANUAL"` |
| descripcion | String | max 500 chars |
| firmsConfidence | String | `"l"` \| `"n"` \| `"h"` |
| firmsFrp | Double | Fire Radiative Power en MW |
| firmsSatellite | String | `"N"` (NOAA-20) \| `"S"` (Suomi NPP) |
| firmsSource | String | `"VIIRS_NOAA20_NRT"` |

#### citizen_reports
| Campo | Tipo | Notas |
|---|---|---|
| _id | String | UUID |
| regionId | String | FK → regions._id |
| category | String | HUMO \| FOCO \| INCENDIO \| OTRO |
| description | String | Descripción del reporte |
| latitude | Double | Ubicación del evento |
| longitude | Double | Ubicación del evento |
| status | CitizenReportStatus | RECIBIDO \| VALIDADO \| DERIVADO \| DESCARTADO |
| photos | List\<String\> | Rutas a archivos (Supabase o local) |
| createdAt | LocalDateTime | |
| updatedAt | LocalDateTime | |

---

## 3. Relaciones entre dominios

```
PostgreSQL ←──────────── REFERENCIA LÓGICA (no FK física) ──────────→ MongoDB

app_users.id ────────────────────────────────────────────────────────▶
  referenciado como String en:
  - community_chat_messages.userId
  - community_chat_presences.userId
  - CommunityChatModerationEvent.moderatorId

user_community_profiles.primary_region_id ───────────────────────────▶
  regions._id (MongoDB)

community_chat_room_access.region_id ────────────────────────────────▶
  regions._id (MongoDB)
```

---

## 4. Lógica de score WLC — resumen matemático

### Modo STANDARD (sin Copernicus)

```
score = FWI_norm × 0.52
      + FIRMS_norm × 0.33
      + Reports_norm × 0.15

FWI_norm    = clamp((fwiRaw - 0) / 50)
FIRMS_norm  = clamp(count/5) × 0.6 + clamp(frpMean/80) × 0.4
Reports_norm = clamp(count / 3)
```

### Modo ENHANCED (con Copernicus, score_standard >= 0.50)

```
score = FWI_norm × 0.38
      + NDMI_norm × 0.22
      + FIRMS_norm × 0.18
      + Loss_norm × 0.10     ← 0.0 en fase piloto
      + NDVI_norm × 0.08
      + Reports_norm × 0.04

NDMI_norm = 1 − clamp((ndmiRaw + 0.4) / 0.8)   ← inverso: seco = alto riesgo
NDVI_norm = clamp((ndviRaw − 0.1) / 0.7)        ← más combustible = más riesgo
```

**Condición de activación ENHANCED:**
- score_standard >= 0.50 **Y**
- OpenEoIndicatorObservation NDVI + NDMI para la región
- observedAt <= 6 días de antigüedad (ventana revisita Sentinel-2)
