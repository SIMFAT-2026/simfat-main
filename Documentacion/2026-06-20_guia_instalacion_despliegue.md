# Guía de Instalación y Despliegue
## SIMFAT — Sistema de Monitorización Forestal y Análisis Territorial

- **Curso:** TPY1101 – Taller Aplicado de Programación
- **Institución:** Duoc UC
- **Estudiante:** David Vásquez
- **Fecha:** 2026-06-20
- **Versión:** 1.0

---

## 1. Requisitos del sistema

### Software requerido

| Componente | Versión mínima | Notas |
|---|---|---|
| Java | 21 (LTS) | OpenJDK o Eclipse Temurin recomendados |
| Maven | 3.9+ | Incluido en wrapper (`mvnw`) en el repositorio |
| Node.js | 20 (LTS) | Se recomienda usar `nvm` para gestionar versiones |
| npm | 10+ | Incluido con Node.js 20 |
| MongoDB | 7.0+ | Modo standalone o Atlas para desarrollo |
| PostgreSQL | 15+ | Se requiere usuario y base de datos dedicada |
| Docker | 24+ | Opcional — recomendado para entorno local reproducible |
| Docker Compose | 2.20+ | Opcional — requerido si se usa la instalación con Docker |

### Acceso a APIs externas

| Servicio | Variable | Notas |
|---|---|---|
| NASA FIRMS (VIIRS) | `firms.api.key` | Registro gratuito en NASA Earthdata |
| OpenWeatherMap (FWI) | `openweathermap.api.key` | Free tier disponible; 1000 llamadas/día |
| Copernicus CDSE (OpenEO) | Credenciales en microservicio `openeo-service` | Cuenta gratuita en dataspace.copernicus.eu |

---

## 2. Estructura del repositorio

```
simfat-main/
├── Producto/
│   ├── backend/
│   │   └── simfat-backend/      # API Spring Boot (Java 17)
│   │       ├── src/
│   │       ├── pom.xml
│   │       └── mvnw
│   └── frontend/
│       └── simfat-web/          # Aplicación React + Vite
│           ├── src/
│           ├── package.json
│           └── vite.config.js
├── Documentacion/               # Documentación académica y técnica
└── docker-compose.yml           # (ver sección 4)
```

---

## 3. Variables de entorno — Backend

El backend Spring Boot lee su configuración desde `application.properties` o desde variables de entorno del sistema (convenio `SPRING_*`). Para desarrollo local, crear o editar el archivo:

```
Producto/backend/simfat-backend/src/main/resources/application.properties
```

### Configuración mínima

```properties
# PostgreSQL — identidad, roles, sesiones
spring.datasource.url=jdbc:postgresql://localhost:5432/simfat
spring.datasource.username=postgres
spring.datasource.password=CAMBIAR_EN_PRODUCCION

# MongoDB — datos de negocio (territorio, comunidad, alertas)
spring.data.mongodb.uri=mongodb://localhost:27017/simfat

# JWT — clave secreta para firma de tokens (mínimo 32 bytes)
auth.jwt.secret=CAMBIAR_POR_CADENA_SECRETA_LARGA_Y_ALEATORIA
auth.jwt.access-ttl-minutes=15
auth.jwt.refresh-ttl-days=14

# APIs externas
firms.api.map-key=TU_CLAVE_NASA_EARTHDATA

# Servidor
server.port=8080
```

> Open-Meteo (FWI meteorológico) no requiere clave de API.

> **Nota de seguridad:** No subir `application.properties` con credenciales reales al repositorio. Usar `.gitignore` o variables de entorno del sistema en producción.

---

## 4. Desarrollo local con bases de datos remotas (modo híbrido)

Este modo levanta el backend en `localhost:8080` conectado a Supabase y MongoDB Atlas de producción, con el frontend en `localhost:5173` apuntando al backend local. Útil para probar cambios del backend sin afectar el deploy de Railway.

### Prerequisitos

- Java 17+ y Maven instalados (o usar `./mvnw` incluido en el repo)
- Node.js 20+ y npm
- Archivo `Producto/backend/simfat-backend/.env.local` con las credenciales del equipo (no se commitea — ver team password manager o pedir a David/Andrés)

### Pasos

```bash
# Terminal 1 — Backend
cd Producto/backend/simfat-backend
bash dev-local-remote-db.sh
# Esperar: "Started SimfatBackendApplication in X seconds"

# Terminal 2 — Frontend
echo "VITE_API_URL=http://localhost:8080" > Producto/frontend/simfat-web/.env.local
cd Producto/frontend/simfat-web
npm run dev
# Abrir: http://localhost:5173
```

Para volver al frontend apuntando al backend remoto (Railway), eliminar o vaciar el `.env.local` del frontend:

```bash
rm Producto/frontend/simfat-web/.env.local
```

> **Nota:** `OPENEO_SYNC_ENABLED=false` está seteado en `.env.local` para evitar disparar sincronizaciones satelitales desde local.

---

## 5. Instalación sin Docker (desarrollo local con BDs locales)

### 4.1 Configurar PostgreSQL

```bash
# Crear base de datos y usuario
psql -U postgres -c "CREATE DATABASE simfat;"
psql -U postgres -c "CREATE USER simfat_user WITH PASSWORD 'tu_password';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE simfat TO simfat_user;"
```

Las migraciones de esquema se ejecutan automáticamente al iniciar el backend mediante **Flyway**. No es necesario ejecutar scripts SQL manualmente.

> **Nota para un proyecto Supabase nuevo:** la migración V7 activa RLS y revoca privilegios en las tablas del backend, pero **no** toca `flyway_schema_history`, porque Flyway mantiene un bloqueo sobre su propia tabla de historial mientras ejecuta migraciones y un `ALTER TABLE` desde dentro de una migración quedaría esperando a Flyway hasta agotar el tiempo de la sentencia. En cada proyecto Supabase nuevo hay que endurecerla manualmente, una sola vez:
>
> ```sql
> ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;
> REVOKE ALL ON TABLE public.flyway_schema_history FROM anon, authenticated, PUBLIC;
> ```
>
> Más contexto en `Documentacion/Informes/seguridad-supabase-rls-2026-04-22.md`.

### 4.2 Configurar MongoDB

MongoDB en modo standalone no requiere configuración adicional. Verificar que el servicio esté activo:

```bash
# Linux/macOS
sudo systemctl status mongod

# Windows (PowerShell)
Get-Service -Name MongoDB
```

La base de datos `simfat` se crea automáticamente al primer acceso desde la aplicación.

### 4.3 Levantar el backend

```bash
cd Producto/backend/simfat-backend

# Opción A: usando el Maven Wrapper incluido en el repositorio
./mvnw spring-boot:run

# Opción B: usando Maven instalado globalmente
mvn spring-boot:run
```

El backend queda disponible en: **http://localhost:8080**

### 4.4 Levantar el frontend

```bash
cd Producto/frontend/simfat-web

# Instalar dependencias (solo la primera vez o después de cambios en package.json)
npm install

# Iniciar servidor de desarrollo
npm run dev
```

El frontend queda disponible en: **http://localhost:5173**

> El frontend espera el backend en `http://localhost:8080`. Si se cambia el puerto, actualizar la variable `VITE_API_BASE_URL` en el archivo `.env.local`.

---

## 6. Instalación con Docker Compose (referencia)

> **Estado actual:** este `docker-compose.yml` y los `Dockerfile` que referencia son una configuración de referencia — todavía no existen como archivos en el repositorio. Para levantar el entorno local hoy, usar la sección 4 (instalación sin Docker). Esta sección documenta cómo se containerizaría el stack (bases de datos + backend + frontend) cuando se agreguen los Dockerfile correspondientes.

### docker-compose.yml (propuesto)

```yaml
version: '3.9'

services:

  postgres:
    image: postgres:15-alpine
    container_name: simfat-postgres
    environment:
      POSTGRES_DB: simfat
      POSTGRES_USER: simfat_user
      POSTGRES_PASSWORD: simfat_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U simfat_user -d simfat"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:7.0
    container_name: simfat-mongo
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./Producto/backend/simfat-backend
      dockerfile: Dockerfile
    container_name: simfat-backend
    ports:
      - "8080:8080"
    environment:
      POSTGRES_URI: jdbc:postgresql://postgres:5432/simfat
      POSTGRES_USER: simfat_user
      POSTGRES_PASSWORD: simfat_password
      MONGODB_URI: mongodb://mongodb:27017/simfat
      AUTH_JWT_SECRET: cambiar_en_produccion_cadena_larga_y_aleatoria
      FIRMS_API_KEY: ${FIRMS_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy
      mongodb:
        condition: service_healthy

  frontend:
    build:
      context: ./Producto/frontend/simfat-web
      dockerfile: Dockerfile
    container_name: simfat-frontend
    ports:
      - "5173:80"
    environment:
      VITE_API_BASE_URL: http://localhost:8080
    depends_on:
      - backend

volumes:
  postgres_data:
  mongo_data:
```

### Levantar el stack

```bash
# Desde la raíz del repositorio
# Exportar clave de NASA FIRMS antes de iniciar (Open-Meteo no requiere clave)
export FIRMS_API_KEY=tu_clave_firms

docker compose up --build -d
```

Para detener el stack:

```bash
docker compose down
```

Para eliminar también los volúmenes (reset completo):

```bash
docker compose down -v
```

---

## 7. Despliegue en producción

### 6.1 Frontend

```bash
cd Producto/frontend/simfat-web

# Generar build optimizado
npm run build
```

El directorio `dist/` generado contiene los archivos estáticos. Se puede servir con:
- **Nginx** (ver configuración de ejemplo abajo)
- **Vercel** (configuración actual del proyecto — push a `main` despliega automáticamente)
- **Netlify** o cualquier CDN compatible con SPA

**Ejemplo de configuración Nginx (`/etc/nginx/sites-available/simfat`):**

```nginx
server {
    listen 80;
    server_name tu-dominio.cl;
    root /var/www/simfat/dist;
    index index.html;

    # Soporte para React Router (SPA)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy al backend API
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 6.2 Backend

```bash
cd Producto/backend/simfat-backend

# Generar JAR ejecutable (omitiendo tests para despliegue rápido)
mvn package -DskipTests

# Ejecutar el JAR
java -jar target/simfat-backend-*.jar
```

En producción, las variables de entorno reemplazan las propiedades del `application.properties`:

```bash
export POSTGRES_URI=jdbc:postgresql://host-produccion:5432/simfat
export POSTGRES_USER=simfat_user
export POSTGRES_PASSWORD=password_produccion
export MONGODB_URI=mongodb+srv://usuario:password@cluster.mongodb.net/simfat
export AUTH_JWT_SECRET=cadena_secreta_produccion_minimo_32_bytes
export FIRMS_API_KEY=clave_firms
```

Se recomienda ejecutar el JAR con un servicio `systemd` o bajo un gestor de procesos como **PM2** (Node) o **Supervisor** para garantizar reinicio automático.

---

## 8. Verificación post-instalación

Una vez levantado el sistema, ejecutar las siguientes verificaciones:

| Verificación | URL o comando | Resultado esperado |
|---|---|---|
| Health check del backend | `GET http://localhost:8080/actuator/health` | JSON `{"status":"UP"}` |
| Swagger UI (documentación API) | `http://localhost:8080/swagger-ui.html` | Interfaz Swagger carga con todos los endpoints documentados |
| Frontend accesible | `http://localhost:5173` | Página de login de SIMFAT carga correctamente |
| Login de prueba | Usar email `davi.vasquezo@duocuc.cl` | Contactar al equipo para obtener la contraseña; el login redirige al mapa territorial |
| Sync de territorio | `POST http://localhost:8080/api/territory/sync` (requiere token ADMIN) | La sincronización de datos de territorio se inicia |

---

## 9. Resolución de problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| Backend falla al iniciar con error de conexión a PostgreSQL | La base de datos no está corriendo o las credenciales son incorrectas | Verificar que PostgreSQL esté activo y que las variables de entorno coincidan con el usuario y contraseña creados |
| Frontend muestra error de CORS al hacer llamadas a la API | El origen del frontend no está en la lista de orígenes permitidos del backend | Agregar el origen del frontend (ej. `http://localhost:5173`) a la configuración CORS del backend en `SecurityConfig.java` |
| MongoDB no conecta desde Docker | El hostname debe ser el nombre del servicio Docker, no `localhost` | Usar `mongodb://mongodb:27017/simfat` dentro de Docker Compose, no `mongodb://localhost:27017/simfat` |
| Las migraciones Flyway fallan al iniciar | La base de datos `simfat` existe pero tiene tablas creadas por una versión anterior sin el historial de Flyway | Limpiar la base de datos y volver a crearla, o agregar la entrada correspondiente en la tabla `flyway_schema_history` |
| Las APIs externas (FIRMS, OpenWeatherMap) no retornan datos | Claves de API incorrectas o límite de cuota diario alcanzado | Verificar las claves en las variables de entorno y revisar el dashboard de uso en NASA Earthdata y OpenWeatherMap |


---

## 10. Actualización 2026-10-12: integración MapBiomas

Esta sección agrega la configuración necesaria para la integración MapBiomas (commit de merge `767caa6`). No modifica las secciones anteriores. Referencias:

- Modelo de datos: `Documentacion/MER/2026-10-12_mer_integracion_mapbiomas_v1.md`
- Arquitectura: `Documentacion/UML/2026-10-12_arquitectura_integracion_mapbiomas_v1.md`

**Base de datos:** la integración no agrega migraciones Flyway (la última sigue siendo `V6__notifications.sql`). Todas las estructuras nuevas están en MongoDB y los índices se crean automáticamente al arrancar (`spring.data.mongodb.auto-index-creation=true`).

### 10.1 Variables de entorno nuevas del backend

Todas son opcionales: si no se definen, se usa el valor por defecto de `application.properties`.

| Variable | Propiedad | Valor por defecto | Significado |
|---|---|---|---|
| `MAPBIOMAS_SEED_ENABLED` | `mapbiomas.seed.enabled` | `true` | Carga la semilla MapBiomas (86 comunas) a la colección `comuna_mapbiomas_stats` al arrancar. La carga es idempotente. Con `false` no se carga nada |
| `MAPBIOMAS_SEED_DATA_VERSION` | `mapbiomas.seed.data-version` | `lulc-col2+fuego-col1@2013-2025` | Versión de datos con la que se guardan los documentos de la semilla |
| `MAPBIOMAS_STATS_DATA_VERSION` | `mapbiomas.stats.data-version` | `lulc-col2+fuego-col1@2013-2025` | Versión que leen el score de riesgo, las capas públicas y el detalle por comuna. Debe coincidir con una versión realmente cargada; si no, no hay datos MapBiomas |
| `MAPBIOMAS_CITATION` | `mapbiomas.citation` | `MapBiomas Chile - Colección 2 de Cobertura y Uso del Suelo (1999-2024) y Colección 1 de Fuego (2013-2025). Licencia CC BY 4.0.` | Texto de atribución que se muestra en las capas y el detalle. Ver la nota sobre caracteres no ASCII |
| `MAPBIOMAS_HISTORY_RECENCY_R_MAX` | `mapbiomas.history.recency.r-max` | `0.0` | Amplitud del multiplicador de recencia del historial de fuego. `0.0` lo desactiva (neutro) |
| `MAPBIOMAS_HISTORY_RECENCY_TAU` | `mapbiomas.history.recency.tau` | `8.0` | Constante de decaimiento (años) del multiplicador de recencia |
| `MAPBIOMAS_WEIGHT` | `territory.riesgo.mapbiomas.weight` | `0.0` | Peso con que MapBiomas se mezcla en el score de riesgo. Con `0.0` MapBiomas **no** afecta ningún nivel de alerta. Solo se aceptan los valores `0.0`, `0.10`, `0.25`, `0.35` y `0.50`; cualquier otro (incluidos `0.30` o `0.60`) **impide el arranque** del backend |
| `TERRITORY_RIESGO_UMBRAL_PREVENTIVO` | `territory.riesgo.umbral.preventivo` | `0.50` | Score mínimo para el nivel `PREVENTIVO` |
| `TERRITORY_RIESGO_UMBRAL_ALTO` | `territory.riesgo.umbral.alto` | `0.70` | Score mínimo para el nivel `ALTO` |
| `TERRITORY_RIESGO_UMBRAL_CRITICO` | `territory.riesgo.umbral.critico` | `0.85` | Score mínimo para el nivel `CRITICO` |
| `TERRITORY_FWI_METHOD` | `territory.fwi.method` | `PROXY_V1` | Método de cálculo del FWI: `PROXY_V1` (aproximación con Open-Meteo) o `VAN_WAGNER` (cadena canadiense real, solo para las comunas monitoreadas) |
| `TERRITORY_FWI_SPINUP_ENABLED` | `territory.fwi.spinup.enabled` | `false` | Ejecuta una vez, al arrancar, el relleno inicial del estado FWI (`comuna_fwi_state`) desde el historial de Open-Meteo Archive |
| `TERRITORY_FWI_SPINUP_LOOKBACK_DAYS` | `territory.fwi.spinup.lookback-days` | `120` | Días de historial que usa el relleno inicial |

Los umbrales de score se pueden ajustar sin tocar el código, pero su recalibración por valor de `MAPBIOMAS_WEIGHT` es un resultado del estudio y no forma parte de esta configuración. Los umbrales de FWI (20 para `ALTO`, 45 para `CRITICO`) son constantes del código, y un valor de FWI mayor o igual que 20 eleva la comuna a `ALTO` aunque su score sea menor.

### 10.2 Procedimientos de configuración

**Activar o revertir MapBiomas en el score (variable de Railway, sin cambios de código):**

1. Definir `MAPBIOMAS_WEIGHT` con un valor de la grilla (por ejemplo `0.50`) y reiniciar el backend.
2. Para revertir, quitar la variable (o fijar `0.0`) y reiniciar. Los snapshots nuevos vuelven a calcularse sin MapBiomas.
3. El peso efectivo de cada snapshot queda en `comuna_risk_snapshots.mapbiomasWeight`, y la interfaz lo muestra en el panel de la comuna.

**Cambiar a la cadena FWI real (`VAN_WAGNER`):**

1. Desplegar una vez con `TERRITORY_FWI_SPINUP_ENABLED=true` (y, si hace falta, `TERRITORY_FWI_SPINUP_LOOKBACK_DAYS`) para rellenar `comuna_fwi_state` antes del cambio de método. Revisar en el log las líneas que comienzan con `fwi_spinup`.
2. **Devolver `TERRITORY_FWI_SPINUP_ENABLED` a `false` justo después de una ejecución exitosa.** Los reinicios automáticos de Railway volverían a disparar el relleno en cada arranque.
3. Fijar `TERRITORY_FWI_METHOD=VAN_WAGNER` como un cambio de configuración independiente, nunca junto con un cambio de código.
4. Cada observación meteorológica nueva registra el método usado en el campo `fwiMethod`; los documentos antiguos sin ese campo se interpretan como `PROXY_V1`.

El relleno inicial funciona mejor al final de la temporada húmeda: el Drought Code no tiene un término de decaimiento por tiempo, de modo que en plena temporada seca sin lluvia dentro de la ventana de 120 días puede subestimar la sequía acumulada.

### 10.3 Valores no ASCII en `application.properties`

Los caracteres no ASCII dentro de `application.properties` deben escribirse como secuencias `\uXXXX`. Si se escriben directamente, el valor se lee con una codificación incorrecta y se sirve corrupto (texto con caracteres extraños). Ejemplo de la atribución por defecto:

```properties
mapbiomas.citation=${MAPBIOMAS_CITATION:MapBiomas Chile - Colección 2 de Cobertura y Uso del Suelo (1999-2024) y Colección 1 de Fuego (2013-2025). Licencia CC BY 4.0.}
```

Esto aplica al valor por defecto escrito dentro del archivo. Una variable de entorno `MAPBIOMAS_CITATION` definida en el servidor no pasa por la lectura del archivo `.properties`; si se usa, conviene verificar en la respuesta de la capa que el texto se vea correcto.

### 10.4 Pruebas del backend

Para ejecutar la suite completa del backend usar:

```bash
cd Producto/backend/simfat-backend
mvn -o clean test
```

El `clean` evita que clases compiladas antiguas (por ejemplo, las generadas por el IDE en `target/`) alteren el resultado de las pruebas, y `-o` ejecuta Maven sin conexión, con las dependencias ya descargadas. La verificación previa al merge de esta integración usó este comando (499 pruebas del backend en verde, según el informe de traspaso del 2026-10-12).

### 10.5 Frontend en producción

- URL de producción: `https://nofires-latam.vercel.app`.
- Ese dominio debe estar asignado como **dominio del proyecto Vercel `nofires-web`**, no como un alias estático: un alias reapuntado a mano puede quedar atrás en un despliegue posterior y servir una versión antigua.
- El backend debe permitir ese origen en `FRONTEND_URL` (propiedad `app.cors.frontend-url`, lista separada por comas). El valor por defecto solo incluye los orígenes locales `http://localhost:3000` y `http://localhost:4173`.
- La URL de staging `https://simfat-web-stg.vercel.app` sigue vigente.

### 10.6 Verificación posterior al despliegue

| Verificación | URL o comando | Resultado esperado |
|---|---|---|
| Semilla cargada | Log del backend al arrancar | Línea `mapbiomas_seed status=ok dataVersion=... loaded=86 skipped=0 errors=0` |
| Capa de cobertura | `GET /api/territory/public/layers?regionId=biobio&indicators=LANDCOVER&year=2024` | `200` con `layers.LANDCOVER.values` por comuna |
| Capa de área quemada | `GET /api/territory/public/layers?regionId=biobio&indicators=BURNED&year=2017` | `200` con `layers.BURNED.values` por comuna |
| Validación de año | `GET /api/territory/public/layers?regionId=biobio&indicators=BURNED&year=2012` | `400` (fuera del rango 2013-2025) |
| Detalle por comuna | `GET /api/territory/public/mapbiomas/CHL.13.1.1_1` | `200` con encabezado `Cache-Control: public, max-age=300` |
| Comuna inexistente | `GET /api/territory/public/mapbiomas/CHL.99.9.9_1` | `404` |
| Peso efectivo | Panel de una comuna en `/territorio` | Con `MAPBIOMAS_WEIGHT=0.0` la interfaz indica que MapBiomas no entra al score |
