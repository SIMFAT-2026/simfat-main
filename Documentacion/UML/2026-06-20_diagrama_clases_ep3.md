# Diagrama de Clases — SIMFAT EP3

- **Curso:** TPY1101 – Taller Aplicado de Programación
- **Institución:** Duoc UC
- **Estudiante:** David Vásquez
- **Fecha:** 2026-06-20
- **Versión:** 1.0

---

## Contexto

El diagrama cubre las entidades principales del backend de SIMFAT al cierre de EP3. Las colecciones de MongoDB y las tablas de PostgreSQL se presentan juntas para mostrar la arquitectura de datos completa. Se indican las relaciones conceptuales entre entidades aunque estén en bases de datos distintas.

**Convención de almacenamiento:**
- Entidades sin nota → MongoDB (`simfat` DB)
- Entidades marcadas con `<<PostgreSQL>>` → PostgreSQL (`simfat` DB)

---

## Diagrama

```mermaid
classDiagram

  %% ─── PostgreSQL (identidad y acceso) ───────────────────────────────────────

  class User {
    <<PostgreSQL>>
    +Long id
    +String email
    +String fullName
    +String passwordHash
    +Boolean verified
    +LocalDateTime createdAt
  }

  class UserRole {
    <<PostgreSQL>>
    +Long userId
    +String roleCode
  }

  class Role {
    <<PostgreSQL>>
    +String code
    +String name
    +String description
  }

  %% ─── MongoDB (dominio territorial) ─────────────────────────────────────────

  class Region {
    +String id
    +String nombre
    +String codigo
    +String zona
    +Double hectareasBosqueReferencia
    +Boolean monitoringEnabled
    +LocalDateTime updatedAt
  }

  class AlertRule {
    +String id
    +String nombre
    +String regionId
    +Double umbralFwi
    +Double umbralNdmi
    +Double umbralNdvi
    +Integer umbralFirmsCount
    +Integer umbralReportesCiudadanos
    +Boolean activa
    +LocalDateTime createdAt
  }

  class Alert {
    +String id
    +String level
    +String regionId
    +String comunaId
    +String message
    +String ruleId
    +LocalDateTime createdAt
  }

  class Notification {
    +String id
    +String title
    +String body
    +String type
    +Boolean read
    +LocalDateTime createdAt
    +String regionId
    +Long userId
    +String alertId
  }

  class ComunaRiskScore {
    +String gadmGid
    +String nombreComuna
    +String regionId
    +Double scoreComposite
    +String alertLevel
    +String mode
    +Map components
    +LocalDateTime computedAt
    +LocalDateTime copernicusSyncedAt
  }

  class RegionalRiskScore {
    +String regionId
    +Double scoreComposite
    +String alertLevel
    +Integer comunasCount
    +Integer comunasEnAlto
    +Integer comunasEnCritico
    +LocalDateTime generatedAt
  }

  %% ─── MongoDB (comunidad) ────────────────────────────────────────────────────

  class CommunityPost {
    +String id
    +String title
    +String message
    +String priority
    +String regionId
    +LocalDateTime publishedAt
    +String author
    +Long authorUserId
  }

  class CommunityContact {
    +String id
    +String name
    +String organization
    +String phone
    +String email
    +String regionId
    +String comunaId
    +String protocol
    +LocalDateTime createdAt
  }

  class CitizenReport {
    +String id
    +String category
    +String description
    +Double[] coordinates
    +String regionId
    +String comunaId
    +LocalDateTime createdAt
    +Long userId
    +String status
  }

  %% ─── Relaciones ─────────────────────────────────────────────────────────────

  User "1" ||--o{ UserRole : "tiene"
  UserRole }o--|| Role : "asigna"

  AlertRule }o--|| Region : "monitorea"
  AlertRule "1" --o{ Alert : "dispara"

  Alert }o--|| Region : "pertenece a"
  Alert "1" --o{ Notification : "genera"

  Notification }o--|| User : "notifica a"

  ComunaRiskScore }o--|| Region : "pertenece a"
  RegionalRiskScore }o--|| Region : "consolida"

  CommunityPost }o--|| Region : "publicado en"
  CommunityContact }o--|| Region : "asociado a"

  CitizenReport }o--|| User : "enviado por"
  CitizenReport }o--|| Region : "reportado en"
```

---

## Notas de diseño

### Separación de bases de datos

| Base de datos | Rol | Entidades principales |
|---|---|---|
| PostgreSQL | Identidad, autenticación, autorización | `User`, `UserRole`, `Role` |
| MongoDB | Dominio de negocio — datos territoriales y comunitarios | Todas las demás entidades |

Esta separación es intencional (ver `modelo-logico-actualizacion-backend-2026-05-14-rbac.md`): PostgreSQL garantiza consistencia transaccional para la gestión de identidad y permisos, mientras MongoDB ofrece flexibilidad de esquema para los datos de monitoreo que evolucionan con frecuencia.

### Campo `components` en ComunaRiskScore

El campo `components` es un mapa dinámico con la siguiente estructura por indicador:

```json
{
  "FWI":      { "score": 0.72, "weight": 0.30, "rawValue": 18.5 },
  "NDMI":     { "score": 0.45, "weight": 0.25, "rawValue": -0.12 },
  "NDVI":     { "score": 0.38, "weight": 0.20, "rawValue": 0.41 },
  "FIRMS":    { "score": 0.60, "weight": 0.15, "focosCount": 3 },
  "REPORTS":  { "score": 0.20, "weight": 0.10, "count": 1 }
}
```

El `scoreComposite` se calcula como `Σ(component.score × component.weight)`.

### Campo `mode` en ComunaRiskScore

- `STANDARD`: calculado con datos locales (FWI, FIRMS, reportes ciudadanos) sin confirmación satelital.
- `ENHANCED`: calculado incluyendo datos Copernicus/OpenEO (NDVI, NDMI) obtenidos via sync manual o programado.

### Niveles de alerta (AlertLevel)

Los niveles válidos son: `NORMAL`, `PREVENTIVO`, `ALTO`, `CRÍTICO`. Se determinan por umbrales configurados en `AlertRule` y se almacenan desnormalizados en `ComunaRiskScore` y `RegionalRiskScore` para consulta eficiente sin recalcular.
