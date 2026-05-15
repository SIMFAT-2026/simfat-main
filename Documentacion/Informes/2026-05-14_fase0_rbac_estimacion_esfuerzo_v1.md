# Fase 0 - Estimacion de Esfuerzo RBAC + JWT

- Fecha: 2026-05-14
- Version: 1.0
- Supuesto: equipo backend de 2 desarrolladores + apoyo QA parcial

## 1. Resumen de esfuerzo (estimacion inicial)

| Fase | Entregable principal | Complejidad | Esfuerzo estimado |
|---|---|---|---|
| Fase 0 | Contrato, arquitectura, DoD, plan QA | Media | 2-3 dias |
| Fase 1 | Modelo datos RBAC y verificacion (PostgreSQL + ajustes MongoDB) | Alta | 4-6 dias |
| Fase 2 | Integracion Spring Security + JWT + permisos declarativos | Alta | 5-7 dias |
| Fase 3 | Proteccion gradual endpoints y ajustes frontend de consumo | Alta | 4-6 dias |
| Fase 4 | Auditoria, trust score inicial, hardening y regresion completa | Media-Alta | 4-5 dias |

Rango total: 19-27 dias habiles (sin contingencias mayores).

## 2. Desglose por area

- Analisis y diseno seguridad: 20%
- Cambios de persistencia y migraciones: 25%
- Implementacion Security/JWT/autorizacion: 30%
- QA, pruebas integracion y regresion: 20%
- Documentacion y cierre: 5%

## 3. Riesgos que impactan esfuerzo

- Migracion desde roles CSV a modelo relacional sin downtime.
- Dependencia de sincronizacion con frontend en cambios de autorizacion.
- Casos limite de token expirado + rol modificado en caliente.
- Cobertura automatizada insuficiente de seguridad por endpoint.

## 4. Estrategia de mitigacion

- Rollout por lotes de endpoints con feature toggle por ambiente.
- Pruebas de regresion auth antes de cada endurecimiento.
- Matriz de permisos versionada y aprobada antes de codificar.
- Verificacion CI obligatoria para pruebas de seguridad criticas.

## 5. Criterio de reestimacion

Se reestima si ocurre cualquiera de los siguientes eventos:
- Cambios en jerarquia de roles o nuevos dominios funcionales.
- Requisito de multi-tenant o permisos por territorio.
- Incorporacion obligatoria de verificadores externos (documento/identidad).
