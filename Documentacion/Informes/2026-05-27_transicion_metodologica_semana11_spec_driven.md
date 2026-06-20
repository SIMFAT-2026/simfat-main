# Transicion Metodologica Semana 11 - De Prototipado Incremental a Spec-Driven Development

Fecha: 2026-05-27
Estado: guia de continuidad para el desarrollo posterior al MVP

## 1) Decision

Hasta el estado de avance de semana 11, SIMFAT se desarrollo bajo un modelo de prototipado incremental: incrementos funcionales pequenos, validacion temprana, estabilizacion progresiva y evidencia QA/documental por entrega.

Desde este punto, dado que ya existe un MVP funcional validado para demostracion y continuidad tecnica, el equipo incorporara Spec-Driven Development (SDD) como guia para las funcionalidades que requieren mayor precision funcional, tecnica y de aceptacion antes de implementar codigo.

## 2) Evidencia documental que respalda el modelo previo

La documentacion existente ya registra este enfoque:

- `Documentacion/Informes/2026-05-14_fase0_rbac_jwt_contrato_arquitectura_v1.md`: declara metodologia de prototipado incremental con practicas agiles, validacion temprana y trazabilidad QA.
- `Documentacion/Informes/Informe-Estado-Avance-2-Semana10-TPY1101.md`: indica que la implementacion se ejecuto de forma incremental para evitar quiebres funcionales y mantener continuidad del producto.
- `Documentacion/Informes/Informe-Estado-Avance-2-Semana10-TPY1101.md`: documenta MVP online disponible para demostracion y validacion externa.
- `Documentacion/Informes/Resultado-Desarrollo-Semana10-Por-Casos-de-Uso.md`: registra cierre de semana 10 con avance estructural sin bloquear la evolucion incremental del producto.

## 3) Motivo del cambio

El prototipado incremental permitio llegar a una base funcional operativa. Sin embargo, las siguientes funcionalidades combinan integraciones externas, reglas de negocio, permisos, calidad de datos y criterios de aceptacion que no conviene resolver solo por implementacion directa.

SDD se aplicara para definir primero:

- contratos funcionales;
- criterios de aceptacion;
- reglas de degradacion;
- permisos y estados;
- escenarios de prueba;
- limites de alcance MVP vs fases posteriores.

## 4) Features que pasan a guia SDD

### 4.1 Afinacion del mapa territorial

Objetivo: evolucionar el mapa desde visualizacion funcional parcial hacia inteligencia territorial con capas y score de riesgo.

Fuentes y variables candidatas:

- NASA FIRMS: detecciones satelitales de focos de calor/incendio.
- OpenWeather Fire Weather Index: indice meteorologico de peligro de incendio.
- NDVI y NDMI: condicion vegetal y estres hidrico.
- Viento: velocidad, direccion y rachas.
- Reportes comunitarios verificados.
- Deforestacion regional historica.

Relacion con casos de uso:

- CU03 - Visualizar mapa de incendios en tiempo real.
- CU07 - Consultar zonas de riesgo.
- CU11 - Integrar datos desde API externa.
- CU09 - Recibir notificaciones, en una fase posterior si el score activa alertas.

### 4.2 Chat comunitario dentro del modulo territorio

Objetivo: habilitar comunicacion interna entre usuarios comunitarios verificados dentro del contexto territorial.

Alcance inicial:

- usuarios comunitarios verificados por nombre y apellido;
- chat desplegable dentro del modulo de territorio;
- estado de presencia: conectado, ausente, no disponible, no conectado;
- conversacion contextual por territorio, region o sala;
- moderacion basica alineada a RBAC.

Relacion con casos de uso:

- CU15 - Gestionar usuarios, por verificacion y roles.
- CU12 - Editar perfil, por identidad visible y datos del usuario.
- CU14 - Actualizar datos personales, por consistencia de nombre/apellido verificado.
- CU09 - Recibir notificaciones, si el chat incorpora avisos de mensajes o actividad.

## 5) Estado de casos de uso segun evidencia vigente

Fuente principal: `Documentacion/Evidencias/2026-05-19_evidencia_cumplimiento_casos_uso_semana10.md` y `Documentacion/Informes/Matriz-Casos-de-Uso-Semana10-Roles-Actualizados.md`.

| Estado | Casos de uso |
|---|---|
| Completos | CU01, CU02, CU04, CU05, CU06, CU08, CU10 |
| Parciales | CU03, CU07, CU09, CU11, CU12, CU13, CU15 |
| No iniciado | CU14 |

## 6) Brechas que quedan por completar

| CU | Brecha documentada | Relacion con siguiente etapa |
|---|---|---|
| CU03 | Falta cierre de tiempo real completo en mapa. | SDD mapa territorial: frescura, polling/cache, capas y explicacion visual. |
| CU07 | Falta consolidar zonas de riesgo como capacidad cerrada y validada. | SDD score/capas de riesgo con criterios de aceptacion. |
| CU09 | Falta canal final de notificacion validado. | Puede derivar de alertas por riesgo y/o mensajes del chat. |
| CU11 | Integracion externa parcial. | Incorporar contratos para NASA FIRMS y OpenWeather FWI. |
| CU12 | Falta cierre integral de perfil. | Necesario para identidad visible en chat comunitario. |
| CU13 | Falta cambio de contrasena autenticado. | Pendiente de funciones de cuenta; no bloquea mapa/chat MVP. |
| CU14 | Sin implementacion dedicada. | Necesario para mantener datos personales consistentes con usuario verificado. |
| CU15 | RBAC y panel de accesos estan en base parcial; faltan expansiones avanzadas. | Necesario para verificacion comunitaria, moderacion y gestion de salas. |

## 7) Guia de continuidad

El desarrollo siguiente debe mantener prototipado incremental para validacion rapida, pero las features de mayor riesgo deben iniciar con especificaciones SDD antes de codigo.

Prioridad sugerida:

1. SDD mapa territorial: fuentes, score, degradacion, capas, contratos y QA.
2. SDD chat comunitario territorial: identidad verificada, presencia, permisos, salas y moderacion.
3. Cierre de funciones de cuenta: perfil, cambio de contrasena autenticado y datos personales.

Documento base:

- `Documentacion/Informes/2026-05-27_spec_gestion_cuenta_perfil_v1.md`
