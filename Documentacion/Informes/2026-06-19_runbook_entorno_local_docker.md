# Runbook: entorno local SIMFAT con Docker (Mongo + Postgres aislados)

Fecha: 2026-06-19

## Por que existe esto

Para probar cambios de backend/frontend sin tocar Atlas (Mongo) ni Supabase
(Postgres) reales — ese par de bases es compartido por el equipo y no hay
plata para un ambiente develop/staging separado. Este runbook levanta un
Mongo y un Postgres descartables en Docker, en la misma maquina, sin pisar
nada remoto.

`scripts/start-local.ps1` carga `.env.remote` por defecto, es decir que el
flujo normal de "levantar local" en este proyecto ya apunta a Atlas/Supabase
remotos. Este runbook es la alternativa cuando se necesita una base
limpia y descartable (p. ej. probar un cambio de esquema antes de
migrarlo en remoto).

## 1. Contenedores Docker

```bash
docker run -d --name simfat-mongo-test -p 27017:27017 mongo:7
docker run -d --name simfat-postgres-test -p 5432:5432 \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=simfat postgres:16
```

Verificar que ambos respondan antes de seguir:

```bash
docker exec simfat-mongo-test mongosh --eval "db.runCommand({ping:1})"
docker exec simfat-postgres-test pg_isready -U postgres
```

Si el puerto 8080 ya esta ocupado por otro contenedor tuyo (paso comun en
esta maquina: `fluentia-nginx` lo usa), correr el backend en otro puerto
(ver variables abajo).

## 2. Variables de entorno para el backend

No usar `.env.remote` ni `.env.example` para esto — exportar directamente
en la sesion de shell donde se corre `mvn spring-boot:run`:

```bash
export SERVER_PORT=8081
export SPRING_PROFILES_ACTIVE=dev
export FRONTEND_URL="http://localhost:5173,http://localhost:3000,http://localhost:4173"
export MONGODB_URI="mongodb://localhost:27017/simfat"
export POSTGRES_URI="jdbc:postgresql://localhost:5432/simfat"
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=postgres
export OPENEO_SYNC_ENABLED=false
export AUTH_JWT_SECRET="dev-local-secret-key-for-visual-testing-only-32bytes"

cd Producto/backend/simfat-backend
mvn -o spring-boot:run
```

Notas de cada variable:

- `SERVER_PORT=8081`: el default es 8080; cambiar si ese puerto ya esta
  tomado por otro servicio local.
- `SPRING_PROFILES_ACTIVE=dev`: habilita el endpoint
  `POST /api/auth/dev/seed-users` (ver paso 4). Sin este profile, el
  endpoint responde 403.
- `FRONTEND_URL`: debe incluir el puerto real donde corre Vite (`5173`
  por defecto). El default del proyecto solo trae `3000` y `4173`, asi
  que sin este override el login falla con error de CORS.
- `MONGODB_URI` / `POSTGRES_URI`: apuntan a los contenedores locales,
  nunca a Atlas/Supabase.
- `AUTH_JWT_SECRET`: debe tener al menos 32 bytes o el arranque falla
  (`JwtService` lo valida). El valor de arriba es solo para uso local,
  no reusar en ningun entorno real.
- `OPENEO_SYNC_ENABLED=false`: evita que el backend intente llamar al
  microservicio OpenEO real al arrancar.

## 3. Frontend

Vite lee `VITE_API_URL` para saber a que backend apuntar (default
`http://localhost:8080` si no se define):

```bash
cd Producto/frontend/simfat-web
export VITE_API_URL="http://localhost:8081"
npm run dev
```

Rutas relevantes para probar el panel de alertas:
- `http://localhost:5173/admin/rules` — configuracion de `AlertRule`
- `http://localhost:5173/alertas` — panel de alertas (FIRMS + operativas)

## 4. Crear un usuario admin de prueba

La base local arranca vacia — no existen los usuarios de Atlas/Supabase
(ej. `test@test.cl`). Crear uno nuevo:

```bash
curl -s -X POST http://localhost:8081/api/auth/dev/seed-users \
  -H "Content-Type: application/json" -d '{"count":1}'
```

Devuelve `email` y `password` generados (rol `USER` por defecto). Para
poder gestionar reglas de alerta (`PERM_ALERT_RULE_MANAGE`), subir el rol
a `ADMIN` directo en Postgres (el `AppUser` vive ahi, no en Mongo, pese
a llamarse igual que otros repos Mongo del proyecto):

```bash
docker exec simfat-postgres-test psql -U postgres -d simfat -c \
  "UPDATE app_users SET roles = '{ADMIN}' WHERE email = '<email-generado>';"
```

## 5. Limpieza al terminar

```bash
docker rm -f simfat-mongo-test simfat-postgres-test
```

Todo lo creado (regiones, reglas de prueba, usuarios seed) desaparece con
los contenedores — nada de esto toca Atlas ni Supabase.

## Gotchas encontrados en esta sesion

- `AppUser` es `@Entity` de Postgres (`app_users`), no `@Document` de
  Mongo, aunque varios otros repos del proyecto (`AlertRule`,
  `HeatAlertEvent`, etc.) si son Mongo. No asumir por el nombre.
- El validator de Mongo (`database/nosql/init-mongodb-schema.js`) para
  `alert_rules` tenia los campos viejos (`umbralPorcentajePerdida`,
  `umbralEventosCalor`) como `required` — si se prueba contra una base
  con ese validator activo y no se actualiza, los inserts con el
  esquema nuevo fallan.
