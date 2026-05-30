# Arquitectura Chat Comunitario SDD

Fecha: 2026-05-28  
Modulo propietario: Comunitario

## Diagrama de componentes

```mermaid
flowchart TB
  subgraph UI[Frontend React/Vite]
    CommunityPage[CommunityPage]
    ChatPanel[CommunityChatPanel]
    UseChat[useCommunityChat polling hook]
    ChatService[communityChatService]
    AccessPage[AccessControlPage]
  end

  subgraph API[Backend Spring Boot]
    ChatController[CommunityChatController]
    ChatServiceBE[CommunityChatService]
    AccessAdmin[AccessAdminService]
    Security[JWT + RBAC + Verification]
  end

  subgraph PG[PostgreSQL/Supabase]
    Users[app_users]
    Verification[user_verification]
    Profile[user_community_profiles]
    Grants[community_chat_room_access]
    Permissions[permissions + role_permissions]
  end

  subgraph MDB[MongoDB]
    Rooms[community_chat_rooms]
    Messages[community_chat_messages]
    Presence[community_chat_presence]
    Moderation[community_chat_moderation_events]
  end

  CommunityPage --> ChatPanel --> UseChat --> ChatService
  AccessPage --> AccessAdmin
  ChatService --> ChatController --> Security --> ChatServiceBE
  AccessAdmin --> Profile
  AccessAdmin --> Grants
  ChatServiceBE --> Users
  ChatServiceBE --> Verification
  ChatServiceBE --> Profile
  ChatServiceBE --> Grants
  ChatServiceBE --> Rooms
  ChatServiceBE --> Messages
  ChatServiceBE --> Presence
  ChatServiceBE --> Moderation
  Security --> Permissions
```

## Flujo de envio de mensaje

```mermaid
sequenceDiagram
  actor User as Usuario autenticado
  participant UI as CommunityChatPanel
  participant API as CommunityChatController
  participant SVC as CommunityChatService
  participant PG as PostgreSQL identidad/acceso
  participant MDB as MongoDB mensajes

  User->>UI: Escribe mensaje
  UI->>API: POST /api/community/chat/rooms/{roomId}/messages
  API->>SVC: sendMessage(roomId, content, actor)
  SVC->>PG: Verifica rol, identidad, region/grant
  alt autorizado
    SVC->>MDB: Persiste CommunityChatMessage
    MDB-->>SVC: Mensaje guardado
    SVC-->>API: CommunityChatMessageDTO
    API-->>UI: ApiResponse OK
  else no autorizado
    SVC-->>API: Forbidden/Unauthorized
    API-->>UI: Error visible
  end
```

## Flujo de acceso regional cruzado

```mermaid
sequenceDiagram
  actor Admin as Admin/Superadmin
  participant AccessUI as AccessControlPage
  participant AccessAPI as AccessAdminController
  participant AccessSVC as AccessAdminService
  participant PG as community_chat_room_access
  actor Brigadista as Usuario verificado
  participant Chat as CommunityChatService

  Admin->>AccessUI: Otorga region adicional
  AccessUI->>AccessAPI: PUT /api/admin/access/users/{id}/community-chat-access
  AccessAPI->>AccessSVC: updateCommunityChatAccess
  AccessSVC->>PG: Revoca grants activos y guarda nuevos grants
  Brigadista->>Chat: Solicita salas disponibles
  Chat->>PG: Lee region primaria + grants activos
  Chat-->>Brigadista: General + region propia + region adicional
```

## Decision de realtime

El MVP usa polling para reducir complejidad y costo inicial. WebSocket/SSE queda como fase posterior si el volumen de usuarios conectados o la criticidad operativa lo justifican.
