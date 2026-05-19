# Informe Estado de Avance 2 - Semana 10

## 1. Portada

**Asignatura:** TPY1101 - Taller Aplicado de Programacion  
**Evaluacion:** Parcial N 2 - Estado de avance 2  
**Proyecto:** SIMFAT (Sistema Integrado de Monitoreo y Alerta Temprana Forestal)  
**Equipo:** SIMFAT-2026  
**Fecha:** 2026-05-19  
**Version:** 1.0

---

## 2. Indice

1. Portada  
2. Indice  
3. Introduccion  
4. Resumen de avance evaluacion parcial 1  
5. Desarrollo evaluacion parcial 2  
6. Estado actual del producto  
7. Pruebas ejecutadas y aseguramiento de calidad  
8. Configuracion de ambientes y despliegue  
9. Estado MVP online y enlaces operativos  
10. Costos y sostenibilidad inicial de plataforma  
11. Respaldo y operacion de base de datos  
12. Conclusiones  
13. Lecciones aprendidas  
14. Anexos y evidencias

---

## 3. Introduccion

Este informe consolida el estado de avance tecnico y funcional de SIMFAT correspondiente a la Evaluacion Parcial 2.  
El foco principal del periodo fue fortalecer seguridad, gobernanza de accesos y capacidad operativa del sistema mediante:

- Implementacion de RBAC (control de acceso por roles/permisos).
- Integracion JWT con autorizacion data-driven.
- Endurecimiento de endpoints criticos por minimo privilegio.
- Auditoria de acciones privilegiadas.
- Habilitacion y validacion de contratos API por Swagger/OpenAPI.
- Estabilizacion de ambientes demo (staging/produccion).

El documento presenta evidencia de implementacion, ambientes de prueba, criterios QA y estado real de despliegue.
Adicionalmente, su estructura se alinea con los indicadores de logro evaluados en la pauta (IL2.1, IL2.2 e IL2.3),
integrando trazabilidad entre diseno, configuracion de ambientes, desarrollo efectivo y verificacion de resultados.

---

## 4. Resumen de avance evaluacion parcial 1

En la evaluacion parcial 1 el equipo avanzo en la construccion de la base funcional del sistema SIMFAT, levantando una arquitectura
de referencia que permitio evolucionar hacia seguridad y operacion en la parcial 2. En terminos practicos, la parcial 1 dejo resuelto:

1. Arquitectura general de solucion:
- Frontend web en React/Vite para operacion de usuarios finales.
- Backend en Spring Boot como API central.
- Servicio analitico complementario para integracion satelital.
- Persistencia dual: PostgreSQL para seguridad/datos relacionales y MongoDB para informacion operativa.

2. Integraciones y contratos iniciales:
- Definicion de endpoints base de autenticacion y modulos funcionales.
- Conexion operativa a PostgreSQL y MongoDB.
- Estandar de respuesta API mediante `ApiResponse`.

3. Fundacion de seguridad:
- Flujo JWT inicial (login/sesion) ya implementado.
- Estructura de codigo apta para evolucionar a autorizacion fina.

4. Trazabilidad documental:
- Organizacion de documentacion tecnica (UML, MER, informes, evidencias).
- Base de casos de uso y lineamientos de calidad.

Como continuidad natural, la parcial 2 se enfoco en fortalecer madurez tecnica y control de riesgo:
seguridad RBAC/JWT, endurecimiento de endpoints, auditoria y validacion formal de ambientes.

Referencia:
- [README principal](/c:/Users/Lenovo/Documents/GitHub/simfat-main/README.md)

---

## 5. Desarrollo evaluacion parcial 2

### 5.1 Elaboracion de documentos y diagramas (IL2.1)

La elaboracion documental en esta etapa no se limito a registro, sino que se utilizo como mecanismo de diseno y gobierno tecnico.
Se construyeron artefactos que permiten justificar decisiones, reducir ambiguedad y alinear implementacion con criterios evaluativos.

Entregables principales generados/actualizados:

1. Contrato tecnico de seguridad RBAC/JWT:
- Define roles oficiales, jerarquia funcional y convenciones.
- Establece separacion entre autenticacion (JWT) y autorizacion (RBAC en BD).
- Fija principio de minimo privilegio y plan incremental por fases.

2. Documentos de ejecucion:
- Definition of Done (DoD) de seguridad.
- Plan incremental de implementacion.
- Estimacion de esfuerzo por fase.

3. Diagramas de soporte:
- UML de casos de uso por rol (vista funcional).
- UML de clases para seguridad (vista estructural).
- MER integrado con tablas/colecciones de RBAC y verificacion.

4. Documentacion de control:
- Matriz de casos de uso actualizada por rol/estado.
- Plan de pruebas y checklist QA checkeable.
- Evidencias de pruebas de seguridad y OpenAPI.

Resultado de esta linea:
- Se cuenta con lineamientos claros para construir, probar y defender tecnicamente la solucion.
- Existe trazabilidad entre problema, diseño, implementacion y validacion.
- Se evidencia cumplimiento del IL2.1 al disponer documentos pertinentes, coherentes y aplicados al desarrollo efectivo del producto.

Referencias principales:
- [Contrato RBAC/JWT](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/2026-05-14_fase0_rbac_jwt_contrato_arquitectura_v1.md)
- [Arquitectura seguridad RBAC/JWT](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/UML/2026-05-14_arquitectura_seguridad_rbac_jwt_backend_v1.md)
- [UML roles RBAC](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/UML/UML-Casos-de-Uso-Roles-RBAC-Semana10.md)
- [MER integrado RBAC](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/MER/MER-Integrado-RBAC-Semana10.md)

### 5.2 Configuracion de ambiente de pruebas (IL2.2)

Se habilito un esquema de ambientes orientado a continuidad operativa, validacion incremental y baja friccion de despliegue.
La estrategia fue probar local con datos remotos para detectar integracion real y luego estabilizar ambientes cloud.

Se definieron tres capas de prueba:

1. **Local integrado**
- Frontend: `localhost:4173`
- Backend: `localhost:8081`
- OpenEO service: `localhost:8000`
- DB remotas: Supabase (PostgreSQL) y MongoDB Atlas

2. **Staging online**
- Backend staging operativo con Swagger:
  - `/swagger-ui/index.html`
  - `/v3/api-docs`

3. **Produccion online**
- Backend y servicio OpenEO desplegados en Railway.
- Frontend desplegado en Vercel con variables por entorno.

Aspectos de configuracion y buenas practicas aplicadas:

- Parametrizacion por variables de entorno para desacoplar codigo y secretos.
- Configuracion de CORS para frontends autorizados.
- Publicacion de contratos API por OpenAPI/Swagger para validacion cruzada con QA.
- Uso de staging como entorno de contingencia en caso de inestabilidad parcial de produccion.

Validaciones realizadas sobre ambiente:

- Disponibilidad de endpoints criticos (`/api/auth/*`, `/api/regions`, `/api/community/*`).
- Disponibilidad de contratos (`/v3/api-docs`, `/swagger-ui/index.html`).
- Pruebas de login y acceso por rol en entorno online.

Resultado IL2.2:

- Se dispone de un ambiente de pruebas funcional y replicable, con configuracion documentada y evidencia de operacion
  tanto en entorno local como en plataformas remotas.

Referencia:
- [Configuracion servidores y despliegue](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/Configuracion-Servidores-Cloud-y-Despliegue.md)
- [Plan de pruebas actualizado con ambientes](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/Plan-de-Pruebas-Semana10-DUOC.md)

### 5.3 Desarrollo de solucion funcional, segura y de calidad (IL2.3)

La implementacion se ejecuto de forma incremental para evitar quiebres funcionales y mantener continuidad del producto.
Se priorizo una arquitectura de seguridad data-driven: roles y permisos definidos en base de datos, no hardcodeados.

Implementaciones clave realizadas:

- Modelo RBAC en BD: `roles`, `permissions`, `role_permissions`, `user_roles`.
- Capa de verificacion: `user_verification`, `verification_events`.
- Resolucion de authorities desde BD en flujo JWT.
- Proteccion de endpoints criticos con `@PreAuthorize`.
- Auditoria de acciones privilegiadas.
- Panel de control de accesos en frontend.

Endurecimiento aplicado (resumen funcional):

1. Endpoints de administracion y escritura critica protegidos por permiso/rol.
2. Separacion de perfiles operativos:
- comunitario
- verificado
- moderador
- admin
- super admin
3. Persistencia de roles efectiva y visualizacion en panel de accesos.
4. Validacion de comportamiento esperado frente a token invalido (`401`) y falta de permisos (`403`).

Complementos operativos:

- Swagger operativo para consumo tecnico y validacion de contratos.
- Pruebas de integracion de seguridad ejecutadas y documentadas.
- Usuarios demo para pruebas funcionales con rol admin.

Resultado IL2.3:

- La solucion implementada evidencia funcionalidad, seguridad y calidad tecnica mediante controles de autorizacion por rol/permiso,
  cobertura de pruebas criticas y disponibilidad de contratos API verificables.

Adicionalmente se dejaron usuarios demo con rol admin para pruebas de negocio:

- `jennifer@aifbn.cl`
- `pablo@aifbn.cl`

Referencia:
- [Evidencia inyeccion usuarios demo AIFBN](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/2026-05-18_inyeccion_usuarios_demo_aifbn.md)

---

## 6. Estado actual del producto

### 6.1 Backend

- Seguridad RBAC/JWT activa.
- Endpoints administrativos protegidos.
- Swagger/OpenAPI habilitado y validado.
- Integracion con PostgreSQL + MongoDB en operacion.

Detalle de estado:

- RBAC persistente aplicado a nivel de datos y seguridad de endpoints.
- Estructura de autorizacion preparada para escalar nuevos permisos por modulo.
- Flujo de autenticacion completo con login/refresh/logout/me.
- Mecanismo de auditoria de acciones privilegiadas integrado.

### 6.2 Frontend

- Flujo de autenticacion funcional.
- Panel de control de accesos operativo (perfil predefinido + switch verificado + ajustes avanzados).
- Modulos principales navegables con fallback controlado en caso de indisponibilidad parcial.

Detalle de estado:

- Integracion por `axiosClient` con manejo de token y refresh.
- Control de accesos utilizable para gestion de usuarios/roles en operacion.
- Mensajeria de continuidad ante falla de backend en modulos no criticos.

### 6.3 Integraciones

- `simfat-backend` y `openeo-service` desplegados en cloud.
- Frontend en Vercel con conexion por variable `VITE_API_URL`.

Detalle de estado:

- Integracion backend-frontend validada en entornos remotos.
- Integracion backend-openeo en estado operativo, sujeta a control de carga y estabilidad de servicio.
- Esquema de ramas/ambientes ordenado para continuidad de desarrollo.

### 6.4 MVP en produccion (habilitado)

Actualmente el proyecto cuenta con un MVP online accesible para demostracion y validacion externa.
El objetivo de este MVP es evidenciar funcionamiento end-to-end de autenticacion, navegacion y acceso a modulos
principales, con soporte de backend remoto y documentacion de contratos API.

Alcance del MVP online:

- Frontend operativo en Vercel.
- Backend productivo operativo en Railway.
- Servicio OpenEO operativo en Railway como componente de integracion.
- Swagger/OpenAPI disponible para validacion tecnica.

---

## 7. Pruebas ejecutadas y aseguramiento de calidad

Se ejecuto una bateria de validacion enfocada en seguridad y disponibilidad de contratos, complementada con verificacion funcional del panel de accesos.
La estrategia QA utilizada fue incremental: primero prueba tecnica automatizada, luego validacion funcional y finalmente evidencia documental.

Pruebas tecnicas ejecutadas:

- `SecurityAuthorizationIntegrationTest`
- `OpenApiSwaggerIntegrationTest`

Resultados:

- Pruebas de seguridad y disponibilidad de contratos en estado PASS.
- Endpoints clave de OpenAPI accesibles.
- Flujo principal de panel de accesos verificado.

Cobertura funcional alcanzada:

1. Seguridad:
- endpoint protegido sin auth -> bloqueado
- endpoint protegido con permiso insuficiente -> bloqueado
- endpoint protegido con permiso correcto -> permitido

2. Contratos API:
- OpenAPI accesible
- Swagger UI accesible
- metadatos y rutas disponibles para QA/consumo tecnico

3. UI operativa:
- carga de usuarios/roles/permisos
- cambios de perfil y guardado de roles
- visualizacion de roles efectivos

Lectura de calidad:

- El sistema alcanza un estado de calidad aceptable-alto para la etapa, con foco en seguridad y trazabilidad.
- Los riesgos residuales identificados no invalidan la funcionalidad principal y cuentan con plan de mitigacion operativo.

Referencias:
- [Checklist QA Semana 10](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/Checklist-QA-Semana10-DUOC.md)
- [Evidencias QA E2E y Swagger](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/Evidencias-QA-E2E-y-Swagger-Semana10.md)
- [Plan de pruebas Semana 10](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/Plan-de-Pruebas-Semana10-DUOC.md)

Nota:
- La evidencia visual QA (capturas) se completa de manera manual por el equipo durante la ejecucion final de pruebas.

---

## 8. Configuracion de ambientes y despliegue

Se trabajaron ambientes separados para control de riesgo:

- **Staging:** priorizado para demo estable.
- **Produccion:** habilitado con monitoreo de estabilidad.

Mejoras aplicadas:

- Reconfiguracion de ramas `develop/*` y `main` como base estable.
- Ajuste de variables de entorno en Railway y Vercel.
- Correccion de CORS y URL de backend para frontend.

Racional tecnico de despliegue:

1. `main` como base estable para release.
2. ramas `develop/*` para trabajo por dominio (backend, frontend, openeo-service).
3. staging como entorno recomendado para presentaciones cuando produccion presenta eventos transitorios.

Riesgos observados y mitigacion:

- Evento de memoria en backend de produccion (OOM) observado en ciclo de estabilizacion.
- Mitigacion aplicada: priorizar staging estable para continuidad de pruebas/demo y ajustar configuracion de servicio.

---

## 9. Estado MVP online y enlaces operativos

Al cierre de este avance, se dispone de enlaces remotos operativos para validacion funcional y presentacion:

1. Frontend (Vercel):
- https://simfat-web-stg.vercel.app/

2. Backend (Railway):
- https://simfat-backend-production.up.railway.app

3. OpenEO service (Railway):
- https://openeo-service-production-production.up.railway.app

4. Contratos API (Swagger/OpenAPI):
- `https://simfat-backend-production.up.railway.app/swagger-ui/index.html`
- `https://simfat-backend-production.up.railway.app/v3/api-docs`

Observacion operativa:

- Para continuidad de demo, se mantiene estrategia de fallback a staging ante eventual inestabilidad puntual de produccion.
- La existencia de MVP online demuestra capacidad de despliegue real y operacion fuera del entorno local.

---

## 10. Costos y sostenibilidad inicial de plataforma

### 10.1 Estado actual de costos

Durante esta etapa el proyecto se ha ejecutado mayoritariamente en capas gratuitas de servicios cloud:

- Vercel (frontend): uso base sin costo en etapa academica/prototipo.
- Railway (backend + openeo-service): uso inicial en modalidad gratuita o creditos de arranque.
- Supabase/Atlas: uso en rango de consumo bajo para desarrollo y pruebas.

### 10.2 Riesgo de costo proximo

Se identifica que Railway comenzara a aplicar cobros al superar umbrales del plan gratuito (ejecucion continua, consumo,
almacenamiento o transferencia). Esto impacta la sostenibilidad del MVP si se mantiene en operacion permanente.

### 10.3 Linea de decision propuesta

Se proponen dos caminos para el siguiente ciclo:

1. Mantener Railway con plan de costo bajo:
- Ventaja: continuidad inmediata sin migracion tecnica.
- Riesgo: costo recurrente mensual.

2. Migrar servicios backend/openeo a alternativa de menor costo:
- Ventaja: reducir gasto operativo fijo.
- Riesgo: esfuerzo de migracion y ventana de estabilizacion.

### 10.4 Recomendacion del equipo

Para el corto plazo (presentacion y cierre de hito academico), se recomienda mantener infraestructura actual por estabilidad.
Para el mediano plazo, evaluar costo real mensual y comparar con opcion de migracion controlada.

Implicancia academica y de gestion:

- Este analisis permite demostrar criterio tecnico-economico sobre continuidad del producto, incorporando sostenibilidad
  como variable de decision y no solo la factibilidad de implementacion.

---

## 11. Respaldo y operacion de base de datos

Se definieron procedimientos y scripts de soporte para respaldo/operacion:

- Script de backup DB (PowerShell).
- Script de rollback.
- Script de reseed y carga de datos.

Aplicacion practica durante el avance:

- Se documentaron scripts que permiten repetir tareas operativas en entorno controlado.
- Se ejecuto inyeccion de usuarios de prueba con trazabilidad tecnica para QA funcional por rol.
- Se mantuvo separacion entre datos de demo y logica de negocio para evitar impacto en codigo fuente.

Resultado:

- El equipo dispone de procedimientos concretos para continuidad operativa, soporte de pruebas y preparacion de demo.

Referencias:
- [Scripts DB y utilitarios backend](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Producto/backend/simfat-backend/scripts)
- [Checklist de despliegue tecnico](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/Configuracion-Servidores-Cloud-y-Despliegue.md)

---

## 12. Conclusiones

1. El proyecto cumple un avance tecnico significativo para la etapa parcial 2, especialmente en seguridad y gobernanza de accesos.
2. Se cuenta con implementacion real de RBAC/JWT, contratos API verificables y evidencia de QA tecnico.
3. El sistema se encuentra operativo en ambiente demo y MVP online en plataformas remotas (Vercel + Railway).
4. Las brechas remanentes son de afinamiento operativo y evidencia visual final, no de ausencia de arquitectura o base funcional.
5. El enfoque incremental permitio avanzar sin detener operacion, manteniendo entregables trazables para defensa academica y tecnica.
6. Se identifica oportunamente la necesidad de definir estrategia de costos para continuidad post-hito academico.

Sintesis evaluativa:

- El estado de avance presentado es consistente con los requerimientos de la Evaluacion Parcial 2 y demuestra progresion
  concreta desde el diseno hacia una solucion operativa, medible y defendible tecnicamente.

---

## 13. Lecciones aprendidas

1. En monorepo, la definicion temprana de ramas y root directories evita errores de despliegue.
2. Documentar ambiente de pruebas con detalle tecnico facilita defensa y trazabilidad evaluativa.
3. Separar staging y produccion reduce impacto de incidentes transitorios (ej. OOM).
4. RBAC data-driven permite evolucionar permisos sin redisenar autenticacion.
5. La evidencia tecnica (comandos, resultados, contratos, scripts) acelera tanto QA como toma de decisiones en reuniones con stakeholders.
6. La preparacion anticipada de informe, entorno y evidencia reduce el riesgo academico en hitos con defensa oral.

---

## 14. Anexos y evidencias

### 14.1 Documentacion tecnica

- [Entrega Semana 10 - Cumplimiento Rubrica](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/Entrega-Semana10-Cumplimiento-Rubrica-DUOC.md)
- [Patrones y decisiones tecnicas](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/Patrones-de-Diseno-y-Decisiones-Tecnicas.md)
- [Detalles integracion servicios/APIs](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/Detalles-Integracion-Servicios-y-APIs.md)

### 14.2 QA y pruebas

- [Checklist QA Semana 10](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/Checklist-QA-Semana10-DUOC.md)
- [Plan de pruebas Semana 10](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/Plan-de-Pruebas-Semana10-DUOC.md)
- [Evidencias QA E2E y Swagger](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Evidencias/Evidencias-QA-E2E-y-Swagger-Semana10.md)

### 14.3 Seguridad RBAC

- [Contrato RBAC/JWT](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/2026-05-14_fase0_rbac_jwt_contrato_arquitectura_v1.md)
- [DoD RBAC](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/2026-05-14_fase0_rbac_definition_of_done_v1.md)
- [Plan incremental RBAC](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/Informes/2026-05-14_fase0_rbac_plan_incremental_v1.md)

### 14.4 Diagramas

- [UML casos de uso roles RBAC](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/UML/UML-Casos-de-Uso-Roles-RBAC-Semana10.md)
- [UML clases seguridad RBAC/JWT](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/UML/UML-Diagrama-de-Clases-RBAC-Seguridad-Semana10.md)
- [MER integrado semana 10](/c:/Users/Lenovo/Documents/GitHub/simfat-main/Documentacion/MER/MER-Integrado-RBAC-Semana10.md)
