# SIMFAT

## Sistema Integrado de Monitoreo y Alerta Temprana Forestal

SIMFAT es una plataforma web para el monitoreo territorial, análisis de riesgo de incendio forestal y coordinación comunitaria, desarrollada como proyecto de titulación de TPY1101 – Taller Aplicado de Programación, Duoc UC.

**Cliente:** AIFBN – Agrupación de Ingenieros Forestales por el Bosque Nativo

**Integrantes:** Andrés Ibáñez · David Vásquez

---

## Producción

| Servicio | URL |
|---|---|
| Frontend | https://simfat-web-stg.vercel.app/ |
| Backend API | https://simfat-backend-production.up.railway.app |
| Swagger UI | https://simfat-backend-production.up.railway.app/swagger-ui/index.html |
| Servicio OpenEO | https://openeo-service-production-production.up.railway.app |

---

## Estructura del repositorio

```
simfat-main/
├── Gestion/           — identificación del proyecto y del equipo
├── Producto/          — código fuente y scripts de base de datos
│   ├── backend/
│   │   ├── simfat-backend/     — API principal (Spring Boot, Java 21)
│   │   └── openeo-service/     — microservicio satelital (FastAPI, Python)
│   ├── frontend/
│   │   └── simfat-web/         — interfaz web (React 18 + Vite)
│   └── database/
│       ├── sql/                — esquema y seed PostgreSQL
│       └── nosql/              — inicialización MongoDB
└── Documentacion/     — toda la documentación académica y técnica
```

---

## Documentación EP3 (Entrega Final)

### Informe consolidado
- [Informe Consolidado EP3](Documentacion/Informe_consolidado_ea3.md) — informe completo de entrega final (EP1 + EP2 + EP3)

### Plan de pruebas y QA
- [Plan de Pruebas EP3 — 34 casos](Documentacion/Evidencias/2026-06-20_plan_pruebas_EP3.md)
- [Mejoras Implementadas EP3 — 9 mejoras con trazabilidad](Documentacion/Evidencias/2026-06-20_mejoras_EP3.md)

### Diseño técnico EP3
- [Diagrama de Clases EP3](Documentacion/UML/2026-06-20_diagrama_clases_ep3.md)
- [MER — Nuevas colecciones MongoDB EP3](Documentacion/MER/2026-06-20_nuevas_colecciones_EP3.md)
- [Guía de instalación y despliegue](Documentacion/2026-06-20_guia_instalacion_despliegue.md)

---

## Documentación EP2 (Semana 10)

### Informes de avance
- [Informe Estado de Avance 2 — Semana 10](Documentacion/Informes/Informe-Estado-Avance-2-Semana10-TPY1101.md)
- [Cumplimiento rúbrica — Semana 10](Documentacion/Informes/Entrega-Semana10-Cumplimiento-Rubrica-DUOC.md)
- [Matriz de casos de uso con roles](Documentacion/Informes/Matriz-Casos-de-Uso-Semana10-Roles-Actualizados.md)

### Arquitectura y seguridad
- [Contrato arquitectura RBAC/JWT](Documentacion/Informes/2026-05-14_fase0_rbac_jwt_contrato_arquitectura_v1.md)
- [UML arquitectura integrada](Documentacion/UML/Arquitectura-Integrada-Sistema-Semana10.md)
- [MER integrado RBAC](Documentacion/MER/MER-Integrado-RBAC-Semana10.md)
- [Configuración servidores cloud](Documentacion/Informes/Configuracion-Servidores-Cloud-y-Despliegue.md)

### QA y evidencias
- [Plan de pruebas — Semana 10](Documentacion/Evidencias/Plan-de-Pruebas-Semana10-DUOC.md)
- [Checklist QA — Semana 10](Documentacion/Evidencias/Checklist-QA-Semana10-DUOC.md)
- [Evidencias QA E2E y Swagger](Documentacion/Evidencias/Evidencias-QA-E2E-y-Swagger-Semana10.md)

---

## Documentación EP1

### Gestión
- [Documento de identificación del proyecto](Gestion/1.1.2%20Documento%20de%20registro%20de%20definicion%20e%20identificacion%20del%20proyecto.docx)
- [Integrantes del equipo](Gestion/Integrantes.txt)

### Planificación
- [Carta Gantt — Semana 10](Documentacion/Gantt/Carta-Gantt-Actualizada-Semana10.md)
- [Carta Gantt — Semana 14](Documentacion/Gantt/Carta-Gantt-Actualizada-Semana14.md)
- [Planificación semanas 10-12](Documentacion/Gantt/planificacion-semana10-a-semana12-cu.md)

---

## Documentación técnica del sistema

### Contratos de API
- [Contrato API completo v2](Documentacion/Informes/2026-06-02_contrato_api_completo_v2.md)
- [Contrato backend territorial](Documentacion/Informes/territory_backend_contract.md)
- [Contrato backend comunitario](Documentacion/Informes/community_backend_contract.md)

### Índice completo de documentación
- [Documentacion/README.md](Documentacion/README.md)

---

## Ejecución local

### Backend (Spring Boot)
```bash
cd Producto/backend/simfat-backend
mvn spring-boot:run
```

### Servicio OpenEO (FastAPI)
```bash
cd Producto/backend/openeo-service
uvicorn app.main:app --reload --port 8000
```

### Frontend (React + Vite)
```bash
cd Producto/frontend/simfat-web
npm install
npm run dev
```

### Stack completo (Docker Compose)
```bash
docker compose up --build -d
```

---

## Base de datos

| Script | Descripción |
|---|---|
| `Producto/database/sql/init-postgres-schema.sql` | Esquema PostgreSQL (Flyway lo aplica automáticamente) |
| `Producto/database/sql/seed-postgres-test-data.sql` | Datos de prueba PostgreSQL |
| `Producto/database/nosql/init-mongodb-schema.js` | Inicialización de colecciones MongoDB |

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Frontend | React 18 + Vite + JSX |
| Backend | Java 21 + Spring Boot 3 |
| Servicio satelital | Python 3.11 + FastAPI + OpenEO |
| BD relacional | PostgreSQL 15 (Supabase) |
| BD documental | MongoDB Atlas (M0) |
| Despliegue frontend | Vercel |
| Despliegue backend | Railway |
