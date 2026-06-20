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
