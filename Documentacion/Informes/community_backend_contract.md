# Contrato Backend Minimo - Coordinacion Comunitaria

Fecha: 2026-04-21  
Frontend consumidor: `simfat-web` (`CommunityPage`)

## Objetivo

Habilitar operaciones comunitarias base con bajo costo operacional:

- mural comunitario
- biblioteca de recursos
- contactos y protocolos

## 1) GET /api/community/board

### Query params (opcionales)

- `regionId`

### Response

```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": "board-1",
      "title": "Vigilancia preventiva",
      "message": "Refuerzo de turnos comunitarios",
      "priority": "ALTA",
      "regionId": "biobio",
      "publishedAt": "2026-04-21T15:00:00Z",
      "author": "Unidad territorial"
    }
  ],
  "timestamp": "2026-04-21T15:00:01Z"
}
```

## 2) POST /api/community/board

Body:

```json
{
  "title": "Titulo del aviso",
  "message": "Contenido operativo",
  "priority": "MEDIA",
  "regionId": "araucania",
  "author": "Equipo comunitario"
}
```

## 3) DELETE /api/community/board/{id}

Respuesta recomendada:

```json
{ "success": true, "message": "Eliminado", "data": true, "timestamp": "..." }
```

## 4) GET /api/community/resources

### Query params (opcionales)

- `regionId`

### Response (item)

```json
{
  "id": "resource-1",
  "title": "Guia de prevencion vecinal",
  "category": "GUIA",
  "url": "https://...",
  "regionId": "biobio",
  "description": "Checklist preventivo"
}
```

## 5) POST /api/community/resources

Body:

```json
{
  "title": "Protocolo local",
  "category": "PROTOCOLO",
  "url": "https://...",
  "regionId": "araucania",
  "description": "Pasos de coordinacion"
}
```

## 6) DELETE /api/community/resources/{id}

## 7) GET /api/community/contacts

### Query params (opcionales)

- `regionId`

### Response (item)

```json
{
  "id": "contact-1",
  "name": "Central regional",
  "organization": "Proteccion Civil",
  "phone": "+56 9 ...",
  "email": "contacto@...",
  "regionId": "biobio",
  "protocol": "Escalamiento inmediato ante alerta critica"
}
```

## 8) POST /api/community/contacts

Body:

```json
{
  "name": "Coordinacion brigadas",
  "organization": "Bomberos",
  "phone": "+56 9 ...",
  "email": "brigadas@...",
  "regionId": "araucania",
  "protocol": "Activacion ante foco confirmado"
}
```

## 9) DELETE /api/community/contacts/{id}

## Recomendaciones backend (costo / performance)

1. Filtrar por `regionId` desde base de datos (no en frontend).
2. Limitar campos de salida para listados (evitar payload excesivo).
3. Mantener TTL corto (30-120s) para lectura frecuente de mural.
4. Auditoria minima de creacion/eliminacion (usuario, fecha, m?dulo).

---

# Extension 2026-05-28 - Chat comunitario territorial

## Objetivo

Agregar mensajeria comunitaria interna para coordinacion territorial y prevencion de incendios, sin duplicar identidad ni reemplazar la administracion de accesos existente.

## Endpoints chat

### 10) GET /api/community/chat/rooms

Retorna salas visibles para el usuario autenticado.

Reglas:

- Usuarios comunitarios verificados: sala general + sala regional primaria + grants regionales adicionales.
- `ROLE_MODERATOR`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`: todas las salas.

Response item:

```json
{
  "id": "general",
  "type": "GENERAL",
  "regionId": null,
  "name": "Sala general"
}
```

### 11) GET /api/community/chat/rooms/{roomId}/messages

Query params:

- `after` opcional, ISO date-time.
- `limit` opcional, default 50.

Response item:

```json
{
  "id": "msg-1",
  "roomId": "general",
  "authorUserId": "user-1",
  "authorName": "Nombre Apellido",
  "content": "Actualizacion operativa",
  "status": "ACTIVE",
  "createdAt": "2026-05-28T09:00:00"
}
```

### 12) POST /api/community/chat/rooms/{roomId}/messages

Body:

```json
{
  "content": "Mensaje de coordinacion"
}
```

Restricciones:

- `content` obligatorio.
- Largo maximo: 800 caracteres.
- Autor se toma del JWT/perfil autenticado; no se acepta autor desde frontend.

### 13) PUT /api/community/chat/presence

Body:

```json
{
  "roomId": "general",
  "state": "CONNECTED"
}
```

Estados soportados:

- `CONNECTED`
- `AWAY`
- `UNAVAILABLE`
- `OFFLINE`

### 14) POST /api/community/chat/messages/{messageId}/moderate

Disponible para `ROLE_MODERATOR`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`.

Body:

```json
{
  "action": "HIDE",
  "reason": "Contenido fuera de protocolo"
}
```

## Retencion

| Escenario | Persistencia |
|---|---|
| Piloto con menos de 6 regiones | 6 meses |
| Escala a 6 o mas regiones | 1 mes |

## Frontend consumidor

- `src/services/communityChatService.js`
- `src/features/community/hooks/useCommunityChat.js`
- `src/features/community/chat/CommunityChatPanel.jsx`
- `src/pages/CommunityPage.jsx`
