# Plan de Pruebas — Gestion de Cuenta y Perfil de Usuario

Fecha: 2026-06-05
Sprint: CU12 / CU13 / CU14
Estado: ejecutado — todos los criterios cumplidos

---

## 1. Objetivo

Verificar que los endpoints `GET /api/account/me`, `PATCH /api/account/me` y `POST /api/account/change-password` funcionan correctamente, incluyendo la logica de degradacion de verificacion, el sync automatico de region al chat y la revocacion de tokens.

---

## 2. Alcance

| Modulo | Cubre |
|---|---|
| Backend Spring Boot | AccountController, AccountServiceImpl, migraciones V4/V5, CORS |
| Frontend React/Vite | AccountPage, selects de region/comuna, flujo de contrasena |
| Integracion | Sync regionCode → UserCommunityProfile.primaryRegionId |
| Seguridad | Revocacion de refresh tokens al cambiar contrasena |

---

## 3. Tipos de prueba

| Tipo | Descripcion |
|---|---|
| Prueba funcional | Verificar comportamiento esperado de cada endpoint con datos validos |
| Prueba de validacion | Verificar rechazo de datos invalidos (campos vacios, demasiado largos, contrasena debil) |
| Prueba de regla de negocio | Degradacion de verificacion, sync de region, revocacion de tokens |
| Prueba de seguridad | Acceso sin JWT devuelve 401; usuario no accede datos de otro usuario |
| Prueba de UI | Formulario se renderiza correctamente, selects filtran comunas, redireccion post-contrasena |

---

## 4. Casos de prueba

### CP-01: Obtener perfil propio

| Atributo | Detalle |
|---|---|
| ID | CP-01 |
| Nombre | GET /api/account/me — perfil completo |
| Precondicion | Usuario autenticado con JWT valido |
| Pasos | 1. Enviar GET /api/account/me con Bearer token |
| Resultado esperado | 200 OK con id, email, fullName, phone, regionCode, comunaCode, organizationName, verificationStatus, roles, createdAt |
| Resultado obtenido | ✓ Cumplido |

### CP-02: Actualizar perfil con todos los campos

| Atributo | Detalle |
|---|---|
| ID | CP-02 |
| Nombre | PATCH /api/account/me — actualizar todos los campos |
| Precondicion | Usuario autenticado, sin verificacion alta |
| Pasos | 1. Enviar PATCH con {fullName, phone, regionCode, comunaCode} |
| Resultado esperado | 200 OK con los valores actualizados en la respuesta |
| Resultado obtenido | ✓ Cumplido |

### CP-03: Degradacion de verificacion al cambiar nombre

| Atributo | Detalle |
|---|---|
| ID | CP-03 |
| Nombre | PATCH /api/account/me — usuario FULLY_VERIFIED cambia fullName |
| Precondicion | Usuario con verificationStatus=FULLY_VERIFIED |
| Pasos | 1. Enviar PATCH con nuevo fullName distinto al actual |
| Resultado esperado | 200 OK con verificationStatus=EMAIL_VERIFIED; registro VerificationEvent(IDENTITY_RESET) en BD |
| Resultado obtenido | ✓ Cumplido |

### CP-04: Sin degradacion para verificacion baja

| Atributo | Detalle |
|---|---|
| ID | CP-04 |
| Nombre | PATCH /api/account/me — usuario EMAIL_VERIFIED cambia fullName |
| Precondicion | Usuario con verificationStatus=EMAIL_VERIFIED |
| Pasos | 1. Enviar PATCH con nuevo fullName |
| Resultado esperado | 200 OK con verificationStatus=EMAIL_VERIFIED (sin cambio) |
| Resultado obtenido | ✓ Cumplido |

### CP-05: Sync automatico de region al chat

| Atributo | Detalle |
|---|---|
| ID | CP-05 |
| Nombre | PATCH regionCode → sync UserCommunityProfile |
| Precondicion | Usuario sin community profile previo |
| Pasos | 1. Enviar PATCH con regionCode="biobio" |
| Resultado esperado | UserCommunityProfile.primaryRegionId="biobio" en BD; usuario puede acceder al chat regional |
| Resultado obtenido | ✓ Cumplido |

### CP-06: Cambio de contrasena exitoso

| Atributo | Detalle |
|---|---|
| ID | CP-06 |
| Nombre | POST /api/account/change-password — contrasena valida |
| Precondicion | Usuario autenticado con refresh tokens activos en BD |
| Pasos | 1. Enviar POST con currentPassword correcta, newPassword fuerte, confirmPassword igual |
| Resultado esperado | 200 OK; todos los refresh tokens con revoked_at=NULL quedan con revoked_at=NOW() |
| Resultado obtenido | ✓ Cumplido |

### CP-07: Rechazo de contrasena actual incorrecta

| Atributo | Detalle |
|---|---|
| ID | CP-07 |
| Nombre | POST /api/account/change-password — currentPassword incorrecta |
| Pasos | 1. Enviar POST con currentPassword incorrecta |
| Resultado esperado | 400 "La contrasena actual no es correcta"; passwordHash no cambia |
| Resultado obtenido | ✓ Cumplido |

### CP-08: Rechazo de contrasena igual a la actual

| Atributo | Detalle |
|---|---|
| ID | CP-08 |
| Nombre | POST /api/account/change-password — newPassword == currentPassword |
| Pasos | 1. Enviar POST con newPassword identica a la actual |
| Resultado esperado | 400 "La nueva contrasena debe ser distinta a la actual" |
| Resultado obtenido | ✓ Cumplido |

### CP-09: Rechazo de confirmacion no coincidente

| Atributo | Detalle |
|---|---|
| ID | CP-09 |
| Nombre | POST /api/account/change-password — confirmPassword distinto |
| Pasos | 1. Enviar POST con newPassword != confirmPassword |
| Resultado esperado | 400 "La confirmacion de contrasena no coincide" |
| Resultado obtenido | ✓ Cumplido |

### CP-10: Rechazo de contrasena debil

| Atributo | Detalle |
|---|---|
| ID | CP-10 |
| Nombre | POST /api/account/change-password — newPassword sin simbolo |
| Pasos | 1. Enviar POST con newPassword="SoloLetras123" (sin simbolo) |
| Resultado esperado | 400 con mensaje de politica de contrasena |
| Resultado obtenido | ✓ Cumplido |

### CP-11: Rechazo de PATCH sin JWT

| Atributo | Detalle |
|---|---|
| ID | CP-11 |
| Nombre | PATCH /api/account/me — sin autenticacion |
| Pasos | 1. Enviar PATCH sin header Authorization |
| Resultado esperado | 401 Unauthorized |
| Resultado obtenido | ✓ Cumplido |

### CP-12: CORS preflight OPTIONS

| Atributo | Detalle |
|---|---|
| ID | CP-12 |
| Nombre | OPTIONS /api/account/me — preflight CORS desde Vercel |
| Pasos | 1. Browser envia OPTIONS antes de PATCH desde origen Vercel |
| Resultado esperado | 200 con Access-Control-Allow-Origin y Access-Control-Allow-Methods incluyendo PATCH |
| Resultado obtenido | ✓ Cumplido tras fix SecurityIntegrationConfig + CorsConfig |

### CP-13: Validacion @Size comunaCode

| Atributo | Detalle |
|---|---|
| ID | CP-13 |
| Nombre | PATCH con comunaCode = GADM ID largo (13 chars) |
| Pasos | 1. Enviar PATCH con comunaCode="CHL.13.3.5_1" |
| Resultado esperado | 200 OK (no debe rechazar) |
| Resultado obtenido | ✓ Cumplido tras fix @Size(max=20) y migracion V5 |

### CP-14: Selects de region y comuna en frontend

| Atributo | Detalle |
|---|---|
| ID | CP-14 |
| Nombre | UI — select de region carga 3 opciones; select de comuna filtra por region |
| Pasos | 1. Abrir /account; 2. Verificar select region con 3 opciones; 3. Cambiar region y verificar que comunas se filtran y resetean |
| Resultado esperado | Biobío (37 comunas), Ñuble (21 comunas), Araucanía (33 comunas) — total 91 |
| Resultado obtenido | ✓ Cumplido |

---

## 5. Criterios de aceptacion del plan

- [ ] 14/14 casos de prueba con resultado "Cumplido"
- [ ] No se introducen regresiones en modulo de auth ni chat
- [ ] CORS no bloquea ninguna operacion desde Vercel
- [ ] Datos en BD PostgreSQL persisten correctamente tras PATCH y POST

**Estado final:** 14/14 cumplidos ✓
