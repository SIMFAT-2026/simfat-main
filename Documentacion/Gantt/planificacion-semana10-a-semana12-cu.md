# Planificacion Actualizada - Semana 10 a 12 (Alineada a CU)

Fecha: 2026-05-11  
Referencia funcional: CU01-CU15

## 1. Objetivo de planificacion

Consolidar CU completos declarados, cerrar CU parciales prioritarios y dejar base tecnica para CU no iniciados.

## 2. Linea base al inicio de semana 10

- Completos: CU01, CU02, CU04, CU05, CU06, CU08, CU10
- Parciales: CU03, CU07, CU09, CU11, CU12, CU13
- No iniciados: CU14, CU15

## 3. Plan de trabajo por semana

| Semana | Bloque | Casos de uso | Actividades clave | Entregable |
|---|---|---|---|---|
| 10 | QA y trazabilidad | CU01, CU02, CU04, CU05, CU06, CU08, CU10 | Ejecutar plan de pruebas base + evidencias + cierre documental | Evidencias QA y ajuste de matriz CU |
| 10 | Definiciones tecnicas | CU03, CU07, CU09, CU11 | Definir criterios de "tiempo real", zonas de riesgo, notificacion y ventana temporal de ingesta | Documento de criterios de cierre |
| 11 | Cierre funcional parcial | CU03, CU07, CU11 | Implementar/ajustar componentes pendientes y validar extremo a extremo | Version candidata de cierre parcial -> completo |
| 11 | Base de seguridad | CU15 | Definir RBAC (roles/permisos) y politica de gestion de usuarios | Especificacion RBAC + backlog tecnico |
| 12 | Funciones de cuenta | CU12, CU13, CU14 | Diseñar e implementar perfil, cambio password autenticado y datos personales | Flujo de perfil funcional |
| 12 | Notificaciones | CU09 | Integrar canal y prueba de despacho real | Notificacion validada con evidencia |

## 4. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigacion |
|---|---|---|
| Ambiguedad en definicion "tiempo real" (CU03) | Medio/Alto | Acordar SLA funcional (frecuencia de refresco y ventana de datos) |
| Ausencia de RBAC (CU15) | Alto | Priorizar diseno de roles minimo viable (Admin, Usuario General) |
| Integracion API externa sin normalizacion cerrada (CU11) | Alto | Definir contrato canonical de datos y validaciones de ingesta |
| Entrega documental sin evidencia de ejecucion | Alto | Correr bateria minima de pruebas sobre CU completos |

## 5. Criterios de control por hito

- Hito QA semana 10: plan de pruebas + checklist + evidencias iniciales por CU completo.
- Hito funcional semana 11: CU03/CU07/CU11 con criterios funcionales formalizados y demo reproducible.
- Hito seguridad semana 12: modelo RBAC aprobado y ruta de implementacion de CU15.
