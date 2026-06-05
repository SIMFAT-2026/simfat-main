# Evidencia de Implementacion — Gestion de Cuenta y Perfil de Usuario

Fecha: 2026-06-05
Sprint: CU12 / CU13 / CU14
Ambiente verificado: produccion (Railway + Vercel)

---

## 1. Resumen de implementacion

| Componente | Archivos creados o modificados | Estado |
|---|---|---|
| Migraciones BD | V4__add_user_profile_fields.sql, V5__expand_user_profile_codes.sql | ✓ aplicadas |
| Entidad | AppUser.java — +phone, +regionCode, +comunaCode | ✓ |
| DTOs | AccountProfileDTO, UpdateProfileRequestDTO, ChangePasswordRequestDTO | ✓ |
| Servicio | AccountService.java (interfaz), AccountServiceImpl.java | ✓ |
| Controlador | AccountController.java (/api/account) | ✓ |
| Admin extension | AccessUserDTO.java +4 campos, AccessAdminServiceImpl.java | ✓ |
| Auth | AuthService.java +revokeAllTokens, AuthServiceImpl.java | ✓ |
| Seguridad | SecurityIntegrationConfig.java — OPTIONS permitAll + /api/account authenticated | ✓ |
| CORS | CorsConfig.java — PATCH en allowedMethods | ✓ |
| Frontend datos | src/data/territorioChile.js — 86 comunas GADM | ✓ |
| Frontend servicio | src/services/accountService.js | ✓ |
| Frontend endpoints | src/api/endpoints.js +accountMe, +accountChangePassword | ✓ |
| Frontend pagina | src/pages/AccountPage.jsx | ✓ |
| Frontend router | src/router/AppRouter.jsx +/account route | ✓ |
| Frontend navbar | src/components/layout/Navbar.jsx — username como Link a /account | ✓ |
| Frontend CSS | src/styles/global.css +clases account-* | ✓ |

---

## 2. Commits del sprint

| Hash | Descripcion |
|---|---|
| (commit inicial) | feat(account): implementacion completa CU12/CU13/CU14 |
| fd639f7 | fix(cors): permitir OPTIONS en toda la API para preflight CORS |
| e9616e6 | feat(account): sync regionCode -> primaryRegionId del chat al guardar perfil |
| ca84b60 | fix(account): selects region/comuna + alineacion + V5 migracion VARCHAR(20) |
| 12e7830 | fix(cors): agregar PATCH a allowedMethods |
| e5c1b1d | fix(account): corregir @Size(max=10) a max=20 en UpdateProfileRequestDTO |

---

## 3. Bugs encontrados y resueltos durante el sprint

### Bug 1 — CORS 403 en OPTIONS (preflight rechazado por Spring Security)

**Sintoma:** El navegador reportaba "Solicitud desde otro origen bloqueada" antes de que el PATCH llegara al servidor.

**Causa raiz:** La regla `.requestMatchers("/api/account/**").authenticated()` en `SecurityIntegrationConfig` evaluaba la peticion OPTIONS antes de que el filtro CORS pudiera agregar los headers de respuesta. Spring Security rechazaba el preflight con 403.

**Solucion:** Agregar `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()` como primera regla de autorizacion (antes de cualquier regla `authenticated()`). Esto permite que el filtro CORS responda el preflight sin intervencion del filtro de autenticacion.

**Archivo:** `SecurityIntegrationConfig.java`

---

### Bug 2 — CORS 403 real: PATCH no en allowedMethods

**Sintoma:** Incluso con OPTIONS permitido, el preflight seguia fallando. Los headers de respuesta incluian `Vary` pero no `Access-Control-Allow-Origin`.

**Causa raiz:** `CorsConfig.java` solo listaba `"GET", "POST", "PUT", "DELETE", "OPTIONS"`. El metodo `PATCH` no estaba incluido. El filtro CORS de Spring rechazaba el preflight porque el metodo solicitado no era permitido, sin agregar los headers de acceso.

**Solucion:** Agregar `"PATCH"` al array `allowedMethods` en `CorsConfig`.

**Archivo:** `CorsConfig.java`

---

### Bug 3 — Validation 400 con comunaCode de 13 caracteres

**Sintoma:** Al guardar un perfil con `comunaCode="CHL.13.3.5_1"` (12 chars), el backend respondia 400 "Uno o mas campos son invalidos".

**Causa raiz:** `UpdateProfileRequestDTO` tenia `@Size(max = 10)` en `comunaCode`, que es demasiado pequeno para IDs GADM Level 3. Los IDs GADM siguen el patron `CHL.{d}.{d}.{d}_{d}` y pueden tener hasta 13 caracteres.

**Solucion (doble):**
1. Cambiar `@Size(max = 10)` a `@Size(max = 20)` en `UpdateProfileRequestDTO` para `regionCode` y `comunaCode`.
2. Agregar migracion `V5__expand_user_profile_codes.sql` para expandir las columnas de `VARCHAR(10)` a `VARCHAR(20)` en `app_users`.

**Archivos:** `UpdateProfileRequestDTO.java`, `V5__expand_user_profile_codes.sql`

---

### Bug 4 — Campo phone desalineado en formulario

**Sintoma:** El campo de texto del telefono quedaba desplazado verticalmente respecto a los otros campos en el formulario de perfil.

**Causa raiz:** El elemento `<span className="field-optional">(opcional)</span>` era un hijo directo del `<label>` que usaba `flex-direction: column`. Esto creaba 3 hijos flex (nodo de texto, span, input) en lugar de 2, desplazando el input.

**Solucion:** Envolver el texto de la etiqueta y el span opcional en un `<span>` padre:
```jsx
<label>
  <span>Telefono <span className="field-optional">(opcional)</span></span>
  <input ... />
</label>
```

**Archivo:** `AccountPage.jsx`

---

## 4. Decisiones tecnicas tomadas durante el sprint

| Decision | Alternativa descartada | Razon de la decision |
|---|---|---|
| Datos de region/comuna como archivo estatico local | API externa (CONAF, INE) | Los GeoJSON GADM ya estaban en el backend; extraerlos evita latencia, dependencias y costos de API |
| regionCode = regionId del chat ("biobio" etc.) | Codigos numericos INE ("08", "09") | Facilita el sync automatico sin tabla de mapeo intermedia |
| revokeAllTokens al cambiar contrasena | Revocar solo el token actual | Maxima seguridad: si el password fue comprometido, todas las sesiones deben cerrarse |
| Redireccion frontend a /login tras 2.5s | Esperar expiracion natural del access token | UX mas clara: el usuario sabe que debe re-autenticarse inmediatamente |
| Sync automatico regionCode → primaryRegionId | Requierir accion manual del admin | La moderacion tecnica disponible es minima; el auto-sync reduce la friccion operacional |

---

## 5. Estado de criterios de aceptacion post-deploy

Verificado en produccion el 2026-06-05:

| CU | Descripcion | Estado |
|---|---|---|
| CU12 | Usuario edita su perfil (nombre, telefono, region, comuna) | ✓ Funciona |
| CU13 | Usuario cambia contrasena con validacion completa | ✓ Funciona |
| CU14 | Usuario actualiza datos personales territoriales | ✓ Funciona |
| Adicional | Sync region → acceso chat comunitario | ✓ Funciona |
| Adicional | Admin ve campos nuevos en panel de usuarios | ✓ Funciona |
