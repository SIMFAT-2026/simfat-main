# Scripts de Creacion de Tablas e Insercion de Datos de Prueba

- Fecha: 2026-04-21
- Version: 1.0
- Alcance: estado actual del backend en este repositorio.

## 1) Creacion de tablas SQL (autenticaci?n)

### Script oficial

- Archivo: `src/main/resources/db/migration/V1__create_auth_tables.sql`
- Motor objetivo: PostgreSQL
- Gesti?n: Flyway (arranca automaticamente con Spring Boot)

### Tablas creadas

- `app_users`
- `refresh_tokens`
- `password_reset_tokens`

### Ejecuci?n

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
  - Inserta regiones base s? la base esta vacia.
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

---

# Actualizacion 2026-05-28 - Scripts y datos chat comunitario

## 5) Migracion Flyway V3 - Chat comunitario

### Script oficial

- Archivo: `Producto/backend/simfat-backend/src/main/resources/db/migration/V3__community_chat_access_foundation.sql`
- Motor objetivo: PostgreSQL/Supabase
- Gestion: Flyway

### Objetos creados

- Tabla `user_community_profiles`
- Tabla `community_chat_room_access`
- Indices de busqueda por usuario, region y grant activo
- Permisos `PERM_COMMUNITY_CHAT_READ`, `PERM_COMMUNITY_CHAT_SEND`, `PERM_COMMUNITY_CHAT_MODERATE`, `PERM_COMMUNITY_CHAT_ACCESS_MANAGE`
- Asignacion de permisos a roles RBAC existentes

### Ejecucion

Con backend levantado y `spring.flyway.enabled=true`, la migracion se ejecuta automaticamente.

```bash
mvn spring-boot:run
```

## 6) Colecciones MongoDB agregadas por el backend

El backend crea/usa estas colecciones logicas al operar el chat:

- `community_chat_rooms`
- `community_chat_messages`
- `community_chat_presence`
- `community_chat_moderation_events`

## 7) Entorno local usado para QA

MongoDB local:

```bash
docker run -d --name simfat-mongo-test -p 27017:27017 -v simfat-mongo-test-data:/data/db mongo:7
```

Backend local con H2 en memoria para QA rapida:

```powershell
$env:AUTH_JWT_SECRET='local-dev-secret-that-has-at-least-32-bytes'
$env:SPRING_PROFILES_ACTIVE='local'
$env:SPRING_DATASOURCE_URL='jdbc:h2:mem:simfat_local;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.h2.Driver'
$env:SPRING_DATASOURCE_USERNAME='sa'
$env:SPRING_DATASOURCE_PASSWORD=''
$env:SPRING_JPA_HIBERNATE_DDL_AUTO='create-drop'
$env:SPRING_FLYWAY_ENABLED='false'
$env:MONGODB_URI='mongodb://localhost:27017/simfat-local'
$env:APP_SEED_ENABLED='true'
$env:FRONTEND_URL='http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://localhost:4173'
mvn '-Dspring-boot.run.useTestClasspath=true' spring-boot:run
```

Generacion de usuarios dev:

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/api/auth/dev/seed-users' `
  -ContentType 'application/json' `
  -Body '{"count":2}'
```
