# Checklist QA - Estado Actual CU01 a CU15 (SIMFAT)

Fecha: 2026-06-15

## Resumen ejecutivo

| Estado | Cantidad |
|---|---|
| Completo | 9 |
| Cerrable con evidencia | 2 |
| Implementado / QA pendiente | 2 |
| Parcial | 1 |
| Funcional / Pendiente mejora | 1 |
| **Total declarado** | **15 / 15** |

CUs sin brecha funcional pendiente: 11 de 15.

## Estado por caso de uso

| CU | Nombre | Estado | Descripcion estado | Evidencia |
|---|---|---|---|---|
| CU01 | Iniciar sesion | Completo | Formulario login, JWT, redireccion por rol | Evidencias-QA-E2E-y-Swagger-Semana10.md |
| CU02 | Dashboard estadistico | Funcional / Pendiente mejora | KPIs cargando; totalAlertas desalineado (cuenta historica vs modelo de riesgo actual) | Analisis tecnico 2026-06-15 |
| CU03 | Mapa tiempo real | Cerrable | Choropleth + FIRMS focos + panel comunal; SLA 24h refresh FIRMS | 2026-06-02_evidencia_mapa_interactivo_comunal_v2_sdd_v1.md |
| CU04 | Registrarse | Completo | Alta usuario, validacion correo duplicado | matriz-casos-uso-semana10-2026-05-11.md |
| CU05 | Recuperar contrasena | Completo | Flujo forgot/reset con token; validacion expiracion | matriz-casos-uso-semana10-2026-05-11.md |
| CU06 | Metricas historicas | Completo | Series NDVI/NDMI/LOSS + graficos + filtros | matriz-casos-uso-semana10-2026-05-11.md |
| CU07 | Zonas de riesgo | Cerrable | Choropleth por nivel NORMAL/PREVENTIVO/ALTO/CRITICO por comuna | 2026-06-01_evidencia_choropleth_comunal_sdd_v1.md |
| CU08 | Configurar alertas | Completo | CRUD reglas de alerta, protegido por ROLE_ADMIN/SUPER_ADMIN | SecurityAuthorizationIntegrationTest |
| CU09 | Recibir notificaciones | Implementado / QA pendiente | Backend V6 + NotificationBell + polling 30s; trigger en escalada de nivel. QA no ejecutado en produccion | 2026-06-05_evidencia_notificaciones_gestion_usuarios_sdd_v1.md |
| CU10 | Historial alertas | Completo | Listado, filtros, paginacion | matriz-casos-uso-semana10-2026-05-11.md |
| CU11 | Integrar API externa | Parcial | FIRMS + OpenEO sync diario operativo; NDVI/NDMI/LOSS retorna 0 para regiones monitoreadas — investigacion pendiente | 2026-05-31_evidencia_mapa_territorial_riesgo_sdd_v1.md |
| CU12 | Editar perfil | Completo | PATCH /api/account/me, fullName/phone/regionCode/comunaCode, QA 49/49 | 2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md |
| CU13 | Cambiar contrasena | Completo | POST /api/account/change-password, revocacion tokens, QA 49/49 | 2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md |
| CU14 | Actualizar datos personales | Completo | Incluido en flujo AccountPage, QA 49/49 | 2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md |
| CU15 | Gestionar usuarios | Implementado / QA pendiente | Panel verificaciones pendientes en AccessControlPage; endpoints /api/admin/access/users. QA no ejecutado en produccion | 2026-06-05_checklist_qa_notificaciones_gestion_usuarios_v1.md |

## Acciones inmediatas

1. Ejecutar QA CU09 y CU15 en produccion siguiendo los planes de prueba existentes.
2. Investigar causa raiz de NDVI/NDMI/LOSS = 0 para las regiones Araucania y Biobio en OpenEO sync.
3. Unificar metrica totalAlertas del dashboard (CU02) con ComunaRiskSnapshot: contar comunas en nivel ALTO o CRITICO en lugar de historico global.
4. Formalizar cierre de CU03 y CU07 con SLA de refresh FIRMS (24h) documentado en la evidencia correspondiente.

## Progreso global

- CUs con estado declarado: 15 / 15
- CUs sin brecha funcional pendiente: 11 / 15
- CUs con accion requerida antes de cierre academico: 4 (CU02, CU09, CU11, CU15)
