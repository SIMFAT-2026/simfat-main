# Gestion de Cuenta y Perfil de Usuario - SDD v1

Fecha: 2026-06-02
Cambio SDD: `gestion-cuenta-perfil-v1`
Modulo propietario: Cuenta / Identidad
Estado: especificacion aprobada — pendiente de implementacion

Spec base: `2026-05-27_spec_gestion_cuenta_perfil_v1.md`

---

## 1. Objetivo

Cerrar los casos de uso CU12 (editar perfil), CU13 (cambiar contrasena autenticado) y CU14 (actualizar datos personales), completando la identidad de usuario que el chat comunitario ya consume y que AIFBN necesita para validar el origen territorial de los reportes ciudadanos.

Usuarios afectados: cualquier usuario autenticado para perfil propio; ROLE_ADMIN y ROLE_SUPER_ADMIN para vista ampliada en panel de administracion.

---

## 2. Alcance MVP

### Entra en este sprint

| Area | Alcance |
|---|---|
| GET /api/account/me | Devolver perfil propio completo incluidos campos nuevos |
| PATCH /api/account/me | Editar campos permitidos; degradar verificacion si fullName cambia en usuario verificado |
| POST /api/account/change-password | Cambio autenticado con contrasena actual; revocar todos los refresh tokens activos |
| Migracion BD | Agregar phone, region_code, comuna_code a app_users |
| Admin: extension AccessUserDTO | phone, regionCode, comunaCode, organizationName visibles para admins |
| Frontend: vista de perfil | Formulario de edicion + formulario de contrasena integrados en /account |

### Queda diferido (deuda documentada)

| Item | Motivo |
|---|---|
| Cambio de correo electronico | Requiere flujo de verificacion por email — spec separada |
| Verificacion de telefono | CU14 extended — sin fecha definida |
| Carga de foto de perfil | Depende de storage Supabase — spec separada |
| Aprobacion manual de cambio de nombre | Sustituido por auto-degradacion (ver seccion 3) |
| Historial de cambios de perfil | Util post-defensa; auditoria via VerificationEvent es suficiente por ahora |

---

## 3. Decisiones de diseno cerradas

Estas decisiones fueron acordadas explicitamente antes de escribir este SDD.

### 3.1 Cambio de nombre en usuario verificado

Si `UserVerification.status ∈ {IDENTITY_VERIFIED, FULLY_VERIFIED}` y el usuario cambia `fullName`:

- `status` se degrada automaticamente a `EMAIL_VERIFIED`
- Se inserta un `VerificationEvent(type=IDENTITY_RESET, reason=NAME_CHANGE_BY_USER, triggeredBy=userId)`
- El admin puede restaurar el estado via CU15 cuando valide el cambio

Razon: proporcionalidad — el email sigue validado, solo la identidad personal cambia. No se introduce un estado PENDING_REVIEW nuevo para no ampliar el alcance.

### 3.2 Campos de datos personales

Tres campos nuevos en `app_users` (PostgreSQL):

| Campo | Tipo BD | Reglas |
|---|---|---|
| `phone` | VARCHAR(20), nullable | Opcional; sin validacion de formato en esta fase |
| `region_code` | VARCHAR(10), nullable | Codigo de region GADM ("08" Biobio, "09" Araucania) |
| `comuna_code` | VARCHAR(10), nullable | Codigo de comuna GADM — coherente con el choropleth existente |

`organizationName` permanece en `user_verification` — es parte del flujo de verificacion, no del perfil editable.

### 3.3 Politica de sesiones post cambio de contrasena

Al cambiar contrasena: revocar **todos** los refresh tokens activos del usuario via el metodo existente `revokeAllUserRefreshTokens(userId)` en `AuthServiceImpl`. No se emite nuevo token — el access token actual permanece valido por su TTL restante; el cliente hace login normal al expirar.

### 3.4 Visibilidad de datos para admins

`GET /api/admin/users/{id}` expande `AccessUserDTO` con `phone`, `regionCode`, `comunaCode`, `organizationName`. El admin **no recibe** passwordHash ni timestamps de tokens. El endpoint propio del usuario (`/api/account/me`) no expone datos de otros usuarios.

---

## 4. Modelo de datos — cambios

### 4.1 Migracion PostgreSQL (Flyway o DDL directo)

```sql
ALTER TABLE app_users
    ADD COLUMN phone       VARCHAR(20),
    ADD COLUMN region_code VARCHAR(10),
    ADD COLUMN comuna_code VARCHAR(10);
```

Columnas nullable sin valor por defecto — compatibles con usuarios existentes sin necesidad de backfill.

### 4.2 Entidad AppUser — campos nuevos

```
phone       : String  (nullable)
regionCode  : String  (nullable)
comunaCode  : String  (nullable)
```

Los tres con `@Column(nullable = true)` en la entidad JPA. Sin validaciones de formato en la capa de persistencia — la validacion de regionCode/comunaCode se hace en el servicio comparando contra la lista de codigos GADM conocidos (Biobio + Araucania).

### 4.3 VerificationEvent (sin cambio de schema)

`VerificationEvent` ya existe. Se usa el tipo `IDENTITY_RESET` o se agrega si no existe. Verificar el enum `VerificationEventType` antes de implementar.

### 4.4 AccessUserDTO — extension admin

```java
public record AccessUserDTO(
    String id,
    String email,
    String fullName,
    boolean enabled,
    Set<String> legacyRoles,
    Set<String> assignedRoles,
    Set<String> effectiveRoles,
    String verificationStatus,
    CommunityChatAccessDTO communityChatAccess,
    // campos nuevos:
    String phone,
    String regionCode,
    String comunaCode,
    String organizationName
) {}
```

`organizationName` se lee desde `UserVerification.organizationName` al construir el DTO en `AccessAdminServiceImpl`.

---

## 5. Contratos API

### 5.1 GET /api/account/me

Requiere: JWT valido (cualquier rol autenticado).

Respuesta 200:

```json
{
  "success": true,
  "message": "Perfil obtenido correctamente",
  "data": {
    "id": "uuid",
    "email": "usuario@example.com",
    "fullName": "Maria Paz Lopez",
    "phone": "+56912345678",
    "regionCode": "08",
    "comunaCode": "08301",
    "organizationName": "Brigada Biobio Norte",
    "verificationStatus": "FULLY_VERIFIED",
    "roles": ["ROLE_COMMUNITY_USER", "ROLE_VERIFIED_USER"],
    "createdAt": "2026-01-15T10:00:00Z"
  }
}
```

`organizationName` es null si el usuario no tiene `UserVerification`. `phone`, `regionCode`, `comunaCode` son null si no se han completado.

### 5.2 PATCH /api/account/me

Requiere: JWT valido.

Payload (todos los campos opcionales — solo se actualizan los presentes):

```json
{
  "fullName": "Maria Paz Lopez Soto",
  "phone": "+56912345678",
  "regionCode": "08",
  "comunaCode": "08301"
}
```

Respuesta 200:

```json
{
  "success": true,
  "message": "Perfil actualizado correctamente",
  "data": {
    "id": "uuid",
    "email": "usuario@example.com",
    "fullName": "Maria Paz Lopez Soto",
    "phone": "+56912345678",
    "regionCode": "08",
    "comunaCode": "08301",
    "organizationName": "Brigada Biobio Norte",
    "verificationStatus": "EMAIL_VERIFIED",
    "roles": ["ROLE_COMMUNITY_USER", "ROLE_VERIFIED_USER"],
    "createdAt": "2026-01-15T10:00:00Z"
  }
}
```

Nota: si `fullName` cambio y el usuario tenia `IDENTITY_VERIFIED` o `FULLY_VERIFIED`, la respuesta ya refleja `verificationStatus: "EMAIL_VERIFIED"`.

Errores:

| Caso | HTTP | Mensaje |
|---|---|---|
| fullName en blanco | 400 | "El nombre no puede estar en blanco" |
| fullName > 120 chars | 400 | "El nombre no puede exceder 120 caracteres" |
| regionCode invalido | 400 | "Codigo de region no reconocido" |
| comunaCode invalido | 400 | "Codigo de comuna no reconocido" |

### 5.3 POST /api/account/change-password

Requiere: JWT valido.

Payload:

```json
{
  "currentPassword": "Contrasena$Actual1",
  "newPassword": "NuevaContrasena$2",
  "confirmPassword": "NuevaContrasena$2"
}
```

Respuesta 200:

```json
{
  "success": true,
  "message": "Contrasena actualizada. Por seguridad se cerraron todas las sesiones activas.",
  "data": null
}
```

Errores:

| Caso | HTTP | Mensaje |
|---|---|---|
| currentPassword incorrecta | 400 | "La contrasena actual no es correcta" |
| newPassword == currentPassword | 400 | "La nueva contrasena debe ser distinta a la actual" |
| newPassword != confirmPassword | 400 | "La confirmacion de contrasena no coincide" |
| newPassword no cumple politica | 400 | "La contrasena debe tener 12-72 caracteres, mayuscula, minuscula, numero y simbolo" |

La politica de contrasena es la misma regex que `RegisterRequestDTO`: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{12,72}$`.

**Efecto colateral documentado**: se invoca `revokeAllUserRefreshTokens(userId)` — todos los refresh tokens con `revokedAt IS NULL` quedan con `revokedAt = NOW()`.

---

## 6. Reglas de negocio por endpoint

### 6.1 PATCH /api/account/me — logica de degradacion

```
si request.fullName != null y request.fullName != user.fullName:
    user.fullName = request.fullName
    si verification.status == IDENTITY_VERIFIED o FULLY_VERIFIED:
        verification.status = EMAIL_VERIFIED
        insertar VerificationEvent(
            userId, type=IDENTITY_RESET, reason=NAME_CHANGE_BY_USER,
            triggeredBy=userId, timestamp=now()
        )
si request.phone != null: user.phone = request.phone (vaciar con "" acepta null)
si request.regionCode != null: validar contra lista; user.regionCode = request.regionCode
si request.comunaCode != null: validar contra lista; user.comunaCode = request.comunaCode
```

La lista de codigos validos es la misma fuente que el choropleth: codigos GADM de Biobio y Araucania. Para esta fase se acepta cualquier codigo de 2 a 10 caracteres para no bloquear usuarios de otras regiones en el futuro.

### 6.2 POST /api/account/change-password — logica completa

```
1. resolver userId desde SecurityContext
2. cargar AppUser; si no existe → 404 (no deberia ocurrir con JWT valido)
3. BCrypt.matches(currentPassword, user.passwordHash) → si false → 400
4. si newPassword == currentPassword → 400
5. si newPassword != confirmPassword → 400
6. validar regex politica sobre newPassword → si falla → 400
7. user.passwordHash = BCrypt.encode(newPassword)
8. appUserRepository.save(user)
9. revokeAllUserRefreshTokens(userId)   // metodo existente en AuthServiceImpl
10. retornar 200
```

---

## 7. Iteraciones planificadas

### Iteracion 1 — Backend: migracion + AccountController

- Migracion SQL: `ALTER TABLE app_users ADD COLUMN phone/region_code/comuna_code`
- Extension entidad `AppUser` con los tres campos nuevos
- Nuevo `AccountController` en `/api/account` con los tres endpoints
- `AccountService` + `AccountServiceImpl` con logica de degradacion y cambio de contrasena
- Extension `AccessUserDTO` + `AccessAdminServiceImpl` para incluir campos nuevos

### Iteracion 2 — Frontend: vista /account

- Ruta `/account` protegida (cualquier usuario autenticado)
- Formulario de datos personales: fullName, phone, regionCode, comunaCode con selects o texto libre
- Formulario de contrasena: currentPassword, newPassword, confirmPassword con show/hide
- Feedback visual: confirmacion de guardado, mensaje de degradacion de verificacion si aplica
- Cierre de sesion automatico post cambio de contrasena (el frontend detecta 401 en el siguiente refresh y redirige a login)

---

## 8. Criterios de aceptacion

1. Usuario autenticado llama `GET /api/account/me` y recibe perfil completo con todos los campos nuevos (null si no configurados).
2. Usuario actualiza `fullName`, `phone`, `regionCode`, `comunaCode` via `PATCH /api/account/me` y la respuesta refleja los valores nuevos.
3. Usuario con `verificationStatus=FULLY_VERIFIED` cambia su `fullName` → la respuesta devuelve `verificationStatus=EMAIL_VERIFIED` y se crea un `VerificationEvent` de tipo `IDENTITY_RESET`.
4. Usuario con `verificationStatus=EMAIL_VERIFIED` o inferior cambia su nombre → `verificationStatus` no cambia.
5. `POST /api/account/change-password` con `currentPassword` incorrecta devuelve 400 sin modificar credenciales.
6. `POST /api/account/change-password` con `newPassword == currentPassword` devuelve 400.
7. `POST /api/account/change-password` con `newPassword != confirmPassword` devuelve 400.
8. `POST /api/account/change-password` con contrasena debil devuelve 400 con el mensaje de politica.
9. Cambio de contrasena exitoso → todos los refresh tokens activos del usuario quedan con `revokedAt` no nulo en BD.
10. Admin llama `GET /api/admin/users/{id}` y recibe `phone`, `regionCode`, `comunaCode`, `organizationName` en la respuesta.
11. El formulario frontend muestra aviso cuando el cambio de nombre baja el estado de verificacion.
12. El frontend redirige a login si el access token expira despues del cambio de contrasena (comportamiento natural por TTL — no requiere implementacion especial).

---

## 9. Deuda tecnica documentada

| Item | Severidad | Descripcion |
|---|---|---|
| Cambio de correo | Media | Requiere flujo de verificacion por email con token de confirmacion — spec separada |
| Verificacion de telefono | Baja | Actualmente phone es texto libre; verificacion OTP queda para post-defensa |
| Validacion estricta region/comuna | Baja | Actualmente acepta cualquier codigo corto; idealmente validar contra lista GADM en memoria |
| Foto de perfil | Baja | Requiere integracion Supabase Storage — spec separada |
| Workflow de re-verificacion de identidad | Media | Cuando admin restaura IDENTITY_VERIFIED post NAME_CHANGE, deberia existir formulario dedicado en CU15 |
| Notificacion por email post cambio de contrasena | Baja | Util para UX de seguridad; sin fecha definida |
