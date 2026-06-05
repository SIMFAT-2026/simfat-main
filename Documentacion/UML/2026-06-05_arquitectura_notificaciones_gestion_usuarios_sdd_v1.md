# Arquitectura UML — Notificaciones In-App y Gestion de Usuarios

Fecha: 2026-06-05
Sprint: CU09 / CU15 brecha

---

## 1. Diagrama de componentes — CU09 Notificaciones

```
┌──────────────────────────────────────────────────────────────────────┐
│ Frontend (React/Vite — Vercel)                                       │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ Navbar.jsx                                                  │    │
│  │   └── NotificationBell.jsx                                  │    │
│  │         ├── polling cada 30s → GET /api/notifications/unread│    │
│  │         ├── badge numerico (unreadCount)                    │    │
│  │         └── dropdown: clic → PATCH ./{id}/read             │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  notificationsService.js                                            │
│    ├── getUnreadNotifications()                                      │
│    └── markNotificationRead(id)                                      │
└──────────────────────────────────────────────────────────────────────┘
                              │ HTTPS / JWT
┌──────────────────────────────────────────────────────────────────────┐
│ Backend (Spring Boot — Railway)                                      │
│                                                                      │
│  NotificationController                                              │
│    ├── GET  /api/notifications/unread → NotificationService          │
│    └── PATCH /api/notifications/{id}/read → NotificationService      │
│                                                                      │
│  NotificationServiceImpl                                             │
│    ├── getUnread(userId) → NotificationRepository                    │
│    ├── markRead(id, userId) → NotificationRepository                 │
│    └── triggerComunaRiskAlert(snapshot, previousLevel)               │
│          ├── verifica alertLevel ALTO/CRITICO                        │
│          ├── verifica escalada vs nivel previo (deduplicacion)       │
│          ├── consulta AlertRuleRepository (MongoDB)                  │
│          └── consulta AppUserRepository.findByComunaCode()           │
│                → crea Notification por cada usuario elegible         │
│                                                                      │
│  ComunaRiskServiceImpl.recomputeByComuna()                           │
│    ├── [calculo WLC existente]                                       │
│    ├── previousLevel = snapshotRepository.findTop...()               │
│    ├── snapshotRepository.save(snapshot)  ← MongoDB                 │
│    └── notificationService.triggerComunaRiskAlert(...)               │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
         │ JPA                              │ MongoRepository
┌────────▼──────┐                 ┌─────────▼────────────────┐
│ PostgreSQL    │                 │ MongoDB Atlas            │
│ notifications │                 │ alert_rules              │
│ app_users     │                 │ comuna_risk_snapshots    │
└───────────────┘                 └──────────────────────────┘
```

---

## 2. Diagrama de secuencia — Trigger de notificacion

```
Scheduler (1 AM) → ComunaRiskServiceImpl.recomputeAllComunas()
  │
  ├─ para cada ComunaInfo:
  │   ├─ fwiService.syncFwiByRegion(...)
  │   └─ recomputeByComuna(comunaId)
  │         │
  │         ├─ [calcula score WLC]
  │         ├─ previousLevel ← snapshotRepo.findTop...()
  │         ├─ snapshot = new ComunaRiskSnapshot(...)
  │         ├─ snapshotRepo.save(snapshot)
  │         └─ notificationService.triggerComunaRiskAlert(snapshot, previousLevel)
  │                 │
  │                 ├─ [check: alertLevel ALTO o CRITICO?] → no → return
  │                 ├─ [check: newLevel > previousLevel?] → no → return
  │                 ├─ alertRuleRepo.findByRegionIdAndActivaTrue(regionId)
  │                 │     → [check: lista vacia?] → si → return
  │                 ├─ appUserRepo.findByComunaCode(comunaId)
  │                 │     → [check: lista vacia?] → si → return
  │                 └─ notificationRepo.saveAll([Notification x usuario])
```

---

## 3. Diagrama de componentes — CU15 Verificacion

```
┌──────────────────────────────────────────────────────────────────────┐
│ Frontend (React/Vite — Vercel)                                       │
│                                                                      │
│  AccessControlPage.jsx                                               │
│    ├── [seccion existente: roles y acceso de chat]                   │
│    └── [NUEVA] seccion "Verificaciones pendientes"                   │
│          ├── carga getPendingReview() al montar                      │
│          ├── card por usuario pendiente:                             │
│          │     ├── nombre, email, estado actual                      │
│          │     ├── <details> → getVerificationEvents(userId)         │
│          │     │     tabla historial de eventos                      │
│          │     └── formulario: select estado + textarea notas        │
│          │           → updateVerificationStatus(userId, draft)       │
│          └── al guardar: elimina usuario de lista local              │
└──────────────────────────────────────────────────────────────────────┘
                              │ HTTPS / JWT (ROLE_ADMIN)
┌──────────────────────────────────────────────────────────────────────┐
│ Backend (Spring Boot — Railway)                                      │
│                                                                      │
│  AccessAdminController                                               │
│    ├── GET  /api/admin/access/users/{id}/verification-events         │
│    ├── PUT  /api/admin/access/users/{id}/verification                │
│    └── GET  /api/admin/access/users/pending-review                   │
│                                                                      │
│  AccessAdminServiceImpl                                              │
│    ├── getVerificationEvents(userId)                                 │
│    │     → VerificationEventRepository.findByUserId...Desc()         │
│    ├── updateVerificationStatus(userId, request, actorId)            │
│    │     ├── valida VerificationStatus enum                          │
│    │     ├── userVerificationRepository.save()                       │
│    │     └── verificationEventRepository.save(ADMIN_STATUS_CHANGE)   │
│    └── getPendingReview()                                            │
│          → VerificationEventRepository.findPendingIdentityResets()   │
│               (query nativa: ultimo evento es IDENTITY_RESET)        │
└──────────────────────────────────────────────────────────────────────┘
                              │ JPA
┌─────────────────────────────▼────────────────┐
│ PostgreSQL (Supabase)                        │
│ user_verification · verification_events      │
│ app_users                                    │
└──────────────────────────────────────────────┘
```

---

## 4. Diagrama de secuencia — Admin restaura verificacion

```
Admin → AccessControlPage → [clic "Guardar estado"]
  │
  └─ updateVerificationStatus(userId, { newStatus: "IDENTITY_VERIFIED", notes: "..." })
        │
        └─ PUT /api/admin/access/users/{id}/verification
              │
              ├─ valida @NotBlank newStatus, notes
              ├─ VerificationStatus.valueOf(newStatus) → validacion enum
              ├─ userVerification.setStatus(IDENTITY_VERIFIED)
              ├─ userVerificationRepo.save()
              ├─ new VerificationEvent(ADMIN_STATUS_CHANGE, oldStatus, IDENTITY_VERIFIED, actorId, notes)
              └─ verificationEventRepo.save()
                    │
                    └─ return AccessUserDTO (con verificationStatus actualizado)
                          → frontend elimina usuario de lista pendientes
```
