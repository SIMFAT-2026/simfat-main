# Scripts de Creacion de Tablas e Insercion de Datos de Prueba

- Fecha: 2026-04-21
- Version: 1.0
- Alcance: estado actual del backend en este repositorio.

## 1) Creacion de tablas SQL (autenticacion)

### Script oficial

- Archivo: `src/main/resources/db/migration/V1__create_auth_tables.sql`
- Motor objetivo: PostgreSQL
- Gestion: Flyway (arranca automaticamente con Spring Boot)

### Tablas creadas

- `app_users`
- `refresh_tokens`
- `password_reset_tokens`

### Ejecucion

Con backend levantado y `spring.flyway.enabled=true`, la migracion se ejecuta automaticamente al iniciar la app.

Comando habitual:

```bash
mvn spring-boot:run
```

## 2) Insercion de datos de prueba en MongoDB

## 2.1 Seeder base (dataset de desarrollo)

- Archivo: `src/main/java/com/simfat/backend/config/DataSeederConfig.java`
- Activacion: `app.seed.enabled=true` (default actual)
- Comportamiento:
  - Inserta regiones base si la base esta vacia.
  - Inserta perdida forestal de ejemplo.
  - Inserta alertas de calor de ejemplo.
  - Inserta regla global de alertas.

## 2.2 Import desde SQL de examen (modo controlado)

- Archivo: `src/main/java/com/simfat/backend/config/ExamSqlImportConfig.java`
- Activacion:
  - `app.seed.exam.enabled=true`
  - `app.seed.exam.sql-path=<ruta_al_sql_fuente>`
- Comportamiento:
  - Crea backup JSON en `backups/exam-import-<timestamp>/`
  - Limpia colecciones objetivo y carga datos normalizados.

## 2.3 Rollback de import examen

- Activacion:
  - `app.seed.exam.rollback.enabled=true`
  - `app.seed.exam.rollback.path=<ruta_backup>`
- Restaura colecciones desde respaldo JSON.

## 3) Recomendacion operativa

- Ambiente local de desarrollo:
  - mantener `app.seed.enabled=true`
  - `app.seed.exam.enabled=false` salvo pruebas de import.
- Antes de importar dataset externo:
  - validar codificacion del archivo SQL
  - conservar respaldo JSON generado.

## 4) Verificacion minima posterior

1. Revisar logs de arranque para confirmar migracion Flyway.
2. Validar conteo de documentos en `regions`, `forest_loss_records`, `heat_alert_events`.
3. Ejecutar compilacion backend para asegurar integridad:

```bash
mvn -q -DskipTests compile
```
