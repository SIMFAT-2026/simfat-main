# Runbook: entorno local SIMFAT apuntando a DBs remotas

Fecha: 2026-07-01

## Por qué existe esto

Para probar el frontend y el backend localmente usando Atlas (MongoDB) y
Supabase (PostgreSQL) reales — el mismo dataset que ve producción.
Útil para demos, validación visual y la defensa del proyecto.

Las credenciales viven en `.env.local` (ignorado por git). No usar
`start-local.ps1` sin el flag `-EnvFile .env.local` porque por defecto
lee `.env.remote`, que no existe o tiene valores de fallback a localhost.

---

## 1. Prerequisitos

- Java 17+ en PATH (`java -version`)
- Maven en PATH (`mvn -version`)
- Node.js en PATH (`node -version`)
- `.env.local` presente en `Producto/backend/simfat-backend/` con las
  credenciales de Atlas y Supabase (ver sección de gotchas)

---

## 2. Backend

```powershell
cd Producto\backend\simfat-backend
.\scripts\start-local.ps1 -EnvFile .env.local
```

Señales de arranque exitoso en los logs:

```
HikariPool-1 - Start completed     ← Supabase Postgres conectado
Tomcat started on port 8080         ← listo para recibir requests
```

Si el puerto 8080 está ocupado por una instancia anterior:

```powershell
# Identificar el proceso
Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess

# Matarlo (reemplazar PID por el número que apareció)
Stop-Process -Id <PID> -Force
```

Luego reintentar `start-local.ps1`.

---

## 3. Frontend

En otra terminal:

```powershell
cd Producto\frontend\simfat-web
npm run preview -- --port 4173
```

O con hot-reload (para desarrollo activo):

```powershell
npm run dev -- --port 4173
```

Abrir: `http://localhost:4173`

Login de prueba: `test@test.cl` / `Aabc.12345678` (rol ADMIN, existe en Supabase).

---

## 4. Variables que carga `.env.local`

Las variables clave y por qué importan:

| Variable | Para qué |
|---|---|
| `POSTGRES_URI` | Supabase Postgres (pooler de AWS sa-east-1) |
| `MONGODB_URI` | Atlas (replica set de 3 nodos) |
| `AUTH_JWT_SECRET` | Firma de tokens JWT — sin esto Spring Security no carga el bean custom y da 401 en todo |
| `OPENEO_SYNC_ENABLED=false` | Evita sync con el microservicio OpenEO al arrancar |
| `FRONTEND_URL` | CORS — incluye `localhost:4173` |
| `FIRMS_MAP_KEY` | API key de NASA FIRMS |

---

## 5. Gotchas descubiertos (2026-07-01)

**El parser del PS1 no stripeaba comillas.**
`Set-EnvFromFile` en `start-local.ps1` tomaba `MONGODB_URI="mongodb://..."` y
pasaba el valor con las comillas literales incluidas. El driver de MongoDB
rechazaba la URI → Spring Boot crasheaba en startup (exit code 1).
Corregido en `start-local.ps1` (commit de esta sesión): ahora stripea
comillas simples y dobles de los valores.

**Usar `bash dev-local-remote-db.sh` desde PowerShell falla.**
PowerShell llama al bash de WSL2 (`/usr/bin/bash`), no a Git Bash. Si no
tenés WSL2, el script da `execvpe failed: No such file or directory`.
Solución: usar siempre `start-local.ps1` desde PowerShell.

**Puerto 8080 puede estar ocupado** por una instancia anterior del backend
que no cerró limpiamente (p. ej. Ctrl+C en Maven no siempre mata el proceso
hijo). Ver paso 2 para matarlo.

**`inMemoryUserDetailsManager` en los logs no es un error real** para esta
arquitectura: SIMFAT usa un filtro JWT custom (`JwtAuthenticationFilter`)
que no depende de `UserDetailsService`. El warning de Spring Security es
cosmético — el login funciona igual.

**La cuenta `test@test.cl` no se bloquea en Supabase** — la tabla
`app_users` remota no tiene columnas `is_locked` ni `failed_login_attempts`.
El bloqueo por intentos fallidos solo existe en la instancia Docker local.
