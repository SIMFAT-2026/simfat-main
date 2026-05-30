# Chat comunitario territorial - Implementacion SDD

Fecha: 2026-05-28  
Cambio SDD: `chat-comunitario`  
Modulo propietario: Comunitario  
Estado: implementado localmente y validado

## Resultado

Se implemento un chat interno desplegable dentro del modulo comunitario para coordinacion territorial con foco en prevencion de incendios. El cambio quedo dividido en tres iteraciones locales: fundacion de accesos regionales, backend del chat y frontend del panel comunitario.

## Alcance implementado

| Area | Resultado |
|---|---|
| Identidad | El chat consume identidad del usuario autenticado; no duplica nombre/apellido. |
| Acceso comunitario | Usuarios comunitarios verificados acceden a sala general y sala regional habilitada. |
| Acceso operacional | `ROLE_MODERATOR`, `ROLE_ADMIN` y `ROLE_SUPER_ADMIN` acceden a todas las salas y controles de moderacion. |
| Salas | Sala general y subsalas regionales. |
| Acceso cruzado regional | Gestionado desde administracion de accesos mediante grants por region. |
| Presencia | `CONNECTED`, `AWAY`, `UNAVAILABLE`, `OFFLINE`. |
| Mensajeria | MVP por polling HTTP; WebSocket/SSE queda diferido. |
| Moderacion inicial | Moderacion operativa por identidad verificada; rol moderador queda disponible para escala. |
| Retencion | 6 meses con menos de 6 regiones; 1 mes cuando la operacion escale a 6 o mas regiones. |

## Iteraciones realizadas

### Iteracion 1 - Fundacion y accesos

Commits:

- `6915b4e feat(community-chat): add regional access foundation`
- `cf499ae feat(community-chat): manage regional chat access`

Cambios principales:

- Migracion Flyway `V3__community_chat_access_foundation.sql`.
- Tablas relacionales `user_community_profiles` y `community_chat_room_access`.
- Permisos `PERM_COMMUNITY_CHAT_READ`, `PERM_COMMUNITY_CHAT_SEND`, `PERM_COMMUNITY_CHAT_MODERATE`, `PERM_COMMUNITY_CHAT_ACCESS_MANAGE`.
- Extension de administracion de accesos para region primaria y grants regionales adicionales.

### Iteracion 2 - Backend chat core

Commits:

- `10d2d98 feat(community-chat): add backend chat core`
- `a61bd1f test(dashboard): authorize sync integration test`

Cambios principales:

- Modelos MongoDB: `CommunityChatRoom`, `CommunityChatMessage`, `CommunityChatPresence`, `CommunityChatModerationEvent`.
- Repositorios MongoDB para salas, mensajes, presencia y auditoria de moderacion.
- `CommunityChatService` con reglas de autorizacion por rol, verificacion y grants regionales.
- `CommunityChatController` bajo `/api/community/chat/*`.
- Ajuste de test dashboard para respetar `PERM_DASHBOARD_SYNC_RUN`.

### Iteracion 3 - Frontend chat comunitario

Commit:

- `d1488c4 feat(community-chat): add community chat panel`

Cambios principales:

- `communityChatService.js` para consumir API del chat.
- `useCommunityChat` con polling, cambio de sala, envio, presencia y moderacion.
- `CommunityChatPanel.jsx` como panel desplegable dentro del modulo comunitario.
- Estilos globales para el panel.

## Contrato de acceso

| Usuario | Sala general | Sala regional propia | Otras regiones | Moderacion |
|---|---:|---:|---:|---:|
| Comunitario no verificado | No | No | No | No |
| Comunitario verificado | Si | Si | Solo con grant | No |
| Moderador | Si | Si | Si | Si |
| Admin | Si | Si | Si | Si |
| Superadmin | Si | Si | Si | Si |

## Decision de arquitectura

El chat pertenece al modulo comunitario. La region es contexto operativo para salas y filtros, no cambia el ownership del modulo.

La persistencia se divide asi:

- PostgreSQL/Supabase: identidad, roles, permisos, verificacion, region primaria y grants regionales.
- MongoDB: salas, mensajes, presencia y auditoria de moderacion.

## Riesgos y deuda tecnica

| Riesgo | Estado | Mitigacion |
|---|---|---|
| Moderacion sin equipo dedicado | Aceptado para MVP | Identidad verificada y roles de moderacion preparados. |
| Polling puede crecer en costo | Aceptado para piloto | WebSocket/SSE diferido para escala. |
| Retencion de mensajes | Controlado | Regla 6 meses / 1 mes segun cantidad de regiones. |
| QA visual automatizado | Parcial | Prueba manual por UI paso; Browser plugin no estuvo disponible. |

## Proximo paso

Realizar push y PR hacia `develop` solo despues de confirmar que el branch local esta actualizado contra `origin/develop` y que no se mezclan cambios documentales no deseados.
