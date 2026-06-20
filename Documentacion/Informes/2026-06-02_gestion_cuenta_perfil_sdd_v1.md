# Gestion de Cuenta y Perfil de Usuario - SDD v1

Fecha: 2026-06-02
Cambio SDD: `gestion-cuenta-perfil-v1`
Modulo propietario: Cuenta / Identidad
Estado: **implementado y verificado en produccion — 2026-06-05**

Spec base: `2026-05-27_spec_gestion_cuenta_perfil_v1.md`

---

## 1. Objetivo

Cerrar los casos de uso CU12 (editar perfil), CU13 (cambiar contrasena autenticado) y CU14 (actualizar datos personales), completando la identidad de usuario que el chat comunitario ya consume y que AIFBN necesita para validar el origen territorial de los reportes ciudadanos.

Usuarios afectados: cualquier usuario autenticado para perfil propio; ROLE_ADMIN y ROLE_SUPER_ADMIN para vista ampliada en panel de administracion.

---

## 2. Alcance MVP

### Entra en este sprint

| Area | Alcance | Estado |
|---|---|---|
| GET /api/account/me | Devolver perfil propio completo incluidos campos nuevos | ✓ implementado |
| PATCH /api/account/me | Editar campos permitidos; degradar verificacion si fullName cambia en usuario verificado | ✓ implementado |
| POST /api/account/change-password | Cambio autenticado con contrasena actual; revocar todos los refresh tokens activos | ✓ implementado |
| Migracion BD V4 | Agregar phone, region_code, comuna_code a app_users (VARCHAR iniciales) | ✓ implementado |
| Migracion BD V5 | Expandir region_code y comuna_code a VARCHAR(20) para IDs GADM largos | ✓ implementado |
| Admin: extension AccessUserDTO | phone, regionCode, comunaCode, organizationName visibles para admins | ✓ implementado |
| Frontend: vista de perfil | Formulario de edicion con selects de region/comuna + formulario de contrasena en /account | ✓ implementado |
| Sync region → chat | Al guardar regionCode, auto-sync a UserCommunityProfile.primaryRegionId para acceso al chat | ✓ implementado |

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

### 3.1 Cambio de nombre en usuario verificado

Si `UserVerification.status ∈ {IDENTITY_VERIFIED, FULLY_VERIFIED}` y el usuario cambia `fullName`:

- `status` se degrada automaticamente a `EMAIL_VERIFIED`
- Se inserta un `VerificationEvent(type=IDENTITY_RESET, reviewedBy=userId, notes="Nombre cambiado por el propio usuario")`
- El admin puede restaurar el estado via CU15 cuando valide el cambio

**Implementacion real:** `VerificationEvent.eventType` es un campo String (no enum), por lo que se persiste directamente la cadena `"IDENTITY_RESET"`. No se requirio crear un enum nuevo.

Razon: proporcionalidad — el email sigue validado, solo la identidad personal cambia. No se introduce un estado PENDING_REVIEW nuevo para no ampliar el alcance.

### 3.2 Campos de datos personales

Tres campos nuevos en `app_users` (PostgreSQL):

| Campo | Tipo BD final | Reglas |
|---|---|---|
| `phone` | VARCHAR(20), nullable | Opcional; sin validacion de formato en esta fase |
| `region_code` | VARCHAR(20), nullable | ID de region del mapa: "biobio", "araucania", "nuble" |
| `comuna_code` | VARCHAR(20), nullable | GADM GID Level 3 — ej. "CHL.6.3.2_1" (hasta 13 chars) |

**Nota as-built:** la especificacion inicial definia VARCHAR(10). Tras implementar los selects con datos GADM reales se detecto que los IDs como "CHL.13.3.5_1" tienen 12-13 caracteres. Se agrego la migracion V5 para expandir a VARCHAR(20) y se corrigio el constraint `@Size(max=20)` en el DTO.

`organizationName` permanece en `user_verification` — es parte del flujo de verificacion, no del perfil editable.

### 3.3 Politica de sesiones post cambio de contrasena

Al cambiar contrasena: revocar **todos** los refresh tokens activos del usuario via `revokeAllUserRefreshTokens(userId)` en `AuthServiceImpl`. El metodo existia como privado; se expuso via interfaz `AuthService.revokeAllTokens(userId)` que delega al metodo privado.

El frontend redirige al usuario a `/login` automaticamente 2.5 segundos despues del cambio exitoso (no espera expiracion natural del access token).

### 3.4 Visibilidad de datos para admins

`GET /api/admin/users/{id}` expande `AccessUserDTO` con `phone`, `regionCode`, `comunaCode`, `organizationName`. El admin **no recibe** passwordHash ni timestamps de tokens.

### 3.5 Region/comuna: datos estaticos locales en frontend

En lugar de llamar a una API externa, el frontend usa el archivo `src/data/territorioChile.js` que contiene 86 comunas extraidas de los GeoJSON GADM del classpath del backend. Las regiones cubiertas en el piloto son:

| regionCode | Label visible |
|---|---|
| `"biobio"` | Biobío |
| `"nuble"` | Ñuble |
| `"araucania"` | Araucanía |

Los valores de `regionCode` coinciden exactamente con los `regionId` de las salas de chat, lo que habilita el auto-sync documentado en 3.6.

### 3.6 Sync automatico region → chat comunitario

Al persistir un cambio de `regionCode` en `PATCH /api/account/me`, el servicio hace upsert de `UserCommunityProfile.primaryRegionId` con el mismo valor. Esto otorga acceso automatico a la sala de chat de la region seleccionada.

El admin conserva la capacidad de revocar el acceso al chat desde el panel de administracion, independientemente del valor guardado en el perfil.

---

## 4. Modelo de datos — cambios

### 4.1 Migraciones PostgreSQL (Flyway)

**V4__add_user_profile_fields.sql** — agrega columnas iniciales:
```sql
ALTER TABLE app_users
    ADD COLUMN phone       VARCHAR(20),
    ADD COLUMN region_code VARCHAR(10),
    ADD COLUMN comuna_code VARCHAR(10);
```

**V5__expand_user_profile_codes.sql** — expande a VARCHAR(20) para IDs GADM:
```sql
ALTER TABLE app_users
    ALTER COLUMN region_code TYPE VARCHAR(20),
    ALTER COLUMN comuna_code TYPE VARCHAR(20);
```

Columnas nullable sin valor por defecto — compatibles con usuarios existentes sin necesidad de backfill.

### 4.2 Entidad AppUser — campos nuevos

```java
@Column(name = "phone", length = 20)
private String phone;

@Column(name = "region_code", length = 20)
private String regionCode;

@Column(name = "comuna_code", length = 20)
private String comunaCode;
```

Los tres con `@Column(nullable = true)` implicito.

### 4.3 DTOs nuevos

**AccountProfileDTO** (respuesta de GET y PATCH):
```java
public record AccountProfileDTO(
    String id, String email, String fullName, String phone,
    String regionCode, String comunaCode, String organizationName,
    String verificationStatus, Set<String> roles, Instant createdAt
) {}
```

**UpdateProfileRequestDTO** (body de PATCH):
```java
public record UpdateProfileRequestDTO(
    @Size(min = 1, max = 120) String fullName,
    @Size(max = 20)           String phone,
    @Size(max = 20)           String regionCode,
    @Size(max = 20)           String comunaCode
) {}
```

**ChangePasswordRequestDTO** (body de POST /change-password):
```java
public record ChangePasswordRequestDTO(
    @NotBlank String currentPassword,
    @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{12,72}$")
    String newPassword,
    @NotBlank String confirmPassword
) {}
```

### 4.4 VerificationEvent (sin cambio de schema)

`VerificationEvent.eventType` es String. Se persiste el literal `"IDENTITY_RESET"` al degradar identidad por cambio de nombre.

### 4.5 AccessUserDTO — extension admin

```java
public record AccessUserDTO(
    String id, String email, String fullName,
    boolean enabled,
    Set<String> legacyRoles, Set<String> assignedRoles, Set<String> effectiveRoles,
    String verificationStatus,
    CommunityChatAccessDTO communityChatAccess,
    // campos nuevos en este sprint:
    String phone,
    String regionCode,
    String comunaCode,
    String organizationName
) {}
```

`organizationName` se lee desde `UserVerification.organizationName` en `AccessAdminServiceImpl`.

### 4.6 UserCommunityProfile — sin cambio de schema, logica nueva

No se modifico el schema. El campo `primary_region_id` existia. Lo nuevo es que ahora se hace **upsert automatico** desde `AccountServiceImpl.updateProfile()` cuando cambia `regionCode`.

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
    "regionCode": "biobio",
    "comunaCode": "CHL.6.3.2_1",
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
  "regionCode": "biobio",
  "comunaCode": "CHL.6.3.2_1"
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
    "regionCode": "biobio",
    "comunaCode": "CHL.6.3.2_1",
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
| fullName en blanco (string vacio) | 400 | "El nombre no puede estar en blanco ni exceder 120 caracteres" |
| fullName > 120 chars | 400 | "El nombre no puede estar en blanco ni exceder 120 caracteres" |
| regionCode > 20 chars | 400 | "Codigo de region invalido" |
| comunaCode > 20 chars | 400 | "Codigo de comuna invalido" |
| Sin JWT | 401 | (RestAuthenticationEntryPoint) |

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

La politica de contrasena usa la misma regex que `RegisterRequestDTO`: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{12,72}$`.

**Efecto colateral:** se invoca `revokeAllUserRefreshTokens(userId)` — todos los refresh tokens con `revokedAt IS NULL` quedan con `revokedAt = NOW()`. El frontend redirige a `/login` tras 2.5s.

---

## 6. Reglas de negocio por endpoint

### 6.1 PATCH /api/account/me — logica de degradacion y sync

```
si request.fullName != null y request.fullName != user.fullName:
    user.fullName = request.fullName
    si verification.status ∈ {IDENTITY_VERIFIED, FULLY_VERIFIED}:
        verification.status = EMAIL_VERIFIED
        insertar VerificationEvent(
            userId, eventType="IDENTITY_RESET",
            oldStatus=prev, newStatus=EMAIL_VERIFIED,
            reviewedBy=userId, notes="Nombre cambiado por el propio usuario"
        )

si request.phone != null:
    user.phone = request.phone.isBlank() ? null : request.phone

si request.regionCode != null:
    user.regionCode = request.regionCode.isBlank() ? null : request.regionCode

si request.comunaCode != null:
    user.comunaCode = request.comunaCode.isBlank() ? null : request.comunaCode

appUserRepository.save(user)

si request.regionCode != null:
    userCommunityProfile = findById(userId).orElseCreate(new UserCommunityProfile())
    userCommunityProfile.primaryRegionId = request.regionCode.isBlank() ? null : request.regionCode
    userCommunityProfileRepository.save(userCommunityProfile)
```

Los valores de `regionCode` del frontend son `"biobio"`, `"nuble"`, `"araucania"` — coinciden con los `regionId` de las salas de chat, de modo que el acceso se habilita automaticamente al guardar.

### 6.2 POST /api/account/change-password — logica completa

```
1. resolver userId desde SecurityContext
2. cargar AppUser; si no existe → 404
3. BCrypt.matches(currentPassword, user.passwordHash) → si false → 400
4. si newPassword == currentPassword → 400
5. si newPassword != confirmPassword → 400
6. validar regex politica sobre newPassword → si falla → 400
7. user.passwordHash = BCrypt.encode(newPassword)
8. appUserRepository.save(user)
9. authService.revokeAllTokens(userId)  ← metodo publico en AuthService que delega a revokeAllUserRefreshTokens
10. retornar 200
```

---

## 7. Iteraciones — resultado

### Iteracion 1 — Backend (completada)

- [x] Migracion V4: `ALTER TABLE app_users ADD COLUMN phone/region_code/comuna_code`
- [x] Migracion V5: expandir region_code y comuna_code a VARCHAR(20)
- [x] Extension entidad `AppUser` con los tres campos nuevos (`@Column(length=20)`)
- [x] Nuevo `AccountController` en `/api/account` con los tres endpoints
- [x] `AccountService` + `AccountServiceImpl` con logica de degradacion, sync y cambio de contrasena
- [x] Extension `AccessUserDTO` + `AccessAdminServiceImpl` para incluir campos nuevos
- [x] Exponer `AuthService.revokeAllTokens(userId)` como metodo publico
- [x] CORS: agregar PATCH a `allowedMethods` en `CorsConfig`
- [x] Security: agregar `OPTIONS /**` como primera regla `permitAll()` en `SecurityIntegrationConfig`

### Iteracion 2 — Frontend (completada)

- [x] Ruta `/account` protegida con lazy loading
- [x] Link al perfil desde el nombre de usuario en Navbar
- [x] Selects de region (3 opciones) + commons filtradas por region (reset al cambiar region)
- [x] Datos estaticos en `src/data/territorioChile.js`: 86 comunas extraidas de GeoJSON GADM
- [x] Campo phone alineado correctamente en layout form-grid 2 columnas
- [x] Formulario de contrasena con show/hide, redirige a `/login` 2.5s tras exito
- [x] Mensaje de advertencia de degradacion de verificacion
- [x] Endpoints en `src/api/endpoints.js` y servicio en `src/services/accountService.js`

---

## 8. Criterios de aceptacion — verificados

| # | Criterio | Estado |
|---|---|---|
| 1 | GET /api/account/me devuelve perfil con todos los campos nuevos | ✓ |
| 2 | PATCH /api/account/me actualiza fullName, phone, regionCode, comunaCode | ✓ |
| 3 | Usuario FULLY_VERIFIED cambia fullName → verificationStatus=EMAIL_VERIFIED en respuesta | ✓ |
| 4 | Usuario EMAIL_VERIFIED cambia nombre → verificationStatus no cambia | ✓ |
| 5 | POST /change-password con currentPassword incorrecta → 400 | ✓ |
| 6 | POST /change-password con newPassword == currentPassword → 400 | ✓ |
| 7 | POST /change-password con newPassword != confirmPassword → 400 | ✓ |
| 8 | POST /change-password con contrasena debil → 400 con mensaje de politica | ✓ |
| 9 | Cambio de contrasena exitoso → todos los refresh tokens activos revocados | ✓ |
| 10 | Admin GET /api/admin/users/{id} incluye phone, regionCode, comunaCode, organizationName | ✓ |
| 11 | Formulario frontend muestra aviso de degradacion si cambia nombre con verificacion alta | ✓ |
| 12 | Frontend redirige a /login 2.5s tras cambio de contrasena exitoso | ✓ |
| 13 | Al guardar regionCode, UserCommunityProfile.primaryRegionId se sincroniza automaticamente | ✓ |
| 14 | Selects de region/comuna en frontend muestran las 3 regiones piloto y sus comunas GADM | ✓ |

---

## 9. Deuda tecnica documentada

| Item | Severidad | Descripcion |
|---|---|---|
| Cambio de correo | Media | Requiere flujo de verificacion por email con token de confirmacion — spec separada |
| Verificacion de telefono | Baja | Actualmente phone es texto libre; verificacion OTP queda para post-defensa |
| Validacion estricta region/comuna en backend | Baja | Frontend enforcea via selects; backend acepta cualquier VARCHAR(20). Idealmente validar lista en memoria |
| Foto de perfil | Baja | Requiere integracion Supabase Storage — spec separada |
| Workflow de re-verificacion de identidad | Media | Cuando admin restaura IDENTITY_VERIFIED post NAME_CHANGE, deberia existir formulario dedicado en CU15 |
| Notificacion por email post cambio de contrasena | Baja | Util para UX de seguridad; sin fecha definida |
| Regiones adicionales | Baja | Actualmente solo biobio, nuble, araucania en territorioChile.js. Escalar requiere agregar datos o consumir API |

---

## 10. Bugs resueltos durante implementacion

| Bug | Causa raiz | Solucion |
|---|---|---|
| CORS 403 en OPTIONS (preflight) | `requestMatchers("/api/account/**").authenticated()` bloqueaba OPTIONS antes del filtro CORS | Agregar `HttpMethod.OPTIONS, "/**"` como primera regla `permitAll()` en SecurityIntegrationConfig |
| CORS 403 real en PATCH | PATCH no estaba en `allowedMethods` de CorsConfig | Agregar `"PATCH"` al array de metodos permitidos |
| Validation 400 "Uno o mas campos son invalidos" | `@Size(max=10)` en comunaCode pero GADM IDs como "CHL.13.3.5_1" tienen 12-13 chars | Cambiar `@Size(max=20)` en UpdateProfileRequestDTO + migracion V5 para columna BD |
| Campo phone desalineado en formulario | `<span class="field-optional">` era hijo directo del `<label>` (flex-column), creando 3 flex items | Envolver texto de etiqueta + span opcional en un `<span>` padre |
