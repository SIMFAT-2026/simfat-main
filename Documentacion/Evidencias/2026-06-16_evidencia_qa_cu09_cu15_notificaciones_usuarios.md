# Evidencia QA - CU09 (Notificaciones) y CU15 (Gestionar usuarios) en produccion

- Fecha: 2026-06-16
- Objetivo: ejecutar en produccion el QA que el checklist `2026-06-15_checklist_qa_estado_actual_cu01_cu15.md` dejaba pendiente para CU09 y CU15.

## Resultados

| Item | Endpoint | Esperado | Resultado | Status |
|---|---|---|---|---|
| item1 | `GET /api/notifications/unread` (sin auth) | 403 | 403 Forbidden | OK |
| item2 | `GET /api/notifications/unread` (autenticado) | 200, lista vacia si no hay notificaciones | 200, `notifications: []`, `unreadCount: 0` | OK |
| item4 | `PATCH /api/notifications/nonexistent-id-000/read` (autenticado) | 404 | 404 Not Found, "Notificacion no encontrada" | OK |
| item5 | `PATCH /api/notifications/nonexistent-id-000/read` (sin auth) | 403 | 403 Forbidden | OK |
| item6 | (caso adicional de notificaciones) | - | SKIP - no hay datos de prueba disponibles | SKIP |
| item14 | `GET /api/admin/access/users/pending-review` | 200, lista de usuarios pendientes de revision | 200, `data: []` (sin usuarios pendientes en este momento) | OK |
| users | `GET /api/admin/access/users?page=0&size=5` | 200, listado paginado con roles efectivos | 200, devuelve usuarios reales (admins, AIFBN, cuentas de prueba QA) con `assignedRoles`/`effectiveRoles` correctos | OK |
| sync_check | `POST /api/territory/sync` | 200/202 | **500 Internal Server Error** ("Ocurrio un error inesperado en SIMFAT") | **FALLA** |

## Hallazgo a investigar

`POST /api/territory/sync` devolvio 500 durante esta corrida. No es parte de CU09/CU15 pero quedo registrado en la misma ejecucion — pendiente investigar causa raiz antes de cerrar evidencia de sincronizacion territorial (relacionado con CU11, que ya tenia una brecha abierta de NDVI/NDMI/LOSS en 0).

## Conclusion

CU09 y CU15: endpoints de notificaciones y gestion de usuarios responden correctamente en produccion (autenticacion, 404 sobre recursos inexistentes, paginacion y roles efectivos). Ambos casos de uso quedan en condiciones de cerrarse con esta evidencia, sujeto a actualizar el checklist `2026-06-15_checklist_qa_estado_actual_cu01_cu15_v2.md`.
