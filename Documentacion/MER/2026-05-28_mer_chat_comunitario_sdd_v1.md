# MER Chat Comunitario SDD

Fecha: 2026-05-28  
Alcance: modelo de datos agregado para chat comunitario territorial.

## Vista integrada

```mermaid
erDiagram
  APP_USERS ||--o{ USER_ROLES : has
  ROLES ||--o{ USER_ROLES : assigned
  ROLES ||--o{ ROLE_PERMISSIONS : grants
  PERMISSIONS ||--o{ ROLE_PERMISSIONS : maps
  APP_USERS ||--o| USER_VERIFICATION : verification
  APP_USERS ||--o| USER_COMMUNITY_PROFILES : community_profile
  APP_USERS ||--o{ COMMUNITY_CHAT_ROOM_ACCESS : granted_access
  APP_USERS ||--o{ COMMUNITY_CHAT_ROOM_ACCESS : granted_by

  APP_USERS {
    varchar id PK
    varchar email
    varchar full_name
    boolean enabled
  }

  USER_VERIFICATION {
    varchar user_id PK
    varchar status
    timestamp updated_at
  }

  USER_COMMUNITY_PROFILES {
    varchar user_id PK
    varchar primary_region_id
    timestamptz updated_at
  }

  COMMUNITY_CHAT_ROOM_ACCESS {
    varchar id PK
    varchar user_id FK
    varchar region_id
    varchar granted_by FK
    timestamptz granted_at
    timestamptz revoked_at
  }

  PERMISSIONS {
    varchar id PK
    varchar code
    varchar module
  }
```

## MongoDB operativo

```mermaid
classDiagram
  class CommunityChatRoom {
    id
    type
    regionId
    name
    active
    createdAt
  }

  class CommunityChatMessage {
    id
    roomId
    authorUserId
    authorName
    content
    status
    createdAt
    moderatedAt
  }

  class CommunityChatPresence {
    id
    roomId
    userId
    state
    updatedAt
  }

  class CommunityChatModerationEvent {
    id
    messageId
    moderatorUserId
    action
    reason
    createdAt
  }

  CommunityChatRoom "1" --> "many" CommunityChatMessage : contains
  CommunityChatRoom "1" --> "many" CommunityChatPresence : tracks
  CommunityChatMessage "1" --> "many" CommunityChatModerationEvent : audits
```

## Reglas de integridad

- `user_community_profiles.user_id` depende de `app_users.id`.
- `community_chat_room_access.user_id` depende de `app_users.id`.
- `community_chat_room_access.granted_by` permite trazabilidad del administrador que otorgo acceso.
- `revoked_at IS NULL` representa grant activo.
- Mensajes y presencia referencian usuarios por `userId` para trazabilidad cruzada entre MongoDB y PostgreSQL.
