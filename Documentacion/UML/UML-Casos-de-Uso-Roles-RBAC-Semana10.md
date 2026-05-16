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
