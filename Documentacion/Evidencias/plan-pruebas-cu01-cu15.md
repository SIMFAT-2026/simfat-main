# Plan de Pruebas - SIMFAT (Alineado a Casos de Uso)

Fecha: 2026-05-11  
Base funcional: `casos de uso SIMFAT PLANTILLA.docx` (CU01-CU15)  
Objetivo: definir pruebas funcionales por CU para control de semana 10 y siguientes iteraciones.

## 1. Alcance

Este plan cubre pruebas funcionales de los casos de uso CU01 a CU15, con foco en:

- Flujos principales (happy path).
- validaciones y excepciones relevantes definidas en cada CU.
- Evidencia de estado real: completo, parcial o no iniciado.

## 2. Ambiente de pruebas

- Frontend: `Producto/frontend/simfat-web`
- Backend principal: `Producto/backend/simfat-backend`
- Servicio OpenEO: `Producto/backend/openeo-service`
- Base de datos: PostgreSQL + MongoDB (según módulo).

## 3. Criterio de resultado

- `OK`: caso ejecutado y resultado esperado cumplido.
- `PENDIENTE`: prueba definida pero no ejecutada en esta iteración.
- `BLOQUEADA`: no ejecutable por dependencia funcional aún incompleta.

## 4. Matriz de pruebas por caso de uso

| ID Prueba | CU | Escenario | Resultado esperado | Estado Ejecución |
|---|---|---|---|---|
| TP-CU01-01 | CU01 Iniciar sesión | Login con credenciales válidas | Usuario autenticado y redirigido a vista protegida | PENDIENTE |
| TP-CU01-02 | CU01 Iniciar sesión | Login con usuario inexistente | Mensaje de error de autenticación | PENDIENTE |
| TP-CU01-03 | CU01 Iniciar sesión | Login con password incorrecta | Mensaje de error y opción de reintento | PENDIENTE |
| TP-CU02-01 | CU02 Dashboard | Carga inicial de dashboard | KPI y gráficos visibles sin error crítico | PENDIENTE |
| TP-CU02-02 | CU02 Dashboard | Aplicar filtros por fecha y región | Datos se refrescan según filtro | PENDIENTE |
| TP-CU03-01 | CU03 Mapa tiempo real | Carga de capa de alertas en mapa | Mapa despliega eventos de alerta | PENDIENTE |
| TP-CU03-02 | CU03 Mapa tiempo real | Verificación de actualización temporal | Datos se actualizan dentro de ventana definida | BLOQUEADA |
| TP-CU03-03 | CU03 Mapa tiempo real | Falla API externa | Mensaje de contingencia visible | PENDIENTE |
| TP-CU04-01 | CU04 Registro | Registro con datos válidos | Cuenta creada y sesión iniciada/habilitada según flujo | PENDIENTE |
| TP-CU04-02 | CU04 Registro | Registro con email duplicado | Error de válidación de correo existente | PENDIENTE |
| TP-CU05-01 | CU05 Recuperar contraseña | Solicitud de recuperacion con email valido | Sistema procesa solicitud y genera token/enlace según política | PENDIENTE |
| TP-CU05-02 | CU05 Recuperar contraseña | Reset con token valido | contraseña actualizada | PENDIENTE |
| TP-CU05-03 | CU05 Recuperar contraseña | Token expirado/invalido | Mensaje de error y reintento | PENDIENTE |
| TP-CU06-01 | CU06 métricas históricas | Consulta de tendencia histórica | Grafico y serie retornan datos coherentes | PENDIENTE |
| TP-CU07-01 | CU07 Zonas de riesgo | Consulta de zonas con criticidad | Vista territorial destaca zonas de riesgo | BLOQUEADA |
| TP-CU08-01 | CU08 Configurar alertas | Crear regla de alerta válida | Regla persistida y visible en listado | PENDIENTE |
| TP-CU08-02 | CU08 Configurar alertas | Editar umbral de regla existente | Regla actualizada correctamente | PENDIENTE |
| TP-CU09-01 | CU09 Recibir notificaciones | Disparo de alerta sobre umbral | Se emite notificacion por canal definido | BLOQUEADA |
| TP-CU10-01 | CU10 Historial alertas | Listado histórico de alertas | Se visualizan alertas registradas | PENDIENTE |
| TP-CU10-02 | CU10 Historial alertas | Filtro por región/fecha | Historial se acota según filtro | PENDIENTE |
| TP-CU11-01 | CU11 integración API externa | Ingesta de datos desde API | Datos almacenados en BD sin error de integridad | PENDIENTE |
| TP-CU11-02 | CU11 integración API externa | válidación de normalizacion | Campos normalizados según contrato interno | BLOQUEADA |
| TP-CU12-01 | CU12 Editar perfil | Actualizar datos de perfil | Cambios persisten y se reflejan en sesión | BLOQUEADA |
| TP-CU13-01 | CU13 Cambiar contraseña | Cambio autenticado (actual + nueva) | Password actualizada y login posterior exitoso | BLOQUEADA |
| TP-CU14-01 | CU14 Actualizar datos personales | Edicion de datos personales extendidos | Datos actualizados con validaciones | BLOQUEADA |
| TP-CU15-01 | CU15 Gestiónar usuarios | Admin lista/crea/edita/elimina usuarios | Operaciones permitidas por rol administrador | BLOQUEADA |

## 5. Priorizacion para cierre proximo

Prioridad alta (para convertir parciales en completos):

- CU03: definir criterio operativo de "tiempo real" (periodicidad, fuente, refresh y fallback).
- CU07: exponer consulta formal de zonas de riesgo con regla documentada.
- CU09: definir canal de notificacion y ejecutar prueba end-to-end.
- CU11: cerrar normalizacion y política temporal de ingesta.

Prioridad estructural:

- CU15: definir RBAC (roles/permisos) y Gestión de usuarios por perfil.

## 6. Trazabilidad con estado de avance

Este plan se alinea con:

- `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md`

Se debe mantener ambos documentos sincronizados en cada iteración.


