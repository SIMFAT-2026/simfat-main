// FASE 0 - PROPUESTA MONGODB RBAC CONTEXTUAL (NO APLICAR AUN)
// Fecha: 2026-05-14
// Estado: borrador

// 1) Indices sugeridos para trazabilidad en reportes ciudadanos
db.citizen_reports.createIndex({ createdByUserId: 1, createdAt: -1 });
db.citizen_reports.createIndex({ status: 1, updatedAt: -1 });

// 2) Campos sugeridos en citizen_reports (snapshot contextual de seguridad)
// {
//   createdByUserId: "uuid",
//   createdByRoleSnapshot: ["ROLE_VERIFIED_USER"],
//   moderation: {
//     moderatedByUserId: "uuid",
//     moderatedByRoleSnapshot: ["ROLE_MODERATOR"],
//     moderatedAt: ISODate("...")
//   }
// }

// 3) Coleccion opcional de auditoria operacional
db.createCollection("audit_events");
db.audit_events.createIndex({ actorUserId: 1, createdAt: -1 });
db.audit_events.createIndex({ eventType: 1, createdAt: -1 });

// Documento sugerido de audit_events:
// {
//   _id: ObjectId(),
//   actorUserId: "uuid",
//   actorRoleSnapshot: ["ROLE_ADMIN"],
//   eventType: "REPORT_STATUS_CHANGED",
//   targetType: "citizen_report",
//   targetId: "report-id",
//   payload: { oldStatus: "RECIBIDO", newStatus: "VALIDADO" },
//   createdAt: ISODate("...")
// }
