# Spec v1 - Gestion de Cuenta y Perfil

Fecha: 2026-05-27
Estado: borrador para cierre de casos de uso pendientes
Enfoque: Spec-Driven Development (contratos + criterios de aceptacion antes de codigo)

## 1) Objetivo

Cerrar las brechas funcionales de cuenta de usuario que siguen pendientes despues del MVP: editar perfil, cambiar contrasena autenticado y actualizar datos personales.

Esta especificacion complementa las specs de mapa territorial y chat comunitario, porque el chat requiere identidad confiable y visible para usuarios comunitarios verificados.

## 2) Alcance por caso de uso

| CU | Estado documental actual | Objetivo de cierre |
|---|---|---|
| CU12 - Editar perfil | Parcial | Permitir editar datos basicos permitidos del perfil propio. |
| CU13 - Cambiar contrasena | Parcial | Agregar flujo autenticado de cambio de contrasena con contrasena actual. |
| CU14 - Actualizar datos personales | No iniciado | Permitir actualizar datos personales definidos, con reglas especiales para identidad verificada. |
| CU15 - Gestionar usuarios | Parcial | Usar verificacion/roles como soporte para identidad comunitaria y moderacion. |

## 3) Estado verificado en codigo/documentacion

- Existe recuperacion de contrasena por token:
  - `POST /api/auth/forgot-password`
  - `POST /api/auth/reset-password`
- No se encontro un flujo cerrado de cambio de contrasena autenticado con `currentPassword`.
- El frontend expone nombre completo en flujos de acceso y registro, pero no se encontro una pantalla dedicada de perfil propio completa.

## 4) Reglas funcionales

### 4.1 Perfil propio

El usuario autenticado debe poder consultar y editar su perfil desde una vista de cuenta.

Campos minimos:

- nombre completo;
- correo electronico de lectura;
- telefono opcional;
- region/comuna opcional;
- organizacion o agrupacion opcional;
- estado de verificacion.

Reglas:

- El correo no se modifica en esta fase.
- El estado de verificacion no lo modifica el usuario directamente.
- Si el usuario esta verificado, cambios en nombre/apellido deben quedar sujetos a revision o degradar el estado a pendiente, segun decision de negocio.

### 4.2 Cambio de contrasena autenticado

El usuario autenticado debe poder cambiar su contrasena ingresando:

- contrasena actual;
- nueva contrasena;
- confirmacion de nueva contrasena.

Reglas:

- La contrasena actual debe validarse contra el hash vigente.
- La nueva contrasena debe cumplir la politica actual de seguridad.
- La nueva contrasena no debe ser igual a la actual.
- Al cambiar contrasena, los refresh tokens activos deben invalidarse o rotarse para reducir riesgo de sesion comprometida.

### 4.3 Datos personales y verificacion comunitaria

Para usuarios comunitarios verificados, el sistema debe mantener consistencia de identidad porque el chat interno mostrara nombre y apellido como identidad visible.

Reglas:

- `ROLE_COMMUNITY_USER` puede editar datos no sensibles permitidos.
- `ROLE_VERIFIED_USER` mantiene identidad visible validada.
- Cambios sensibles de identidad deben quedar trazados.
- Administradores/moderadores no deben poder cambiar contrasenas de usuarios desde esta fase, salvo flujo futuro separado.

## 5) Contratos propuestos

Endpoint perfil:

- `GET /api/account/me`
- `PATCH /api/account/me`

Endpoint cambio de contrasena:

- `POST /api/account/change-password`

Payload minimo:

```json
{
  "currentPassword": "********",
  "newPassword": "********",
  "confirmPassword": "********"
}
```

Respuesta esperada:

- exito sin exponer hashes ni datos sensibles;
- error controlado si la contrasena actual no coincide;
- error de validacion si la nueva contrasena no cumple politica.

## 6) Criterios de aceptacion

1. Usuario autenticado consulta su perfil y visualiza datos actuales.
2. Usuario autenticado actualiza datos permitidos y ve confirmacion.
3. Usuario verificado no puede cambiar identidad sensible sin trazabilidad o revision.
4. Usuario autenticado cambia contrasena usando contrasena actual valida.
5. Intento con contrasena actual incorrecta no modifica credenciales.
6. Nueva contrasena debil o distinta de confirmacion se rechaza con error claro.
7. Despues de cambiar contrasena, sesiones/tokens quedan tratados segun politica definida.

## 7) Relacion con chat comunitario

El chat comunitario debe consumir la identidad del perfil, no duplicarla.

Para el MVP de chat:

- mostrar nombre y apellido verificado;
- mostrar estado de presencia separado de datos personales;
- impedir suplantacion visual de identidad;
- usar roles/verificacion existentes para permitir acceso a salas comunitarias.

## 8) Pendientes de decision

1. Definir si cambio de nombre/apellido en usuario verificado requiere aprobacion manual o cambia estado a pendiente.
2. Definir datos personales exactos requeridos por AIFBN/agrupacion.
3. Definir politica de sesiones posterior al cambio de contrasena: cerrar todas, mantener actual o rotar refresh tokens.
4. Definir si administradores pueden ver datos personales completos o solo estado de verificacion.
