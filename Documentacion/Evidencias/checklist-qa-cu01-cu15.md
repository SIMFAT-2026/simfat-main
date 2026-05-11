# Checklist QA - Semana 10 (Alineado a Casos de Uso)

Fecha: 2026-05-11  
Objetivo: verificar calidad funcional mínima por caso de uso antes de entrega.

## 1. Reglas de uso del checklist

- Marcar cada item con: `SI`, `NO` o `N/A`.
- Adjuntar evidencia (captura, log, request/response, commit o acta de prueba).
- Si un item crítico queda en `NO`, el CU no puede declararse cerrado.

## 2. Checklist transversal

| Item | Estado | Evidencia |
|---|---|---|
| Existe trazabilidad CU -> pantalla/endpoint -> prueba | SI | Matriz CU + Plan de Pruebas |
| Se válidaron errores de negocio principales | NO | Pendiente Ejecución sistematica |
| Se válidaron permisos/seguridad en rutas protegidas | PARCIAL | Auth y rutas protegidas implementadas |
| Se registraron incidencias y acciónes de correccion | NO | Pendiente consolidación semana 10 |

## 3. Checklist por caso de uso

| CU | Estado esperado (Semana 10) | QA Funcional | QA Excepciones | QA Evidencia mínima | Resultado |
|---|---|---|---|---|---|
| CU01 Iniciar sesión | Completo | Form login + redireccion | Usuario inexistente/password incorrecta | Captura + respuesta API | PENDIENTE |
| CU02 Dashboard estadistico | Completo | Carga KPI + filtros | Error de carga / sin datos | Captura + log frontend | PENDIENTE |
| CU03 Mapa tiempo real | Parcial | Carga mapa y eventos | API no disponible | Captura + endpoint usado | PENDIENTE |
| CU04 Registro | Completo | Alta de usuario | Correo duplicado | Captura + respuesta API | PENDIENTE |
| CU05 Recuperar contraseña | Completo | Solicitud + reset | Token expirado/invalido | Evidencia token de prueba + reset | PENDIENTE |
| CU06 métricas históricas | Completo | visualización de históricos | Error consulta histórica | Captura grafico + payload | PENDIENTE |
| CU07 Zonas de riesgo | Parcial | Vista de criticidad/base riesgo | Datos insuficientes | Captura mapa + criterio riesgo | PENDIENTE |
| CU08 Configurar alertas | Completo | CRUD de reglas | Datos inválidos | Capturas crear/editar/eliminar | PENDIENTE |
| CU09 Recibir notificaciones | Parcial | UI/flujo asociado a alerta | Falla de envio | Registro de trigger y canal | BLOQUEADA |
| CU10 Historial alertas | Completo | Listado y filtros | Sin alertas | Capturas tabla/filtros | PENDIENTE |
| CU11 Integrar API externa | Parcial | Ingesta y persistencia | Error formato/API down | Log de ingesta + BD | PENDIENTE |
| CU12 Editar perfil | Parcial | Flujo no cerrado | validaciones de perfil | N/A en semana 10 | BLOQUEADA |
| CU13 Cambiar contraseña | Parcial | Solo reset disponible | Password actual incorrecta | N/A en semana 10 | BLOQUEADA |
| CU14 Actualizar datos personales | No iniciado | Sin flujo operativo | N/A | N/A | BLOQUEADA |
| CU15 Gestiónar usuarios | No iniciado | Sin RBAC/admin cerrado | N/A | N/A | BLOQUEADA |

## 4. Criterio de aceptacion de entrega (semana 10)

- ningún CU marcado como `Completo` puede quedar sin evidencia mínima.
- Los CU `Parcial` deben declarar claramente su brecha de cierre.
- Mantener consistencia entre:
  - Matriz de casos de uso
  - Plan de pruebas
  - Checklist QA

## 5. Pendientes inmediatos para la entrega

- Ejecutar al menos 1 pasada de pruebas sobre CU completos (CU01, CU02, CU04, CU05, CU06, CU08, CU10).
- Adjuntar evidencias en carpeta `Documentacion/Evidencias`.
- Registrar incidencias de severidad alta s? aparecen durante la corrida.


