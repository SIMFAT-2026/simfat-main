# Plan de Portafolio — NoFires / SIMFAT + Batería E2E (Selenium + Java)

Objetivo: convertir el repo de NoFires/SIMFAT en una pieza de portafolio que demuestre
**desarrollo fullstack** y, sobre todo, **QA de automatización E2E** con Selenium + Java.
Público: reclutadores para roles de **QA Engineer** y **Desarrollador Junior**.

---

## Contexto del proyecto (para no re-descubrirlo)

- **Frontend:** React 18 + Vite + react-router-dom v6 + axios. Carpeta: `Producto/frontend/simfat-web`.
- **Backend:** Java 17 + Spring Boot 3, API REST con **RBAC + JWT**. Tests backend ya usan **JUnit 5**.
- **Roles:** administrador, analista de monitoreo, operador regional, usuario comunitario.
- **Rutas reales del frontend:** `/login`, `/register`, `/forgot-password`, `/reset-password`,
  `/dashboard`, `/home`, `/territorio`, `/alertas` (`/alerts`), `/reportes`, `/comunidad`,
  `/regions`, `/rules`, `/account`, `/admin/access-control`, `/admin/regions`, `/admin/rules`.
- **URLs producción/staging:**
  - Frontend: https://simfat-web-stg.vercel.app/
  - Backend: https://simfat-backend-production.up.railway.app (Swagger en `/swagger-ui/index.html`)
- **Repo compartido:** integrantes David Vásquez y Andrés Ibáñez (org SIMFAT-2026).

---

## Antes de empezar (2 chequeos críticos)

1. **Secrets:** auditar que no haya credenciales/tokens/`.env` versionados antes de hacer nada
   público. Si el repo va a mostrarse, revisar historial (`git log -p` sobre `.env*`) y usar
   `.gitignore` + `*.env.example`. Nunca commitear usuarios/passwords de prueba: van por variables
   de entorno.
2. **Ownership / coordinación:** el repo es compartido con Andrés. Trabajar en una **rama**
   (`feature/qa-e2e-selenium`) y abrir PR, o en un **fork** bajo la cuenta de David para el
   portafolio. Acordar con Andrés antes de tocar `main`.

---

## Fase 1 — Batería E2E con Selenium + Java (el core del portafolio)

Nuevo módulo Maven independiente: `Producto/qa-e2e-selenium/` (no toca el código de la app).

### Stack
- Java 17 · Maven · **Selenium 4** (usa Selenium Manager integrado; sin WebDriverManager)
- **JUnit 5** (Jupiter) + **AssertJ** para aserciones legibles
- **Page Object Model** (POM)
- Reporte HTML: **Allure** (o ExtentReports)
- Ejecución headless para CI + modo headed para debug

### Estructura propuesta
```
qa-e2e-selenium/
├── pom.xml
├── README.md
├── .env.example                 # BASE_URL y credenciales de prueba (NO commitear reales)
├── src/test/java/cl/simfat/e2e/
│   ├── support/
│   │   ├── DriverFactory.java    # Chrome/Firefox, headless, opciones
│   │   ├── BaseTest.java         # setup/teardown, screenshot on failure
│   │   ├── Config.java           # lee BASE_URL, credenciales desde env/props
│   │   └── Waits.java            # esperas explícitas reutilizables
│   ├── pages/
│   │   ├── LoginPage.java
│   │   ├── DashboardPage.java
│   │   ├── TerritoryPage.java
│   │   ├── AlertsPage.java
│   │   ├── ReportsPage.java
│   │   ├── CommunityPage.java
│   │   └── AdminAccessControlPage.java
│   └── tests/
│       ├── AuthTest.java
│       ├── RbacTest.java
│       ├── DashboardTest.java
│       ├── AlertsTest.java
│       ├── TerritoryTest.java
│       └── CrossBrowserSmokeTest.java
└── src/test/resources/config.properties
```

### Journeys a cubrir (mapeados a rutas reales)
- **Auth:** login válido; login inválido (mensaje de error); logout; link a `/forgot-password` visible.
- **RBAC (diferenciador fuerte):** con rol admin se accede a `/admin/access-control`, `/admin/rules`;
  con rol usuario comunitario se **bloquea/redirige**. Acceso directo a ruta protegida sin sesión → redirige a `/login`.
- **Dashboard:** carga de indicadores/widgets tras login.
- **Territorio:** el mapa/capa territorial renderiza.
- **Alertas:** listado y filtros de alertas.
- **Reportes:** generación/visualización de un reporte.
- **Comunidad:** flujo básico de la sección.
- **Cross-browser smoke:** Chrome + Firefox sobre el flujo crítico (login → dashboard).
- **Negativos/edge:** sesión expirada; formulario con campos vacíos; credenciales incorrectas.

### Buenas prácticas a demostrar (esto es lo que evalúa un QA senior)
- Page Object Model limpio, sin `Thread.sleep` (solo esperas explícitas).
- Datos y URLs por configuración (env), nada hardcodeado.
- Screenshot automático al fallar + adjunto al reporte.
- Tests independientes e idempotentes (crean/limpian su propia data en tenant de prueba).
- Apuntar a **staging**, nunca a datos productivos reales.
- Etiquetas/tags (`@Tag("smoke")`, `@Tag("regression")`) para suites selectivas.

### CI
- **GitHub Actions**: workflow que corre la suite headless (Chrome) en push/PR,
  publica el reporte Allure y los screenshots como artefactos. Badge de build en el README.

---

## Fase 1B — Refactor de Auth: vista pública de monitoreo en tiempo real (sin login)

Meta: exponer la **monitorización en tiempo real** en una ruta **pública** (sin autenticación),
para que cualquiera —incluido un reclutador— vea la app viva sin credenciales. Excelente para el
portafolio y habilita un flujo E2E público.

### Enfoque
- **Frontend:** nueva ruta pública, p. ej. `/monitoreo` (o modo "público/demo" de `/territorio`),
  renderizada **fuera del guard de autenticación**. Sin menús ni acciones que requieran sesión;
  solo lectura del mapa/indicadores.
- **Backend:** endpoints **públicos de solo lectura** para la capa de monitoreo (sin JWT),
  separados de los endpoints protegidos. Reformular el filtro/seguridad de Spring Security para
  permitir `permitAll()` en esas rutas específicas y mantener el resto detrás de RBAC + JWT.

### Seguridad (imprescindible)
- Exponer **solo datos no sensibles y agregados** de monitoreo (capa satelital/territorial pública).
  NADA de datos de tenants, usuarios, reglas de negocio ni endpoints de administración.
- Rate limiting básico y CORS acotado en las rutas públicas.
- Revisar que la vista pública no filtre IDs internos ni permita inferir datos privados.

### Impacto en E2E
- Nuevo test **`PublicMonitoringTest`**: la ruta pública **carga sin login** y muestra el mapa/indicadores.
- Se mantienen los tests de **RBAC**: lo protegido sigue exigiendo sesión; acceso directo redirige a `/login`.

---

## Fase 2 — Pulir el README / presentación de NoFires

Un README que un reclutador entienda en 60 segundos:
- Título + una línea de qué es y para quién (AIFBN, monitoreo forestal, alerta temprana).
- **Badges:** build (CI), lenguaje, licencia.
- **Screenshots o GIF** del sistema (dashboard, mapa) y, si se puede, un **GIF de la corrida E2E**.
- **Arquitectura:** diagrama simple (frontend React ↔ backend Spring Boot ↔ OpenEO/PostgreSQL/MongoDB).
- **Stack** en tabla.
- **Links de producción** (ya existen).
- Sección **"Testing / QA"** enlazando la batería E2E y explicando cobertura.
- **Roles del equipo:** dejar claro qué hizo David (backend, integración de APIs, y la automatización E2E).
- **Cómo correr** (frontend, backend, y la suite E2E) con pasos reproducibles.

---

## Fase 3 — Presentación y difusión

- **Pin** del repo en el perfil de GitHub de David.
- **README de perfil de GitHub** (landing personal: stack, links a CV/LinkedIn, proyectos).
- Enlazar el repo desde el **CV** (sección Proyectos) y desde **LinkedIn** (Destacado/Proyectos).
- Opcional: un `docs/` con capturas y el reporte Allure de ejemplo.

---

## Orden sugerido de trabajo
1. Chequeo de secrets + rama/fork.
2. **Refactor de auth: ruta pública de monitoreo `/monitoreo` (sin login)** + endpoints públicos de solo lectura.
3. Scaffolding del módulo `qa-e2e-selenium` (pom, DriverFactory, BaseTest, Config).
4. `LoginPage` + `AuthTest` corriendo verde contra staging.
5. `PublicMonitoringTest` (la vista pública carga sin sesión).
6. `RbacTest` (el diferenciador) + páginas admin.
7. Resto de journeys (dashboard, alertas, territorio, reportes, comunidad).
8. Reporte Allure + screenshots on failure.
9. GitHub Actions (CI) + badge.
10. Pulir README de NoFires + screenshots/GIF (incluir link a la demo pública).
11. Pin en GitHub + enlazar en CV/LinkedIn.

---

## PROMPT LISTO PARA VS CODE (pegar en Claude Code / Copilot Chat dentro del repo)

```
Contexto: Estoy en el repo de NoFires/SIMFAT (proyecto de título, cliente AIFBN).
Frontend: React 18 + Vite + react-router v6 en Producto/frontend/simfat-web.
Backend: Java 17 + Spring Boot 3, API REST con RBAC + JWT (tests con JUnit 5).
Roles: administrador, analista de monitoreo, operador regional, usuario comunitario.
Frontend en staging: https://simfat-web-stg.vercel.app/
Rutas: /login, /register, /forgot-password, /dashboard, /territorio, /alertas,
/reportes, /comunidad, /regions, /rules, /account, /admin/access-control, /admin/regions, /admin/rules.

Objetivo: crear una batería de tests E2E de automatización para portafolio, en un módulo Maven
NUEVO e independiente llamado Producto/qa-e2e-selenium (no modifiques el código de la app).

Requisitos técnicos:
- Java 17, Maven, Selenium 4 (usa Selenium Manager integrado, sin WebDriverManager), JUnit 5, AssertJ.
- Patrón Page Object Model. Sin Thread.sleep: solo esperas explícitas (WebDriverWait).
- Configuración por variables de entorno / config.properties: BASE_URL, credenciales de prueba.
  NUNCA hardcodear ni commitear credenciales reales; incluye un .env.example.
- DriverFactory con soporte Chrome y Firefox, modo headless para CI y headed para debug.
- BaseTest con setup/teardown y screenshot automático al fallar.
- Reporte Allure (o ExtentReports) y tags @Tag("smoke") / @Tag("regression").

Además, TAREA DE APP (aparte del módulo E2E): reformular la lógica de autenticación para exponer
la MONITORIZACIÓN EN TIEMPO REAL en una ruta PÚBLICA sin login (p. ej. /monitoreo), de solo lectura.
- Frontend: ruta pública fuera del guard de auth, solo lectura del mapa/indicadores.
- Backend (Spring Security): permitAll() en endpoints públicos de monitoreo; el resto sigue con RBAC + JWT.
- Seguridad: exponer solo datos no sensibles/agregados; nada de datos de tenants, usuarios o administración;
  CORS acotado y rate limiting básico. Verifica que no se filtren IDs internos ni datos privados.

Empieza por:
1. Refactor de auth: ruta pública /monitoreo (sin login) + endpoints públicos read-only de monitoreo.
2. Scaffolding: pom.xml, DriverFactory, BaseTest, Config, Waits.
3. LoginPage + AuthTest (login válido, login inválido con mensaje de error, logout,
   acceso directo a ruta protegida sin sesión redirige a /login).
4. PublicMonitoringTest: /monitoreo carga sin sesión y muestra el mapa/indicadores.
5. RbacTest: con rol admin se accede a /admin/access-control; con rol usuario comunitario se bloquea/redirige.
6. Journeys de dashboard, alertas, territorio, reportes y comunidad.
7. CrossBrowserSmokeTest (Chrome + Firefox) del flujo login → dashboard.
8. Workflow de GitHub Actions que corra la suite headless en push/PR y publique reporte + screenshots.
9. Un README del módulo con cómo correrlo (local headed, CI headless) y qué cubre.

Restricciones:
- Los tests apuntan a STAGING, nunca a datos productivos reales.
- Tests independientes e idempotentes; si crean data, que la limpien (tenant de prueba).
- Explica en el README las decisiones de diseño (POM, esperas, config por env).

Trabaja en una rama feature/qa-e2e-selenium. Antes de nada, revisa que no haya secrets
versionados (.env, tokens) en el repo y avísame si encuentras alguno.
```
