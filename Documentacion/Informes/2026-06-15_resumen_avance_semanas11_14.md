# Resumen de Avance Semanas 11 a 14 (SIMFAT)

Fecha: 2026-06-15

## Resumen ejecutivo

Desde la semana 10, el proyecto ejecuto cuatro sprints adicionales que cerraron 8 casos de uso y establecieron la arquitectura territorial de monitoreo de riesgo como componente operativo central del sistema. CU12, CU13 y CU14 quedaron completos con QA 49/49 aprobado. CU09 y CU15 estan implementados con QA pendiente de ejecucion en produccion. CU03 y CU07 son cerrables con evidencia. El mapa territorial de riesgo por comuna es funcional en produccion.

## Sprint S11 - Chat comunitario

Objetivo: habilitar comunicacion en tiempo real entre actores territoriales.

Implementacion:
- Servidor WebSocket con rooms por region, moderacion de mensajes y presencia en linea.
- Backend: `CommunityChatController`, `CommunityChatRoom`, `CommunityChatMessage`.
- Frontend: modulo `CommunityChat` integrado en la interfaz comunitaria.
- SDD completo: propuesta, spec, design, tasks, apply y QA ejecutados.

CU impactado: ninguno de los 15 directamente. Funcionalidad transversal de soporte a la comunidad territorial.

Evidencia: `2026-05-28_evidencia_chat_comunitario_sdd_v1.md`

## Sprint S11-12 - Mapa territorial de riesgo de incendio

Objetivo: implementar CU03 y CU07 con modelo de riesgo cuantitativo por comuna.

Implementacion:
- Score WLC (Weighted Linear Combination) por comuna: FWI 52%, FIRMS 33%, reportes ciudadanos 15%.
- Colecciones MongoDB nuevas: `comuna_risk_snapshots`, `territory_risk_snapshots`.
- Backend: `TerritoryController`, `ComunaRiskServiceImpl`, `ComunaRiskSnapshot`.
- Frontend: `TerritoryMapPanel.jsx` con choropleth interactivo por nivel (NORMAL / PREVENTIVO / ALTO / CRITICO).
- Panel lateral `ComunaRiskPanel.jsx` con desglose de componentes WLC, historial 30 dias y breakdown FWI proxy via OpenMeteo.
- Capas toggleables en el mapa: FIRMS focos activos, alertas, riesgo choropleth.

CUs cerrados: CU03 (Mapa tiempo real), CU07 (Zonas de riesgo).

Evidencias: `2026-05-31_evidencia_mapa_territorial_riesgo_sdd_v1.md`, `2026-06-02_evidencia_mapa_interactivo_comunal_v2_sdd_v1.md`

## Sprint S12-13 - Indicadores climaticos y fix N+1

Objetivo: enriquecer el score de riesgo con variables ambientales en tiempo real y corregir degradacion de performance.

Implementacion:
- Integracion OpenMeteo para cuatro indicadores: WIND, HUMIDITY, AIR_TEMP, SOIL_TEMP, incorporados al score ENHANCED de cada comuna.
- Fix critico de performance en `/api/territory/layers`: el patron N+1 en `climateValueMap` generaba ~80 consultas MongoDB por solicitud. Corregido con `findByRegionIdInOrderByObservedAtDesc` reduciendo a 2 queries. Commit `b0f7dca`.

CU impactado: CU03 (mejora de fidelidad del score), CU11 (datos climaticos integrados al pipeline).

## Sprint S12-13 - Gestion de cuenta y perfil

Objetivo: cerrar CU12, CU13 y CU14.

Implementacion:
- Migraciones PostgreSQL V4 (columna `phone`) y V5 (columnas `regionCode`, `comunaCode`) sobre tabla `app_users`.
- Backend: `AccountController`, `AccountService` con endpoints PATCH /api/account/me y POST /api/account/change-password.
- Cambio de contrasena con revocacion de refresh tokens activos (invalidacion de sesiones paralelas).
- Frontend: `AccountPage.jsx` con formulario unificado de datos personales y seccion de cambio de contrasena.
- QA: 49/49 items aprobados.

CUs cerrados: CU12 (Editar perfil), CU13 (Cambiar contrasena), CU14 (Actualizar datos personales).

Evidencia: `2026-06-05_checklist_qa_gestion_cuenta_perfil_v1.md`

## Sprint S13-14 - Notificaciones y gestion de usuarios

Objetivo: implementar CU09 y avanzar CU15.

Implementacion:
- Migracion PostgreSQL V6: tabla `notifications` con campos userId, type, message, read, createdAt.
- Trigger de escalada: cuando el nivel de riesgo de una comuna pasa a ALTO o CRITICO, se genera una notificacion para los usuarios de esa region.
- Backend: `NotificationController`, `NotificationService`.
- Frontend: `NotificationBell.jsx` con polling cada 30 segundos e indicador de no leidas.
- Extension de `AccessControlPage` con seccion "Verificaciones pendientes" para que administradores gestionen usuarios sin rol verificado (CU15).

Estado: implementacion completa. QA pendiente de ejecucion en produccion.

CUs implementados: CU09 (Recibir notificaciones), CU15 (Gestionar usuarios).

Evidencia: `2026-06-05_evidencia_notificaciones_gestion_usuarios_sdd_v1.md`

## Sprint S14 - Fixes y analisis de desalineacion

Correcciones ejecutadas:
- Alertas filtradas por `regionId` en la capa de mapa (antes mostraban alertas de todas las regiones).
- `soilTemp` agregado correctamente a `buildFwiInputs` y al panel comunal de desglose FWI.
- Toggle RISK_SCORE inerte eliminado del selector de capas en el mapa.

Analisis realizado:
- Se identifico desalineacion en el dashboard KPI: la metrica `totalAlertas` cuenta el historico global de la tabla `alerts`, no refleja el modelo de riesgo actual por comuna. Decision: unificar contando comunas con nivel ALTO o CRITICO segun `ComunaRiskSnapshot`.

## Deuda tecnica identificada

1. NDVI/NDMI/LOSS retorna 0 para las regiones monitoreadas (Araucania, Biobio). El sync diario OpenEO opera sin error aparente, pero los valores no se persisten correctamente. Causa raiz pendiente de investigacion.
2. `totalAlertas` en dashboard (CU02) no esta alineado con el modelo WLC de riesgo actual. Requiere unificacion via `ComunaRiskSnapshot`.
3. QA de CU09 y CU15 no ha sido ejecutado en produccion. La implementacion esta desplegada pero sin validacion formal.

## Proximos pasos

1. Ejecutar plan de pruebas QA para CU09 y CU15 en produccion.
2. Investigar y corregir la causa raiz de NDVI/NDMI/LOSS = 0 en OpenEO (CU11 brecha).
3. Implementar unificacion de `totalAlertas` en dashboard con `ComunaRiskSnapshot`.
4. Polish UX orientado al usuario comunitario rural o indigena no nativo digital.
5. Ejecutar QA final de regresion completa CU01-CU15 (Semana 16).
6. Consolidacion documental DUOC (Semana 17).
