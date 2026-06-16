# Carta Gantt Actualizada - Semana 14 (SIMFAT)

Fecha actualizacion: 2026-06-15

## Horizonte

- Semana 10 a Semana 14 (cierre de implementacion)
- Semana 14 a Semana 18 (QA final, documentacion y defensa)

## Plan consolidado

| Semana | Linea | Actividad | Estado | Evidencia |
|---|---|---|---|---|
| 10 | Seguridad | Diseno RBAC + contrato + MER/UML | Completado | docs fase 0/1 |
| 10 | Seguridad | Implementacion RBAC core + JWT authorities | Completado | migraciones + backend |
| 10 | Seguridad | Proteccion endpoints criticos + auditoria | Completado | tests seguridad |
| 10 | UX Admin | Panel control accesos v1 compacto | Completado | AccessControlPage |
| 11 | Comunidad | Chat comunitario WebSocket (rooms, moderacion, presencia) | Completado | 2026-05-28_evidencia_chat_comunitario_sdd_v1.md |
| 11-12 | Territorio | Score WLC por comuna (FWI 52% + FIRMS 33% + Reportes 15%) | Completado | 2026-05-31_evidencia_mapa_territorial_riesgo_sdd_v1.md |
| 11-12 | Territorio | Choropleth comunal interactivo v2 + panel lateral componentes WLC | Completado | 2026-06-02_evidencia_mapa_interactivo_comunal_v2_sdd_v1.md |
| 12-13 | Territorio | Indicadores climaticos OpenMeteo (WIND, HUMIDITY, AIR_TEMP, SOIL_TEMP) | Completado | commit b0f7dca |
| 12-13 | Performance | Fix N+1: /api/territory/layers (~80 queries a 2) | Completado | commit b0f7dca |
| 12-13 | Cuenta | CU12/CU13/CU14 gestion cuenta y perfil (migraciones V4/V5, AccountController) | Completado | 2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md |
| 13-14 | Notificaciones | CU09 notificaciones in-app (V6, NotificationBell, polling 30s, trigger escalada) | Implementado / QA pendiente | 2026-06-05_evidencia_notificaciones_gestion_usuarios_sdd_v1.md |
| 13-14 | Admin | CU15 panel verificacion usuarios en AccessControlPage | Implementado / QA pendiente | 2026-06-05_evidencia_notificaciones_gestion_usuarios_sdd_v1.md |
| 14 | Fixes | Alertas filtradas por regionId, soilTemp en buildFwiInputs, toggle inerte eliminado | Completado | commits semana 14 |
| 14 | Analisis | Desalineacion dashboard KPI identificada (totalAlertas global vs modelo riesgo) | Completado | analisis tecnico 2026-06-15 |
| 14-16 | Integracion | Fix NDVI/NDMI/LOSS retornando 0 para Araucania y Biobio (CU11 brecha) | Pendiente | por ejecutar |
| 14-16 | Dashboard | Unificacion KPI totalAlertas con ComunaRiskSnapshot (comunas ALTO/CRITICO) | Pendiente | por ejecutar |
| 14-16 | QA | Ejecucion QA CU09 + CU15 en produccion | Pendiente | por ejecutar |
| 14-16 | UX | Polish UX para usuario comunitario rural / indigena no nativo digital | Pendiente | por ejecutar |
| 16 | QA Final | Regresion funcional completa CU01-CU15 | Pendiente | por ejecutar |
| 17 | Documentacion | Consolidacion documental DUOC | Pendiente | por ejecutar |
| 18 | Entrega | Defensa final | Pendiente | por ejecutar |

## Hitos

1. Hito H1 (Semana 10): seguridad RBAC/JWT habilitada y documentada.
2. Hito H2 (Semana 13): CU12/CU13/CU14 completados con QA 49/49 aprobado.
3. Hito H3 (Semana 14): CU09/CU15 implementados (QA pendiente); mapa territorial de riesgo operativo en produccion.
4. Hito H4 (Semana 16): QA final de regresion completo sobre los 15 casos de uso.
5. Hito H5 (Semana 18): cierre academico y entrega final DUOC.
