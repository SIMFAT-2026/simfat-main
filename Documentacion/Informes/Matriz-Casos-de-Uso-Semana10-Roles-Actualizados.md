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

| CU | Nombre | Estado | Roles principales | Evidencia tecnica | Evidencia de cumplimiento |
|---|---|---|---|---|
| CU01 | Iniciar sesion | Completo | COMMUNITY+ | `AuthController` + `LoginPage` | `Evidencias-QA-E2E-y-Swagger-Semana10.md` (auth OK) |
| CU02 | Visualizar dashboard estadistico | Completo | PUBLIC+ | `DashboardController` + `DashboardPage` | `matriz-casos-uso-semana10-2026-05-11.md` (CU02 completo) |
| CU03 | Visualizar mapa de incendios en tiempo real | Parcial | PUBLIC+ | `AlertsPage` + `AlertsOperationalMap` | `matriz-casos-uso-semana10-2026-05-11.md` (brecha tiempo real) |
| CU04 | Registrarse | Completo | PUBLIC | `/api/auth/register` + `RegisterPage` | `matriz-casos-uso-semana10-2026-05-11.md` (CU04 completo) |
| CU05 | Recuperar contrasena | Completo | PUBLIC | forgot/reset backend + UI | `matriz-casos-uso-semana10-2026-05-11.md` (CU05 completo) |
| CU06 | Consultar metricas historicas | Completo | PUBLIC+ | endpoints series/summary + graficos | `matriz-casos-uso-semana10-2026-05-11.md` (CU06 completo) |
| CU07 | Consultar zonas de riesgo | Parcial | COMMUNITY+ | capas/indicadores parciales | `matriz-casos-uso-semana10-2026-05-11.md` (brecha cierre funcional) |
| CU08 | Configurar alertas | Completo | ADMIN/SUPER_ADMIN | reglas protegidas por permisos | `SecurityAuthorizationIntegrationTest` + matriz CU semana 10 |
| CU09 | Recibir notificaciones | Parcial | COMMUNITY+ | base de alertas, falta canal final | `matriz-casos-uso-semana10-2026-05-11.md` (canal pendiente) |
| CU10 | Consultar historial de alertas | Completo | COMMUNITY+ | consultas y filtros de alertas | matriz CU semana 10 + modulo `AlertsPage` |
| CU11 | Integrar datos desde API externa | Parcial | ADMIN+ (operacion) | OpenEO ingest/sync | `Resultado-Desarrollo-Semana10-Por-Casos-de-Uso.md` |
| CU12 | Editar perfil | Parcial | COMMUNITY+ | base auth/me, falta flujo completo | matriz CU semana 10 (flujo pendiente) |
| CU13 | Cambiar contrasena | Parcial | COMMUNITY+ | reset existe, falta cambio autenticado | matriz CU semana 10 (flujo autenticado pendiente) |
| CU14 | Actualizar datos personales | No iniciado | COMMUNITY+ | falta endpoint+UI dedicados | matriz CU semana 10 (no iniciado) |
| CU15 | Gestionar usuarios | Parcial | ADMIN/SUPER_ADMIN | RBAC core + panel accesos v1 | `2026-05-15_evidencia_qa_fase4_seguridad_rbac.md` + panel de accesos |

## Cambio relevante Semana 10

- CU15 pasa de `No iniciado` a `Parcial` por implementacion efectiva de:
  - tablas RBAC
  - resolucion de permisos
  - proteccion de endpoints
  - panel de control de accesos

## Brecha prioritaria siguiente

- Completar verificacion avanzada de usuario para robustecer `ROLE_VERIFIED_USER`.

## Evidencia consolidada recomendada

- [Evidencia de cumplimiento por casos de uso (Semana 10)](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/2026-05-19_evidencia_cumplimiento_casos_uso_semana10.md)
