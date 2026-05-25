# Spec Iteracion v1 - Riesgo Geografico y Comunidad

Fecha: 2026-05-25
Estado: borrador para validacion con stakeholders (AIFBN)
Enfoque: Spec-Driven Development (contratos + criterios de aceptacion antes de codigo)

## 1) Objetivo de iteracion

Definir especificaciones funcionales y tecnicas para:
1. Inteligencia geografica con monitoreo de riesgo de incendio por region (Biobio y La Araucania).
2. Evolucion del modulo comunitario con UX simple y chat MVP.

## 2) Frente 1 - Metodologia de funcionamiento del mapa

### 2.1 Variables del modelo de monitoreo

- `Deforestacion regional anual` (historica desde 2001).
- `NDVI` (condicion de vegetacion).
- `NDMI` (humedad/estres hidrico de vegetacion).
- `Reportes comunitarios verificados`.
- `Viento` (velocidad y racha).
- `Indice externo de peligro de incendio` (ej. FWI), si la fuente esta disponible.

### 2.2 Rol de cada variable

- NDVI/NDMI: contexto biofisico de combustible (no alerta instantanea).
- Deforestacion: presion estructural acumulada del territorio.
- Reportes: senal humana local inmediata.
- Viento: acelerador operativo de propagacion.
- FWI externo: referencia sintetica experta de peligro meteorologico.

### 2.3 Temporalidad y actualizacion recomendada

- Viento: cada 1 hora.
- Reportes verificados: tiempo real / polling 1-3 min.
- FWI externo: diario (con pronostico 24-72h cuando exista).
- NDVI/NDMI: composito rolling de 7 dias + tendencia 30 dias.
- Deforestacion: mensual o trimestral (segun fuente).

### 2.4 Formula inicial de score de riesgo regional (v1)

Normalizacion objetivo: 0-100

`R = 0.30*FWI + 0.20*Viento + 0.20*NDMI_stress + 0.15*NDVI_stress + 0.10*Reportes + 0.05*Deforestacion`

Donde:
- `NDVI_stress = 1 - NDVI_norm`
- `NDMI_stress = 1 - NDMI_norm`

Reglas de degradacion:
- Si falta FWI, redistribuir su peso entre viento y NDMI.
- Si NDVI/NDMI no estan frescos, usar ultimo valor valido y bajar confianza.
- Si faltan 2 o mas fuentes, marcar salida `LOW_CONFIDENCE`.

### 2.5 Capas de mapa y prioridad visual

1. `RISK_SCORE` (capa principal).
2. `USER_REPORTS_VERIFIED`.
3. `WIND_FIELD` (flechas/isobandas simplificadas).
4. `DEFORESTATION_HISTORY`.
5. `NDVI`.
6. `NDMI`.

### 2.6 Contrato funcional de salida (propuesto)

Endpoint propuesto:
- `GET /api/territory/risk-layers?regionId={id}&from={yyyy-mm-dd}&to={yyyy-mm-dd}`

Respuesta minima:
- `regionId`
- `generatedAt`
- `qualityFlag` (`OK|STALE|LOW_CONFIDENCE`)
- `layers` (RISK_SCORE, NDVI, NDMI, DEFORESTATION_HISTORY, USER_REPORTS_VERIFIED, WIND_FIELD)
- `fusionMeta` (pesos aplicados, faltantes, nivel de confianza)

### 2.7 Criterios de aceptacion del frente 1

1. El mapa opera aunque falte una fuente externa (degradacion controlada).
2. El tooltip de riesgo explica factores y frescura de datos.
3. La UI separa "riesgo operativo" de "contexto ecologico".
4. La region puede cambiar entre Biobio/Araucania con respuesta consistente.

## 3) Frente 2 - Evolucion modulo comunitario y chat MVP

### 3.1 UX de bajo costo

- Navegacion principal: `Comunidad | Chat | Salas`.
- Bloques prioritarios: `Mural`, `Recursos`, `Contactos`.
- CTA visibles: `Iniciar conversacion` y `Entrar a sala`.

### 3.2 Chat MVP funcional

- Alcance: 1:1 y salas.
- Estados de mensaje: `sent`, `delivered`, `read` (read en salas puede quedar en fase siguiente).
- Moderacion basica: eliminar mensaje, silenciar usuario temporal, reportar.

### 3.3 Permisos por rol (alineado RBAC)

- `ROLE_COMMUNITY_USER`, `ROLE_VERIFIED_USER`: leer/enviar en espacios habilitados.
- `ROLE_MODERATOR`: acciones de moderacion.
- `ROLE_ADMIN`, `ROLE_SUPER_ADMIN`: gestion de salas y politicas.

### 3.4 Contratos iniciales chat

REST MVP:
- `POST /api/chat/conversations`
- `GET /api/chat/conversations`
- `GET /api/chat/conversations/{id}/messages`
- `POST /api/chat/conversations/{id}/messages`
- `POST /api/chat/rooms`
- `GET /api/chat/rooms`
- `POST /api/chat/rooms/{id}/join`
- `POST /api/chat/messages/{id}/moderate`

Realtime:
- Fase MVP: polling 3-5s.
- Fase siguiente: WebSocket/SSE (`message.created`, `message.updated`, `message.deleted`).

### 3.5 Criterios de aceptacion del frente 2

1. Usuario autenticado inicia chat 1:1 en maximo 3 pasos.
2. Usuario entra a sala regional y envia/recibe mensajes.
3. Moderacion basica queda auditada por usuario/fecha/accion.
4. UI usable en desktop y movil sin rediseño profundo.

## 4) Decisiones abiertas para cerrar antes de implementacion

1. Fuente principal de viento (Windy vs Open-Meteo u otra alternativa por costo/confiabilidad).
2. Fuente oficial de indice de peligro de incendio (FWI u otro) con cobertura Chile.
3. Granularidad espacial de score v1 (por region vs grilla).
4. Politica exacta de frescura NDVI/NDMI y umbral de "stale" definitivo.

## 5) Plan incremental (Now / Next / Later)

### Now
- Cerrar metodologia del score y fuentes de datos.
- Validar contratos de capas y calidad de datos.
- Validar alcance MVP de chat con matriz de permisos.

### Next
- Implementar agregador de capas y score v1.
- Implementar chat 1:1 + salas con polling.
- Ejecutar QA funcional por criterios de aceptacion.

### Later
- Migrar chat a realtime persistente.
- Ajustar pesos del score con evidencia historica local.
- Integrar pronostico 24/48/72h y alertas proactivas.

## 6) Referencias internas de continuidad

- `Documentacion/Informes/territory_backend_contract.md`
- `Documentacion/Informes/citizen_reports_backend_contract.md`
- `Documentacion/Informes/community_backend_contract.md`
- `Documentacion/Informes/diccionario-datos-simfat-backend.md`
- `Documentacion/UML/Arquitectura-Integrada-Sistema-Semana10.md`
- `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md`
