# Evidencia de Cumplimiento por Casos de Uso - Semana 10

Fecha de consolidacion: 2026-05-19
Proyecto: SIMFAT
Alcance: Validar cumplimiento funcional y tecnico de CU01-CU15 con evidencia documental existente en el repositorio.

## Criterio de lectura

- `Completo`: existe implementacion operativa y evidencia QA/documental suficiente.
- `Parcial`: existe implementacion base, pero hay brechas funcionales, de integracion o de cierre UX.
- `No iniciado`: no existe implementacion funcional verificable en esta iteracion.

## Matriz de cumplimiento con evidencia

| CU | Estado | Evidencia principal | Evidencia complementaria | Observacion de cumplimiento |
|---|---|---|---|---|
| CU01 - Iniciar sesion | Completo | `Documentacion/Evidencias/Evidencias-QA-E2E-y-Swagger-Semana10.md` | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | Flujo de autenticacion operativo con validacion QA. |
| CU02 - Visualizar dashboard estadistico | Completo | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/Checklist-QA-Semana10-DUOC.md` | Dashboard disponible y validado en ambiente de pruebas. |
| CU03 - Visualizar mapa en tiempo real | Parcial | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/Plan-de-Pruebas-Semana10-DUOC.md` | Visualizacion disponible; brecha en tiempo real completo. |
| CU04 - Registrarse | Completo | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/Evidencias-QA-E2E-y-Swagger-Semana10.md` | Registro habilitado y probado en flujo E2E. |
| CU05 - Recuperar contrasena | Completo | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/checklist-qa-cu01-cu15.md` | Flujo de recuperacion implementado y trazado en checklist. |
| CU06 - Consultar metricas historicas | Completo | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/Plan-de-Pruebas-Semana10-DUOC.md` | Consultas y visualizacion historica operativas. |
| CU07 - Consultar zonas de riesgo | Parcial | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/Checklist-QA-Semana10-DUOC.md` | Cobertura parcial; faltan cierres funcionales de riesgo. |
| CU08 - Configurar alertas | Completo | `Documentacion/Evidencias/2026-05-15_evidencia_qa_fase4_seguridad_rbac.md` | `Documentacion/Evidencias/2026-05-14_plan_pruebas_rbac_jwt_v1.md` | Accion protegida con RBAC y validada por pruebas de seguridad. |
| CU09 - Recibir notificaciones | Parcial | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/plan-pruebas-cu01-cu15.md` | Base funcional presente; faltan canales finales de notificacion. |
| CU10 - Consultar historial de alertas | Completo | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/checklist-qa-cu01-cu15.md` | Historial consultable con evidencia funcional. |
| CU11 - Integrar datos API externa | Parcial | `Documentacion/Resultado-Desarrollo-Semana10-Por-Casos-de-Uso.md` | `Documentacion/Evidencias/CHECKLIST-EVIDENCIAS-backend.md` | Integracion OpenEO habilitada de forma parcial y estabilizada por etapas. |
| CU12 - Editar perfil | Parcial | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/Plan-de-Pruebas-Semana10-DUOC.md` | Existe base de cuenta/perfil; falta cierre integral de flujo. |
| CU13 - Cambiar contrasena | Parcial | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/checklist-qa-cu01-cu15.md` | Reset disponible; pendiente flujo autenticado de cambio directo. |
| CU14 - Actualizar datos personales | No iniciado | `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md` | `Documentacion/Evidencias/Checklist-QA-Semana10-DUOC.md` | Sin implementacion dedicada en esta iteracion. |
| CU15 - Gestionar usuarios | Parcial | `Documentacion/Evidencias/2026-05-15_evidencia_qa_fase4_seguridad_rbac.md` | `Documentacion/Informes/Matriz-Casos-de-Uso-Semana10-Roles-Actualizados.md` | RBAC + control de accesos operativo en version compacta; faltan expansiones de administracion avanzada. |

## Evidencia tecnica transversal (seguridad y contratos)

- Endpoints de documentacion activos:
  - `/v3/api-docs`
  - `/swagger-ui/index.html`
- Pruebas de integracion reportadas OK:
  - `SecurityAuthorizationIntegrationTest`
  - `OpenApiSwaggerIntegrationTest`
- Entregables RBAC/JWT con endurecimiento de permisos y trazabilidad:
  - `Documentacion/Evidencias/2026-05-14_checklist_qa_fase0_rbac_v1.md`
  - `Documentacion/Evidencias/2026-05-15_evidencia_qa_fase4_seguridad_rbac.md`

## Conclusiones ejecutivas

- El avance permite demostracion funcional real de autenticacion, control de acceso por rol y trazabilidad base.
- El mayor valor de negocio ya operativo es la separacion de responsabilidades con minimo privilegio.
- Las brechas prioritarias para siguiente iteracion se concentran en CU03, CU07, CU09, CU12, CU13 y CU14.

## Proximas evidencias a incorporar (pendiente equipo)

- Capturas visuales QA por caso de uso en ambiente remoto (staging/produccion).
- Registro de ejecucion paso a paso por perfil de usuario para demo con AIFBN.
