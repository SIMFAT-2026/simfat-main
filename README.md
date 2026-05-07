# SIMFAT Monorepo

Repositorio monorepo del proyecto **SIMFAT** (Sistema Inteligente de Monitorizacion Forestal y Alerta Temprana), organizado para cumplimiento academico de Duoc UC y continuidad profesional de desarrollo/despliegue.

## Estructura Duoc

- `Gestion/`: identificacion del equipo y del proyecto.
- `Producto/`: codigo fuente, scripts tecnicos, dependencias y entregables de producto.
- `Documentacion/`: UML, MER, wireframes, informes, gantt, evidencias y analisis.

## Estructura tecnica actual

- `Producto/backend/simfat-backend`: backend principal Spring Boot.
- `Producto/backend/openeo-service`: microservicio openEO (FastAPI).
- `Producto/frontend/simfat-web`: frontend SPA (React + Vite).
- `Producto/database/sql`: scripts SQL.
- `Producto/database/nosql`: scripts NoSQL.
- `Producto/database/plsql`: scripts PL/SQL.

## Despliegue futuro (Railway)

Configurar cada servicio con su **Root Directory**:

- Backend: `Producto/backend/simfat-backend`
- OpenEO Service: `Producto/backend/openeo-service`
- Frontend: `Producto/frontend/simfat-web`

## Nota de migracion

La reorganizacion se realizo con `git mv` para preservar trazabilidad de historial y mantener integridad del monorepo.
