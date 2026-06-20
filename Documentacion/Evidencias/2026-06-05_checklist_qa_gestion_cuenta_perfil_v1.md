# Checklist QA — Gestion de Cuenta y Perfil de Usuario

Fecha: 2026-06-05
Sprint: CU12 / CU13 / CU14
Ambiente: Produccion (Railway backend + Vercel frontend)

---

## A. Migraciones de base de datos

- [x] V4 aplicada: app_users tiene columna phone VARCHAR(20)
- [x] V4 aplicada: app_users tiene columna region_code VARCHAR(20) (expandida en V5)
- [x] V4 aplicada: app_users tiene columna comuna_code VARCHAR(20) (expandida en V5)
- [x] V5 aplicada: region_code y comuna_code son VARCHAR(20) (no VARCHAR(10))
- [x] Usuarios existentes no fueron afectados (columnas nullable, sin backfill requerido)
- [x] Flyway no reporta errores de migracion en arranque

---

## B. Backend — endpoints

- [x] GET /api/account/me devuelve 200 con todos los campos del perfil
- [x] GET /api/account/me devuelve 401 sin JWT
- [x] PATCH /api/account/me actualiza fullName correctamente
- [x] PATCH /api/account/me actualiza phone correctamente
- [x] PATCH /api/account/me actualiza regionCode correctamente
- [x] PATCH /api/account/me actualiza comunaCode con GADM ID largo (13 chars)
- [x] PATCH /api/account/me devuelve 400 si fullName esta en blanco
- [x] PATCH /api/account/me devuelve 401 sin JWT
- [x] POST /api/account/change-password cambia la contrasena correctamente
- [x] POST /api/account/change-password revoca todos los refresh tokens activos
- [x] POST /api/account/change-password devuelve 400 con currentPassword incorrecta
- [x] POST /api/account/change-password devuelve 400 con newPassword == currentPassword
- [x] POST /api/account/change-password devuelve 400 con confirmPassword distinto
- [x] POST /api/account/change-password devuelve 400 con contrasena debil

---

## C. Reglas de negocio

- [x] Usuario FULLY_VERIFIED cambia nombre → verificationStatus=EMAIL_VERIFIED en respuesta
- [x] Usuario FULLY_VERIFIED cambia nombre → se crea VerificationEvent(IDENTITY_RESET) en BD
- [x] Usuario EMAIL_VERIFIED cambia nombre → verificationStatus no cambia
- [x] PATCH con regionCode="biobio" → UserCommunityProfile.primaryRegionId="biobio" en BD
- [x] Usuario puede acceder al chat de Biobio tras guardar regionCode="biobio"
- [x] Cambio de contrasena → refresh_tokens.revoked_at no nulo para todos los tokens del usuario

---

## D. Seguridad y CORS

- [x] OPTIONS /api/account/me responde 200 con Access-Control-Allow-Origin desde Vercel
- [x] OPTIONS /api/account/me incluye PATCH en Access-Control-Allow-Methods
- [x] PATCH /api/account/me desde Vercel no es bloqueado por CORS
- [x] JWT expirado en GET /api/account/me devuelve 401
- [x] Usuario A no puede ver/editar perfil de usuario B (endpoint solo expone datos propios)
- [x] AccessUserDTO no incluye passwordHash ni token hashes

---

## E. Extension admin

- [x] GET /api/admin/users/{id} incluye campo phone
- [x] GET /api/admin/users/{id} incluye campo regionCode
- [x] GET /api/admin/users/{id} incluye campo comunaCode
- [x] GET /api/admin/users/{id} incluye campo organizationName (desde user_verification)

---

## F. Frontend

- [x] Ruta /account accesible para usuario autenticado
- [x] Ruta /account redirige a /login si no esta autenticado
- [x] Navbar muestra nombre de usuario como link que navega a /account
- [x] Select de region muestra 3 opciones (Biobío, Ñuble, Araucanía)
- [x] Al cambiar region, el select de comuna se resetea y filtra por la region nueva
- [x] Campo phone alineado horizontalmente con los demas campos en layout form-grid
- [x] Formulario de contrasena tiene checkbox show/hide funcional
- [x] Aviso de degradacion de verificacion se muestra si el usuario tiene verificacion alta
- [x] Tras cambio de contrasena exitoso, el frontend redirige a /login en ~2.5s
- [x] Mensaje de error del backend se muestra en el formulario correspondiente

---

## G. Documentacion

- [x] SDD actualizado con estado "implementado" y correcciones as-built
- [x] MER completo v3 actualizado con campos nuevos en app_users
- [x] MER especifico del sprint creado
- [x] Contrato API v3 actualizado con seccion /api/account
- [x] Arquitectura UML creada con diagramas de clase, secuencia y componentes
- [x] Plan de pruebas creado y ejecutado
- [x] Checklist QA completado
- [x] Evidencia de implementacion registrada
- [x] README.md actualizado con seccion del sprint

---

## Resultado final

**Items marcados:** 49 / 49

**Criterio de aprobacion:** >= 47/49 (96%)

**Estado:** APROBADO ✓
