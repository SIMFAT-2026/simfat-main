# SIMFAT

## Sistema Inteligente de Monitorizacion Forestal y Alerta Temprana

SIMFAT es un proyecto academico-profesional orientado al monitoreo territorial, analisis de indicadores ambientales y soporte a la toma de decisiones para prevencion y alerta temprana en contexto forestal.

Este repositorio está organizado como **monorepo**, alineado con las exigencias de la pauta academica.

## Estructura General

- `Gestion/`: antecedentes de identificacion del proyecto y del equipo.
- `Producto/`: codigo fuente, scripts de base de datos y dependencias tecnicas.
- `Documentacion/`: diseno tecnico/visual, planificacion, informes y evidencias QA.

## Arquitectura Tecnica

### Backend principal
- Ruta: `Producto/backend/simfat-backend`
- Tecnologia: Spring Boot
- Proposito: autenticacion, reglas de negocio y APIs principales.

### Microservicio OpenEO
- Ruta: `Producto/backend/openeo-service`
- Tecnologia: FastAPI
- Proposito: integracion OpenEO/Copernicus para consulta e ingesta de indicadores.

### Frontend web
- Ruta: `Producto/frontend/simfat-web`
- Tecnologia: React + Vite
- Proposito: interfaz de usuario y visualizacion de datos.

## Documentacion (con accesos directos)

### Gestion
- [Documento 1.1.2 de definicion e identificacion](Gestion/1.1.2%20Documento%20de%20registro%20de%20definicion%20e%20identificacion%20del%20proyecto.docx)
- [Integrantes](Gestion/Integrantes.txt)

### Informes de avance y control
- [Matriz de casos de uso - Semana 10](Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md)
- [Auditoria de entrega - Semana 10](Documentacion/Informes/auditoria-entrega-semana10.md)
- [Estado de avance integrado](Documentacion/Informes/estado-avance-integrado-simfat-2026-04-14.md)

### QA y pruebas
- [Plan de pruebas alineado a CU01-CU15](Documentacion/Evidencias/plan-pruebas-cu01-cu15.md)
- [Checklist QA alineado a CU01-CU15](Documentacion/Evidencias/checklist-qa-cu01-cu15.md)
- [Evidencias QA backend](Documentacion/Evidencias/qa-evidencias-iteracion-backend-2026-04-21.md)
- [Evidencias QA frontend](Documentacion/Evidencias/qa-evidencias-iteracion-frontend-2026-04-22.md)

### Diseno tecnico y visual
- [UML / Arquitectura](Documentacion/UML/)
- [MER / Modelo logico](Documentacion/MER/)
- [Wireframes funcionales](Documentacion/Wireframes/wireframes-funcionales-cu-prioritarios.md)
- [Analisis de problemas / roadmap](Documentacion/Analisis-Problemas/roadmap_frontend_backend_simfat.md)

### Planificacion
- [Planificacion semana 10 a 12 (alineada a CU)](Documentacion/Gantt/planificacion-semana10-a-semana12-cu.md)

## Producto Tecnico

### Codigo fuente
- [Backend principal](Producto/backend/simfat-backend/)
- [Microservicio OpenEO](Producto/backend/openeo-service/)
- [Frontend web](Producto/frontend/simfat-web/)

### Base de datos
- [Esquema PostgreSQL](Producto/database/sql/init-postgres-schema.sql)
- [Datos de prueba PostgreSQL](Producto/database/sql/seed-postgres-test-data.sql)
- [Funciones/procedimientos PostgreSQL](Producto/database/plsql/postgresql_auth_helpers.sql)
- [Esquema MongoDB](Producto/database/nosql/init-mongodb-schema.js)

### Dependencias
- [Registro consolidado de dependencias](Producto/dependencias/registro-dependencias-consolidado.md)
- [Registro dependencias backend](Producto/dependencias/registro-dependencias-backend.md)
- Manifiestos por modulo:
  - `Producto/backend/simfat-backend/pom.xml`
  - `Producto/backend/openeo-service/requirements.txt`
  - `Producto/frontend/simfat-web/package.json`

## Ejecucion Local (rapida)

### Backend principal
```bash
cd Producto/backend/simfat-backend
mvn spring-boot:run
```

### Microservicio OpenEO
```bash
cd Producto/backend/openeo-service
uvicorn app.main:app --reload --port 8000
```

### Frontend
```bash
cd Producto/frontend/simfat-web
npm install
npm run dev
```
