# Matriz de Casos de Uso - Semana 10 (SIMFAT)

Fecha de corte: 2026-05-11  
Fuente funcional: `casos de uso SIMFAT PLANTILLA.docx`  
Fuente t?cnica: repositorio `simfat-main` (frontend + backend)

## Criterio de estado

- Completo: flujo funcional implementado y disponible para uso dentro del sistema.
- Parcial: existe implementacion relevante (l?gica, endpoints, UI o integraci?n), pero faltan piezas para cierre funcional.
- No iniciado: no existe implementacion suficiente para sostener el caso de uso como capacidad del sistema.

## Matriz CU

| CU | Nombre | Estado | Evidencia t?cnica actual | Brecha principal para cierre |
|---|---|---|---|---|
| CU01 | Iniciar sesi?n | Completo | Backend de autenticaci?n en `AuthController` (`/api/auth/login`, `/api/auth/me`, `/api/auth/refresh`) y rutas protegidas en frontend (`/login`, `ProtectedRoute`). | Sin brecha critica para el objetivo del CU. |
| CU02 | Visualizar Dashboard estadistico | Completo | Endpoints de dashboard (`/api/dashboard/*`) y vista analitica en `DashboardPage` integrada en `TerritoryPage`. | Sin brecha critica para el objetivo del CU. |
| CU03 | Visualizar mapa de incendios en tiempo real | Parcial | Existe m?dulo de alertas y mapa operativo (`AlertsPage`, `AlertsOperationalMap`) con backend (`/api/alerts`, `/api/alerts/map`). | Falta garantizar visualizaci?n en tiempo real operativa (frecuencia/refresh/validaci?n end-to-end en vivo). |
| CU04 | Registrarse | Completo | Registro implementado en backend (`/api/auth/register`) y flujo frontend (`/register`). | Sin brecha critica para el objetivo del CU. |
| CU05 | Recuperar contrase?a | Completo | Flujo forgot/reset implementado (`/api/auth/forgot-password`, `/api/auth/reset-password`) con vistas `/forgot-password` y `/reset-password`. | Sin brecha critica para el objetivo del CU. |
| CU06 | Consultar m?tricas historicas | Completo | Consultas historicas disponibles en endpoints de dashboard (`loss-trend`, `series`, `summary`) y gr?ficos en frontend. | Sin brecha critica para el objetivo del CU. |
| CU07 | Consultar zonas de riesgo | Parcial | Existen logicas relacionadas (criticidad, umbrales, capas territoriales, alertas) y visualizaci?n parcial. | Falta consolidar consulta expl?cita de zonas de riesgo como capacidad cerrada y validada funcionalmente. |
| CU08 | Configurar alertas | Completo | CRUD de reglas en backend (`/api/rules`) y m?dulo admin en frontend (`/admin/rules`). | Sin brecha critica para el objetivo del CU. |
| CU09 | Recibir notificaciones | Parcial | Hay l?gica y m?dulos asociados a alertas, pero sin notificacion operativa validada. | Falta motor/canal de notificacion (envio real) y pruebas funcionales de entrega. |
| CU10 | Consultar historial de alertas | Completo | Consulta y filtrado de alertas (`/api/alerts`, `/api/alerts/region/{id}`) y tabla/historial en `AlertsPage`. | Sin brecha critica para el objetivo del CU. |
| CU11 | Integrar datos desde API externa | Parcial | Integraci?n e ingesta implementadas (`OpenEoIngestController`, servicios OpenEO/NASA, persistencia en BD). | Falta normalizacion final de datos y definici?n formal de ventana temporal de obtencion (per?odo/dia especifico). |
| CU12 | Editar perfil | Parcial | Existe base de usuario autenticado (`/api/auth/me`) y estructura de auth. | Falta flujo completo de edicion de perfil (UI + endpoint dedicado + validaciones). |
| CU13 | Cambiar contrase?a | Parcial | Existe reset de contrase?a; hay base de seguridad y auth. | Falta cambio de contrase?a autenticado desde perfil (actual + nueva + reglas). |
| CU14 | Actualizar datos personales | No iniciado | No hay evidencia clara de m?dulo/endpoint especifico para actualizar datos personales de usuario. | Definir modelo de datos de perfil y exponer flujo completo de actualizacion. |
| CU15 | Gestionar usuarios | No iniciado | No existe gesti?n de usuarios completa con separacion de roles consolidada (RBAC). | Definir y aplicar roles/permisos en backend+frontend y habilitar administraci?n de usuarios. |

## Notas de alcance

- Esta matriz refleja estado real de desarrollo a semana 10, priorizando criterio operativo sobre mera existencia de c?digo.
- CU03, CU07, CU09, CU11 y CU15 fueron ajustados explicitamente segun validaci?n funcional del equipo.
- La matriz esta orientada a control acad?mico: traza implementacion actual y deja visible la brecha concreta de cierre.
