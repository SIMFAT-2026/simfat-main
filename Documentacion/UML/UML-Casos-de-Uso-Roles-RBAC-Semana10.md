# UML Casos de Uso - Semana 10 (Roles RBAC Ampliados)

Fecha: 2026-05-15

## Actores

- `ROLE_PUBLIC`
- `ROLE_COMMUNITY_USER`
- `ROLE_VERIFIED_USER`
- `ROLE_MODERATOR`
- `ROLE_ADMIN`
- `ROLE_SUPER_ADMIN`

## Diagrama de casos de uso (mermaid)

```mermaid
flowchart LR
  PUBLIC[ROLE_PUBLIC]
  COMMUNITY[ROLE_COMMUNITY_USER]
  VERIFIED[ROLE_VERIFIED_USER]
  MODERATOR[ROLE_MODERATOR]
  ADMIN[ROLE_ADMIN]
  SUPER[ROLE_SUPER_ADMIN]

  CU01((Iniciar sesion))
  CU02((Visualizar dashboard))
  CU03((Visualizar mapa alertas))
  CU04((Registrarse))
  CU05((Recuperar contrasena))
  CU08((Configurar alertas))
  CU10((Consultar historial alertas))
  CU15((Gestionar usuarios y accesos))
  CUX1((Reportar incidente ciudadano))
  CUX2((Moderar contenido ciudadano))
  CUX3((Administrar permisos y roles))

  PUBLIC --> CU02
  PUBLIC --> CU03
  PUBLIC --> CU04
  PUBLIC --> CU05

  COMMUNITY --> CU01
  COMMUNITY --> CU02
  COMMUNITY --> CU10

  VERIFIED --> CUX1
  VERIFIED --> CU10

  MODERATOR --> CUX2
  MODERATOR --> CU10

  ADMIN --> CU08
  ADMIN --> CU15
  ADMIN --> CUX3

  SUPER --> CU08
  SUPER --> CU15
  SUPER --> CUX3
```

## Notas

- La jerarquia funcional es acumulativa por permisos efectivos.
- `ROLE_VERIFIED_USER` agrega capacidades de reporte y geolocalizacion por sobre comunidad.
- `ROLE_ADMIN` y `ROLE_SUPER_ADMIN` concentran operaciones sensibles y de gobierno.

---

# Actualizacion 2026-05-28 - Casos de uso chat comunitario

## Casos agregados o extendidos

- `CU09`: recibir notificaciones; el chat puede generar avisos como fase posterior.
- `CU12`: editar perfil; la identidad visible del chat depende del perfil/verificacion.
- `CU14`: actualizar datos personales; nombre/apellido verificado se reutiliza como autor visible.
- `CU15`: gestionar usuarios y accesos; agrega region primaria y grants regionales de chat.
- `CUX4`: participar en chat comunitario territorial.
- `CUX5`: moderar chat comunitario.

## Diagrama actualizado

```mermaid
flowchart LR
  COMMUNITY[ROLE_COMMUNITY_USER]
  VERIFIED[ROLE_VERIFIED_USER]
  MODERATOR[ROLE_MODERATOR]
  ADMIN[ROLE_ADMIN]
  SUPER[ROLE_SUPER_ADMIN]

  CU09((Recibir notificaciones))
  CU12((Editar perfil))
  CU14((Actualizar datos personales))
  CU15((Gestionar usuarios y accesos))
  CUX4((Participar en chat comunitario territorial))
  CUX5((Moderar chat comunitario))

  COMMUNITY --> CU12
  COMMUNITY --> CU14
  VERIFIED --> CUX4
  VERIFIED --> CU09

  MODERATOR --> CUX4
  MODERATOR --> CUX5

  ADMIN --> CU15
  ADMIN --> CUX4
  ADMIN --> CUX5

  SUPER --> CU15
  SUPER --> CUX4
  SUPER --> CUX5
```

## Reglas

- Usuarios comunitarios deben estar verificados para participar.
- Moderadores, administradores y superadministradores pueden entrar a todas las salas.
- El control inicial de moderacion se apoya en identidad verificada por falta de personal operativo dedicado.
