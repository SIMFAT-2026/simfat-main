# Auditoría de Entrega - Semana 10

Fecha: 2026-05-11  
Proyecto: SIMFAT  
Criterio: pauta de estructura de repositorio TPY1101

## 1. Resultado general

- Gestión: CUMPLE
- Documentación: CUMPLE PARCIAL (con pendientes formales de informes y formato final)
- Producto: CUMPLE PARCIAL (scripts BD reforzados, revisar formato final de evidencia)

## 2. Revisión por carpeta

### 2.1 Gestión

Requerido:
- `1.1.2 Documento de registro de definición e identificación del proyecto.docx`
- `Integrantes.txt` con tres integrantes.

Estado:
- Documento 1.1.2: presente.
- Integrantes: presente, pero actualmente contiene 2 líneas visibles.  
  Acción: confirmar y agregar el tercer integrante antes de entrega.

### 2.2 Documentación

Requerido:
- Informes de avance N1, N2, N3.
- Diagramas técnicos (MER/UML/arquitectura/casos de uso).
- Diseño de interfaz (wireframes/mockups).
- Planificación (Carta Gantt actualizada).
- QA y plan de pruebas actualizados.

Estado actual:
- Informes: existen múltiples informes técnicos; falta consolidación explícita de N1/N2/N3.
- Diagramas técnicos: hay carpeta UML y MER con contenido.
- Wireframes/mockups: agregado documento de wireframes funcionales de baja fidelidad.
- Planificación: agregado documento de planificación semana 10 a 12 alineado a CU.
- QA/Pruebas: agregados plan de pruebas y checklist QA alineados a CU01-CU15.

Documentos agregados en esta revisión:
- `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md`
- `Documentacion/Evidencias/plan-pruebas-cu01-cu15.md`
- `Documentacion/Evidencias/checklist-qa-cu01-cu15.md`
- `Documentacion/Gantt/planificacion-semana10-a-semana12-cu.md`
- `Documentacion/Wireframes/wireframes-funcionales-cu-prioritarios.md`

### 2.3 Producto

Requerido:
- código fuente.
- Scripts BD (tablas, procedimientos y datos de prueba).
- Librerías/dependencias.

Estado actual:
- Código fuente: presente (frontend, backend, microservicio).
- Scripts BD:
  - Existían scripts de esquema SQL y Mongo.
  - Se agrega seed de datos de prueba.
  - Se agrega archivo con función/procedimiento PostgreSQL.
- Dependencias:
  - `pom.xml` (Java), `requirements.txt` (Python), `package.json` + `package-lock.json` (Frontend) presentes.

Scripts agregados en esta revisión:
- `Producto/database/sql/seed-postgres-test-data.sql`
- `Producto/database/plsql/postgresql_auth_helpers.sql`

## 3. Pendientes críticos antes de entregar

1. Confirmar tercer integrante en `Gestion/Integrantes.txt`.
2. Consolidar "Informe N1", "Informe N2" y "Informe N3" con nombres explícitos.
3. Ejecutar corrida mínima del plan de pruebas sobre CUs completos y adjuntar evidencias.
4. Si el docente exige estrictamente formatos Office/PDF, exportar documentos `.md` clave a `.pdf` o `.docx`.

## 4. Nota sobre ubicación de QA y Plan de Pruebas

Sí, corresponde incluirlos en `Documentacion` (idealmente bajo `Documentacion/Evidencias` o `Documentacion/Informes`), ya que son parte del aseguramiento de calidad y evidencia de avance.


