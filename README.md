# SIMFAT

## Sistema Inteligente de Monitorización Forestal y Alerta Temprana

SIMFAT es un proyecto académico-profesional orientado al monitoreo territorial, análisis de indicadores ambientales y soporte a la toma de decisiones para prevención y alerta temprana en contexto forestal.

Este repositorio está organizado como **monorepo**, con una estructura técnica modular y, al mismo tiempo, alineada con la pauta académica de Taller de Programación.

## Estructura General del Repositorio

- `Gestion/`: antecedentes de identificación del proyecto y del equipo.
- `Producto/`: código fuente, servicios, frontend, scripts de base de datos y dependencias técnicas.
- `Documentacion/`: documentación de ingeniería, diseño, evidencias y control de avance.

## Arquitectura Técnica (Monorepo)

### Backend principal
- Ruta: `Producto/backend/simfat-backend`
- Tecnología: Spring Boot
- Responsabilidad: reglas de negocio, autenticación, APIs principales y orquestación de integración.

### Microservicio OpenEO
- Ruta: `Producto/backend/openeo-service`
- Tecnología: FastAPI
- Responsabilidad: integración especializada con OpenEO/Copernicus para procesamiento y consulta de indicadores.

### Frontend web
- Ruta: `Producto/frontend/simfat-web`
- Tecnología: React + Vite
- Responsabilidad: interfaz de usuario, visualización de datos y consumo de APIs del backend.

### Scripts y recursos de base de datos
- Ruta base: `Producto/database/`
- Subcarpetas:
  - `sql/`
  - `nosql/`
  - `plsql/`

## Estructura Académica de Documentación

La carpeta `Documentacion/` centraliza los entregables de ingeniería y evidencia del proyecto, incluyendo:

- `UML/`: arquitectura y diagramas de diseño.
- `MER/`: modelo entidad-relación y modelo lógico.
- `Wireframes/`: diseño de interfaces.
- `Analisis-Problemas/`: análisis técnico y funcional.
- `Gantt/`: planificación y control temporal.
- `Informes/`: reportes técnicos y de avance.
- `Evidencias/`: evidencia de pruebas y validaciones.
- `Presentaciones/`: material para revisión y defensa.

## Ejecución Local (Referencia Rápida)

### Backend principal
1. Ir a `Producto/backend/simfat-backend`
2. Configurar variables de entorno del servicio
3. Ejecutar:
```bash
mvn spring-boot:run
```

### OpenEO service
1. Ir a `Producto/backend/openeo-service`
2. Configurar variables de entorno del servicio
3. Ejecutar:
```bash
uvicorn app.main:app --reload --port 8000
```

### Frontend
1. Ir a `Producto/frontend/simfat-web`
2. Instalar dependencias:
```bash
npm install
```
3. Ejecutar:
```bash
npm run dev
```

## Despliegue por Servicio (Railway)

Para despliegues por subdirectorio, configurar el **Root Directory** de cada servicio:

- Backend: `Producto/backend/simfat-backend`
- OpenEO service: `Producto/backend/openeo-service`
- Frontend: `Producto/frontend/simfat-web`

## Observación de Trazabilidad

La reorganización del monorepo se realizó preservando historial Git y trazabilidad de cambios, manteniendo la modularidad técnica de los servicios y la compatibilidad con la estructura académica solicitada.
