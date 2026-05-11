# Auditoria de Entrega - Semana 10

Fecha: 2026-05-11  
Proyecto: SIMFAT  
Criterio: pauta de estructura de repositorio TPY1101

## 1. Resultado general

- Gestion: CUMPLE
- Documentacion: CUMPLE PARCIAL (con pendientes formales de informes y formato final)
- Producto: CUMPLE PARCIAL (scripts BD reforzados, revisar formato final de evidencia)

## 2. Revision por carpeta

### 2.1 Gestion

Requerido:
- `1.1.2 Documento de registro de definicion e identificacion del proyecto.docx`
- `Integrantes.txt` con tres integrantes.

Estado:
- Documento 1.1.2: presente.
- Integrantes: presente, pero actualmente contiene 2 lineas visibles.  
  Accion: confirmar y agregar el tercer integrante antes de entrega.

### 2.2 Documentacion

Requerido:
- Informes de avance N1, N2, N3.
- Diagramas tecnicos (MER/UML/arquitectura/casos de uso).
- Diseno de interfaz (wireframes/mockups).
- Planificacion (Carta Gantt actualizada).
- QA y plan de pruebas actualizados.

Estado actual:
- Informes: existen multiples informes tecnicos; falta consolidacion explicita de N1/N2/N3.
- Diagramas tecnicos: hay carpeta UML y MER con contenido.
- Wireframes/mockups: agregado documento de wireframes funcionales de baja fidelidad.
- Planificacion: agregado documento de planificacion semana 10 a 12 alineado a CU.
- QA/Pruebas: agregados plan de pruebas y checklist QA alineados a CU01-CU15.

Documentos agregados en esta revision:
- `Documentacion/Informes/matriz-casos-uso-semana10-2026-05-11.md`
- `Documentacion/Evidencias/2026-05-11_plan-pruebas-cu01-cu15.md`
- `Documentacion/Evidencias/2026-05-11_checklist-qa-cu01-cu15.md`
- `Documentacion/Gantt/2026-05-11_planificacion-semana10-a-semana12-cu.md`
- `Documentacion/Wireframes/2026-05-11_wireframes-funcionales-cu-prioritarios.md`

### 2.3 Producto

Requerido:
- Codigo fuente.
- Scripts BD (tablas, procedimientos y datos de prueba).
- Librerias/dependencias.

Estado actual:
- Codigo fuente: presente (frontend, backend, microservicio).
- Scripts BD:
  - Existian scripts de esquema SQL y Mongo.
  - Se agrega seed de datos de prueba.
  - Se agrega archivo con funcion/procedimiento PostgreSQL.
- Dependencias:
  - `pom.xml` (Java), `requirements.txt` (Python), `package.json` + `package-lock.json` (Frontend) presentes.

Scripts agregados en esta revision:
- `Producto/database/sql/seed-postgres-test-data.sql`
- `Producto/database/plsql/postgresql_auth_helpers.sql`

## 3. Pendientes criticos antes de entregar

1. Confirmar tercer integrante en `Gestion/Integrantes.txt`.
2. Consolidar "Informe N1", "Informe N2" y "Informe N3" con nombres explicitos.
3. Ejecutar corrida minima del plan de pruebas sobre CUs completos y adjuntar evidencias.
4. Si el docente exige estrictamente formatos Office/PDF, exportar documentos `.md` clave a `.pdf` o `.docx`.

## 4. Nota sobre ubicacion de QA y Plan de Pruebas

Si, corresponde incluirlos en `Documentacion` (idealmente bajo `Documentacion/Evidencias` o `Documentacion/Informes`), ya que son parte del aseguramiento de calidad y evidencia de avance.
