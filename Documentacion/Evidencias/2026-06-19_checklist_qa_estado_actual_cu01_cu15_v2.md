# Checklist QA - Estado Actual CU01 a CU15 (SIMFAT) v2

Fecha: 2026-06-19
Supersede: `2026-06-15_checklist_qa_estado_actual_cu01_cu15.md`

## Resumen ejecutivo

| Estado | Cantidad |
|---|---|
| Completo | 10 |
| Cerrable con evidencia | 2 |
| Implementado / QA pendiente | 2 |
| Parcial | 1 |
| **Total declarado** | **15 / 15** |

CUs sin brecha funcional pendiente: 12 de 15 (subio de 11 a 12 — ver CU02).

## Cambios respecto a la v1 (2026-06-15)

| CU | Cambio |
|---|---|
| CU02 | **Cerrado.** `totalAlertas` ahora cuenta comunas en ALTO/CRITICO via agregacion sobre `ComunaRiskSnapshot` (ultimo snapshot por comuna), no el historico de `HeatAlertEvent`. Ver `2026-06-19_informe_sprint_clima_viento_y_fixes.md` seccion 2.2. |
| CU03 / CU07 | Sin cambio de estado, pero la integracion Copernicus que alimenta el modo ENHANCED del choropleth comunal fue corregida este sprint (sync via red privada Railway) — reduce el riesgo de regresion silenciosa que afecta a estos CUs indirectamente. |
| CU11 | Sin cambio de estado declarado. La causa raiz de NDVI/NDMI=0 para Araucania **fue identificada y corregida** (timeout/proxy de Railway) — pendiente confirmar en el dashboard regional (`/api/dashboard/indicators/*`) si el mismo patron de fix aplica ahi o si esa via usa un sync distinto (`OpenEoSyncServiceImpl`, sync regional programado, no el sync por comuna que se corrigio). **No marcar como cerrado sin verificar esto explicitamente.** |

## Estado por caso de uso

| CU | Nombre | Estado | Descripcion estado | Evidencia |
|---|---|---|---|---|
| CU01 | Iniciar sesion | Completo | Sin cambios | Evidencias-QA-E2E-y-Swagger-Semana10.md |
| CU02 | Dashboard estadistico | **Completo** | KPIs cargando; `totalAlertas` ahora alineado con el modelo de riesgo comunal vigente | 2026-06-19_informe_sprint_clima_viento_y_fixes.md |
| CU03 | Mapa tiempo real | Cerrable | Choropleth + FIRMS focos + panel comunal + **nueva capa de viento con direccion y slider horario** | 2026-06-19_informe_sprint_clima_viento_y_fixes.md |
| CU04 | Registrarse | Completo | Sin cambios | matriz-casos-uso-semana10-2026-05-11.md |
| CU05 | Recuperar contrasena | Completo | Sin cambios | matriz-casos-uso-semana10-2026-05-11.md |
| CU06 | Metricas historicas | Completo | Sin cambios | matriz-casos-uso-semana10-2026-05-11.md |
| CU07 | Zonas de riesgo | Cerrable | Sin cambios funcionales; fix de persistencia de score al cambiar de comuna en el panel (bug de UI, no de calculo) | 2026-06-19_informe_sprint_clima_viento_y_fixes.md |
| CU08 | Configurar alertas | Completo | Sin cambios | SecurityAuthorizationIntegrationTest |
| CU09 | Recibir notificaciones | Implementado / QA pendiente | Sin cambios. **Nueva pista:** `/api/territory/sync` (endpoint distinto, no el de notificaciones) usa `regionId` en minuscula — si CU09 comparte algun patron de validacion de `regionId`, revisar casing | 2026-06-05_evidencia_notificaciones_gestion_usuarios_sdd_v1.md |
| CU10 | Historial alertas | Completo | Sin cambios | matriz-casos-uso-semana10-2026-05-11.md |
| CU11 | Integrar API externa | Parcial | Causa raiz de NDVI/NDMI/LOSS=0 en Araucania identificada y corregida (Railway proxy timeout) para el sync por comuna. **Pendiente verificar si el sync regional (dashboard) tiene el mismo problema** | 2026-06-19_informe_sprint_clima_viento_y_fixes.md |
| CU12 | Editar perfil | Completo | Sin cambios | 2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md |
| CU13 | Cambiar contrasena | Completo | Sin cambios | 2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md |
| CU14 | Actualizar datos personales | Completo | Sin cambios | 2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md |
| CU15 | Gestionar usuarios | Implementado / QA pendiente | Sin cambios | 2026-06-05_checklist_qa_notificaciones_gestion_usuarios_v1.md |

## Funcionalidad nueva sin CU asignado (a clasificar)

- **Capa de direccion de viento + slider horario:** no hay un CU explicito que la cubra; podria
  encuadrarse dentro de CU03 (Mapa tiempo real) o requerir un CU nuevo si AIFBN lo considera una
  funcionalidad de analisis distinta. Pendiente de decision de producto.
- **Boton de sync manual de clima (ADMIN):** funcionalidad operativa/administrativa, no
  visible para usuarios finales. Sin CU asignado, consistente con `SyncNowButton` (Copernicus)
  que tampoco tiene CU propio.

## Acciones inmediatas (actualizadas)

1. Ejecutar QA CU09 y CU15 en produccion (sin cambios respecto a v1).
2. Verificar si el sync regional del dashboard (`OpenEoSyncServiceImpl`) tiene el mismo problema
   de timeout/proxy que se corrigio para el sync por comuna — si aplica, CU11 podria cerrarse.
3. Ejecutar plan de pruebas manuales de UI con Andres:
   `2026-06-19_plan_pruebas_manuales_ui_andres.md`.
4. Correr `mvn test` con Mongo local/CI para validar los 2 tests de integracion nuevos de esta
   sesion (`TerritoryControllerClimateIntegrationTest`, `ComunaRiskSnapshotRepositoryIntegrationTest`).
5. Asignar a Codex: criterio de accesibilidad colorblind
   (`2026-06-19_criterio_accesibilidad_colorblind_ux.md`).
6. Decidir si la capa de viento y el boton de sync de clima requieren CU formal o quedan como
   funcionalidad de soporte sin CU (ver seccion anterior).
7. Formalizar cierre de CU03 y CU07 con SLA de refresh FIRMS (24h) documentado — pendiente desde
   v1, sin cambios.

## Progreso global

- CUs con estado declarado: 15 / 15
- CUs sin brecha funcional pendiente: 12 / 15 (CU02 se suma a la lista de cerrados)
- CUs con accion requerida antes de cierre academico: 3 (CU09, CU11, CU15)
