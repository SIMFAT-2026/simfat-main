# Matriz de Casos de Uso - Semana 10 (Actualizada con Roles RBAC)

Fecha de corte: 2026-05-15

## Roles considerados

- `ROLE_PUBLIC`
- `ROLE_COMMUNITY_USER`
- `ROLE_VERIFIED_USER`
- `ROLE_MODERATOR`
- `ROLE_ADMIN`
- `ROLE_SUPER_ADMIN`

## Estado por caso de uso

| CU | Nombre | Estado | Roles principales | Evidencia |
|---|---|---|---|---|
| CU01 | Iniciar sesion | Completo | COMMUNITY+ | AuthController + LoginPage |
| CU02 | Visualizar dashboard estadistico | Completo | PUBLIC+ | DashboardController + DashboardPage |
| CU03 | Visualizar mapa de incendios en tiempo real | Parcial | PUBLIC+ | AlertsPage + AlertsOperationalMap |
| CU04 | Registrarse | Completo | PUBLIC | `/api/auth/register` + RegisterPage |
| CU05 | Recuperar contrasena | Completo | PUBLIC | forgot/reset backend + UI |
| CU06 | Consultar metricas historicas | Completo | PUBLIC+ | endpoints series/summary + graficos |
| CU07 | Consultar zonas de riesgo | Parcial | COMMUNITY+ | capas/indicadores parciales |
| CU08 | Configurar alertas | Completo | ADMIN/SUPER_ADMIN | reglas protegidas por permisos |
| CU09 | Recibir notificaciones | Parcial | COMMUNITY+ | base de alertas, falta canal final |
| CU10 | Consultar historial de alertas | Completo | COMMUNITY+ | consultas y filtros de alertas |
| CU11 | Integrar datos desde API externa | Parcial | ADMIN+ (operacion) | OpenEO ingest/sync |
| CU12 | Editar perfil | Parcial | COMMUNITY+ | base auth/me, falta flujo completo |
| CU13 | Cambiar contrasena | Parcial | COMMUNITY+ | reset existe, falta cambio autenticado |
| CU14 | Actualizar datos personales | No iniciado | COMMUNITY+ | falta endpoint+UI dedicados |
| CU15 | Gestionar usuarios | Parcial | ADMIN/SUPER_ADMIN | RBAC core + panel accesos v1 |

## Cambio relevante Semana 10

- CU15 pasa de `No iniciado` a `Parcial` por implementacion efectiva de:
  - tablas RBAC
  - resolucion de permisos
  - proteccion de endpoints
  - panel de control de accesos

## Brecha prioritaria siguiente

- Completar verificacion avanzada de usuario para robustecer `ROLE_VERIFIED_USER`.
