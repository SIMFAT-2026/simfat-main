# Fase 0 - Contrato Tecnico RBAC + JWT (SIMFAT Backend)

- Fecha: 2026-05-14
- Version: 1.0
- Estado: Aprobado para diseno y planificacion (sin implementacion)
- Rama de trabajo: `sprint/fase0-rbac-permisos-jwt`
- Metodologia: Prototipado incremental con practicas agiles (entregas pequenas, validacion temprana, trazabilidad QA)

## 1. Objetivo del contrato

Definir el marco tecnico y de gobernanza para incorporar control de acceso basado en roles (RBAC) en SIMFAT, con JWT y Spring Security, reduciendo deuda tecnica futura y evitando redisenos de alto costo.

## 2. Alcance de Fase 0

Incluye:
- Definicion oficial de roles y jerarquia de acceso.
- Definicion de permisos por dominio funcional.
- Definicion de estado de verificacion de usuario.
- Definicion de impacto tecnico en Spring Security, JWT y entidades.
- Definicion de plan incremental de implementacion por fases.
- Definicion de plan de pruebas y criterios DoD.

No incluye:
- Cambios en codigo de backend productivo.
- Migraciones aplicadas en ambientes remotos.
- Activacion de bloqueos de endpoints en runtime.

## 3. Roles oficiales del sistema

1. `ROLE_SUPER_ADMIN`
- Acceso total del sistema.
- Gestion de administradores y configuracion global.
- Control de seguridad e infraestructura.

2. `ROLE_ADMIN`
- Gestion de usuarios y verificaciones.
- Gestion de moderadores.
- Gestion de recursos y reglas operativas.

3. `ROLE_MODERATOR`
- Moderacion de contenido y reportes.
- Suspension temporal y escalamiento.

4. `ROLE_VERIFIED_USER`
- Reportes ciudadanos con evidencia, geolocalizacion y participacion comunitaria completa.

5. `ROLE_COMMUNITY_USER`
- Acceso a recursos comunitarios, paneles publicos y alertas basicas.

6. `ROLE_PUBLIC` (conceptual)
- Usuario no autenticado.
- No se persiste en DB como rol asignable.

## 4. Jerarquia funcional

`SUPER_ADMIN > ADMIN > MODERATOR > VERIFIED_USER > COMMUNITY_USER > PUBLIC`

Nota: la jerarquia se define para gobernanza, pero autorizacion real se basara en permisos explicitos para aplicar minimo privilegio.

## 5. Politicas de autorizacion

- Minimo privilegio por defecto.
- Denegar escritura si no existe permiso explicito.
- Permisos declarativos (`PERM_*`) y no hardcode de reglas por endpoint.
- Separacion entre autenticacion (JWT valido) y autorizacion (permiso vigente).
- Auditoria de cambios sensibles (roles, verificaciones, moderacion).

## 6. Convenciones tecnicas

- Roles: `ROLE_*`
- Permisos: `PERM_*`
- Estados de verificacion: `UNVERIFIED`, `EMAIL_VERIFIED`, `PHONE_VERIFIED`, `IDENTITY_VERIFIED`, `FULLY_VERIFIED`, `REJECTED`, `SUSPENDED`
- Estructura recomendada: `roles`, `permissions`, `role_permissions`, `user_roles`, `user_verification`

## 7. Criterios de compatibilidad

- Compatibilidad con JWT actual (claim de roles vigente).
- Introduccion gradual de permisos sin romper flujo login/refresh/logout.
- Endpoints criticos protegidos por iteraciones para evitar quiebre funcional.

## 8. Riesgos aceptados en Fase 0

- Posible ajuste futuro de matriz permiso-endpoint tras pruebas integrales.
- Riesgo de payload JWT excesivo si se incluyen demasiados permisos en token.
- Riesgo de regresion frontend si se endurece seguridad sin feature flag.

## 9. Aprobacion

Aprobado como contrato base para iniciar Fase 1 (modelo de datos y seguridad declarativa).

## 10. Matriz resumida de roles y capacidades (base de presentacion)

| Capacidad | COMMUNITY_USER | VERIFIED_USER | MODERATOR | ADMIN | SUPER_ADMIN |
|---|---|---|---|---|---|
| Ver recursos comunitarios | Si | Si | Si | Si | Si |
| Crear reporte ciudadano | No | Si | Parcial (si hereda permiso explicito) | Si | Si |
| Moderar reportes ciudadanos | No | No | Si | Si | Si |
| Gestionar mural comunitario | No | No | Si | Si | Si |
| Gestionar recursos comunitarios | No | No | Si | Si | Si |
| Gestionar contactos comunitarios | No | No | No | Si | Si |
| Gestionar reglas de alerta | No | No | No | Si | Si |
| Gestionar regiones (CRUD/AOI) | No | No | No | Si | Si |
| Ejecutar sync dashboard manual | No | No | No | Si | Si |
| Gestionar usuarios/roles | No | No | No | Si | Si |

Notas:
- El principio aplicado es minimo privilegio con permisos declarativos `PERM_*`.
- La fuente de verdad de autorizacion es RBAC persistido en BD (`roles`, `permissions`, `role_permissions`, `user_roles`).
