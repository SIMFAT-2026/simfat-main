# Auditoría de detalles UX — SIMFAT / NoFires (frontend web)

Fecha: 2026-10-11 · Rama: `mb/s3-7-ux-pass` · Alcance: todas las páginas (`src/pages`) y componentes (`src/features`, `src/components`) · Método: análisis estático de código y CSS, cálculo de contraste WCAG 2.1, distancias de color CIEDE2000 y consulta de los endpoints públicos del backend local (`localhost:8081`).

Convenciones:
- Rutas de archivo relativas a `Producto/frontend/simfat-web/src/` salvo las del backend (ruta completa desde `Producto/backend/...`).
- **Dato clave de tamaños:** `:root` fija `font-size: 14px` (`styles/global.css:6-8`), por lo que 1 rem = 14 px. `0.75rem` = 10,5 px y `0.85rem` = 11,9 px: todo el texto secundario de la app queda por debajo de 12 px.
- Severidad: Alta = ilegible/incorrecto o falla WCAG en texto principal; Media = texto secundario o inconsistencia visible; Baja = pulido. Esfuerzo: S (< 1 h), M (medio día), L (más de un día).
- Contrastes de fondos translúcidos calculados sobre `#f2efe9` (tierra de OpenStreetMap, valor asumido).

---

## 1. Resumen

| Categoría | Alta | Media | Baja | Total |
|---|---:|---:|---:|---:|
| 1. Ortografía, tildes y consistencia de textos (O) | 5 | 15 | 2 | 22 |
| 2. Contraste y color (C) | 5 | 12 | 3 | 20 |
| 3. Legibilidad (L) | 2 | 8 | 3 | 13 |
| 4. Coherencia de datos mostrados (D) | 4 | 5 | 2 | 11 |
| **Total** | **16** | **40** | **10** | **66** |

## 2. Top 10 para corregir antes del 17 de octubre

Prioridad: `/territorio`, páginas públicas (`/alertas`, login, 404) y lo que ve un jurado.

| # | Qué corregir | IDs | Esf. |
|---|---|---|---|
| 1 | Resumen regional y leyenda de riesgo: un nivel "Alto" con puntaje 21-30/100 parece contradictorio (ver sección 3). Añadir la causa (FWI ≥ 20) en el resumen, en la leyenda y en el tooltip. | D-01, D-02 | S |
| 2 | Informe PDF: tabla "Score WLC" con umbrales 20/40/60 y escala FWI 11/21/38/50; el sistema usa 50/70/85 y reglas de FWI 20/45. El PDF se entrega impreso. | D-03 | S |
| 3 | `/alertas` pública: 33 líneas con textos sin tilde (priorización, público, ubicación, "Ultimos 30 dias", "Que muestra"…). | O-01 | S |
| 4 | Textos en inglés en componentes compartidos: "Retry", "Cancel", "Try adjusting filters…", "Loading…". "Retry" sale en `/territorio` si falla la carga. | O-02 | S |
| 5 | Cabecera, menú lateral, pie y campana de notificaciones sin tildes (se ven en todas las páginas). | O-03 | S |
| 6 | Niveles de alerta mostrados como enum crudo ("CRITICO", "CRITICA", "GUIA") frente a "Crítico" en el mapa. | O-05 | S |
| 7 | Panel de comuna de `/territorio`: texto de 9-10 px, `#94a3b8` sobre `#f8fafc` (2,45:1) y verde de éxito 3,15:1. | L-01, C-03, C-04 | S |
| 8 | Mapa de riesgo: los tres niveles se pintan casi iguales (ΔE 9,5-10,6) y "Normal" es indistinguible de "sin datos" (ΔE 3,8); la leyenda usa colores opacos que no coinciden con el mapa. | C-01 | M |
| 9 | Tarjeta de reporte ciudadano: "Incendio activo" `#dc2626` sobre fondo oscuro 3,13:1 y hora 3,17:1; ocultar la descripción a 2 líneas sin alternativa. | C-05, L-06 | S |
| 10 | Páginas de acceso y 404 (login, registro, recuperación): "Iniciar sesion", "Debil", "Minimo", "La pagina solicitada no existe". | O-04 | S |

Fuera del top 10 pero a decidir con el equipo: D-04 (el backend aplica reglas de FWI distintas a nivel regional y comunal).

---

## 3. Hallazgo principal: "20 en nivel alto" con "Máx 30/100"

**Es coherente en el código, pero se lee como contradicción.** Reproducido con datos reales del backend local (consulta a `/api/territory/risk-score/comunas/{region}`):

| Región | Comunas | En nivel Alto | Puntaje de las Alto | FWI mínimo de las Alto | Puntaje máximo |
|---|---:|---:|---|---:|---:|
| Ñuble | 21 | 20 | 21-30 | 20,24 | 30 |
| Biobío | 33 | 22 | 21-30 | 20,42 | 30 |
| La Araucanía | 32 | 29 | 21-30 | 20,16 | 30 |

Las comunas Normal de Biobío tienen FWI entre 14,5 y 19,6 y puntaje 15-20. El corte es exactamente FWI = 20.

**Causa (backend):**
- `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/ComunaRiskServiceImpl.java:74` define `FWI_PREVENTIVO = 20.0` y las líneas 486-487 devuelven `"ALTO"` si `fwiRaw >= 20` **o** `score >= 0.70`. El nivel no depende solo del puntaje: hay reglas de escalamiento (override) por FWI (líneas 477, 486), por foco FIRMS de hoy, por ≥ 4 focos o por FRP medio ≥ 60 (líneas 477-485).
- El puntaje en modo STANDARD es `0,52·FWI/50 + 0,33·FIRMS + 0,15·reportes` (`ComunaRiskServiceImpl.java:268`). Sin focos ni reportes y con FWI 20-29, el puntaje solo llega a 21-30/100 (aquí todas las comunas están en modo STANDARD con `COPERNICUS_UNAVAILABLE`).
- Consecuencia: el "Máx 30/100" es el techo real del puntaje, y el nivel "Alto" lo da solo el FWI.

**Causa (frontend):** `features/territory/utils/regionalSummary.js:41-47` cuenta comunas por `score.alertLevel` (nivel final) y `RegionalSummary.jsx:19` muestra el máximo del puntaje compuesto sin decir que ambos se derivan con reglas distintas. La leyenda (`constants/colorScales.js:11,22,33,44`) afirma "Alto (70-84)" y solo el nivel Crítico tiene nota (línea 45), visible únicamente como `title` al pasar el ratón (`TerritoryMapPanel.jsx:1357`).

**Agravante:** a nivel regional el mismo FWI da otro nivel. `TerritoryRiskServiceImpl.java:50-52,240-248` usa FWI ≥ 20 → Preventivo y FWI ≥ 30 → Alto. Hoy `/api/territory/public/layers` devuelve Biobío con `alertLevel: PREVENTIVO` (puntaje 0,19) mientras 22 de sus 33 comunas están en Alto. Ver D-04.

**Propuesta de texto y UI (sin tocar el backend):** el payload por comuna ya trae `fwiRaw`, `firmsCount`, `firmsFrpMean` (verificado), así que el frontend puede derivar el motivo.

1. Resumen regional (`RegionalSummary.jsx:18-19`):
   `Ñuble · 21 comunas · 20 en nivel Alto (por FWI ≥ 20) · Puntaje máx.: Coihueco 30/100`
   Si hay mezcla: `22 en nivel Alto (21 por FWI ≥ 20, 1 por puntaje)`. Renombrar "Máx" a "Puntaje máx." y añadir un botón ⓘ con: "El nivel de alerta también sube por reglas: FWI ≥ 20 → Alto; FWI ≥ 45, foco FIRMS hoy, ≥ 4 focos o FRP medio ≥ 60 MW → Crítico. Por eso un nivel Alto puede tener puntaje menor a 70."
2. Leyenda (`colorScales.js:11-45`): `Alto (puntaje 70-84, o FWI ≥ 20)` y `Crítico (85-100, o FWI ≥ 45, foco FIRMS hoy, ≥ 4 focos o FRP ≥ 60)`, con la nota visible bajo la lista y no solo en `title`.
3. Tooltip y panel de comuna (`TerritoryMapPanel.jsx:827-829`, `ComunaRiskPanel.jsx:303`): bajo el nivel, una línea "Activado por FWI 20,6" cuando el nivel no corresponde al rango del puntaje.
4. Reutilizar el texto que ya existe en `/alertas` (`AlertsReadView.jsx:362-363`), que sí explica la regla (con "supera 20", cuando el código es ≥ 20; ver D-11).
5. Decisión de fondo (equipo): alinear el backend de comunas con la escala regional (≥ 20 Preventivo, ≥ 30 Alto). Con los datos de hoy, ninguna comuna de las tres regiones tiene FWI ≥ 30, así que pasarían de Alto a Preventivo y el nivel Preventivo dejaría de ser inalcanzable por FWI.

---

## 4. Categoría 1 — Ortografía, tildes y consistencia de textos

Nota: los plurales en "-ciones" (detecciones, notificaciones, regiones, publicaciones) están bien escritos; solo se reportan singulares y palabras realmente sin tilde.

### Alta

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| O-01 | `/alertas` (pública) | `features/alerts/components/AlertsReadView.jsx`: 173, 174, 182, 206, 210, 270, 290, 298, 302, 304, 318, 320, 335, 345, 358, 360-364, 369, 370, 377, 379, 389, 408, 410, 428, 445, 451, 452, 458, 459 | Textos sin tilde: "priorizacion", "datos publicos", "ubicacion aproximada", "Filtrar por region", "Ultimos 30 dias", "Vista publica… dias… ultimos… maximo", "historico", "Criticas / Altas", "deteccion", "segun", "Que muestra", "Por que FWI…", "Como funciona", "indice meteorologico", "vegetacion", "condicion meteorologica", "escalo", "mas", "Priorizacion operativa". Además "período" (L288) frente a "periodo" (resto). | Corregir todas (priorización, públicos, ubicación, región, Últimos 30 días, máximo, histórico, Críticas, detección, según, Qué muestra, Por qué, Cómo funciona, índice, vegetación, condición, escaló, más). Unificar "periodo". | S |
| O-02 | Componentes compartidos (visibles en `/territorio`, `/alertas` y todo el CRUD) | `components/ErrorMessage.jsx`: 2, 9 · `components/EmptyState.jsx`: 1 · `components/LoadingSpinner.jsx`: 1 · `components/ConfirmModal.jsx`: 1, 13 | Valores por defecto en inglés: "An unexpected error occurred.", botón "Retry", "No data found", "Try adjusting filters or creating a new record.", "Loading...", "Confirm", "Cancel". Se ven: "Retry" en `TerritoryMapPanel.jsx:1121`; descripción en inglés en `AlertsReadView.jsx:462`, `AccessControlPage.jsx:379`, `RulesPage.jsx:403`, `RegionsPage.jsx:210`, `ForestLossPage.jsx:258`; "Cancel" en los 6 modales (`AlertsPage.jsx:233`, `RulesPage.jsx:424`, `CitizenReportsPage.jsx:493`, `ForestLossPage.jsx:277`, `RegionsPage.jsx:229`, `CommunityPage.jsx:1118`). | Cambiar defaults a "Ocurrió un error inesperado.", "Reintentar", "Sin datos", "Prueba ajustando los filtros.", "Cargando…", "Confirmar", "Cancelar". | S |
| O-03 | Estructura común (todas las páginas) | `components/layout/Footer.jsx`: 4 · `components/layout/Navbar.jsx`: 35, 72 · `components/layout/Sidebar.jsx`: 11, 12, 19, 21, 26, 28 · `components/layout/NotificationBell.jsx`: 61, 76, 80 | "Monitorizacion forestal", `aria-label="Navegacion principal"`, "Cerrar sesion", "Operacion", "Administracion", "Menu lateral", "Modulos", "Expandir/Contraer menu", "no leidas" (campana y desplegable). | Monitorización, Navegación principal, Cerrar sesión, Operación, Administración, Menú lateral, Módulos, menú, no leídas. | S |
| O-04 | Acceso y errores (públicas) | `pages/auth/LoginPage.jsx`: 44, 60, 82 · `pages/auth/ForgotPasswordPage.jsx`: 22 · `pages/auth/RegisterPage.jsx`: 63 · `pages/auth/ResetPasswordPage.jsx`: 43 · `pages/NotFoundPage.jsx`: 7 · `auth/PublicOnlyRoute.jsx`: 8 · `utils/passwordPolicy.js`: 6, 11, 16, 21, 26, 48 · `services/authService.js`: 19 | "Iniciar sesion", "No fue posible iniciar sesion", "Completa la verificacion de Cloudflare", "La pagina solicitada no existe", "Validando sesion…", reglas de contraseña ("Minimo", "mayuscula", "minuscula", "numero", "simbolo") y fortaleza "Debil", "autenticacion". | Corregir tildes (sesión, verificación, página, Mínimo, mayúscula, minúscula, número, símbolo, Débil, autenticación). | S |
| O-05 | Niveles y categorías como enum crudo | `components/AlertBadge.jsx`: 10 · `features/alerts/components/AlertsReadView.jsx`: 199, 379 · `pages/AlertsPage.jsx`: 193 · `components/layout/NotificationBell.jsx`: 91 · `features/alerts/components/AlertsOperationalMap.jsx`: 117 · `pages/CommunityPage.jsx`: 14-15, 619, 642, 710, 858, 875, 1090 · `pages/CitizenReportsPage.jsx`: 93-101, 351 | Se imprime el valor técnico: "CRITICO", "CRITICA", "GUIA", "CAPACITACION", "RECIBIDO (vencido)", mientras `/territorio` muestra "Crítico" (`colorScales.js:43`). Mezcla de mayúsculas y minúsculas en el mismo texto ("DESCARTADO (vencido)"). | Helper único `levelLabel()` / `categoryLabel()` que use las etiquetas con tilde y capitalización normal (Crítico, Crítica, Guía, Capacitación, Recibido (vencido)). | S |

### Media

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| O-06 | Panel de comuna y pie de leyenda | `features/territory/components/ComunaRiskPanel.jsx`: 80, 307, 309, 317, 343, 353, 363, 400 · `features/territory/components/TerritoryMapPanel.jsx`: 1423 | Jerga/inglés visible: "Score", insignia "STANDARD/ENHANCED", "⚠ parcial" (sin explicar), "Componentes WLC", "Componentes FWI proxy", "Sync ahora", "Error Copernicus" (sin causa visible), y "Fuente: Backend / Mock local". | "Puntaje"; "Modo estándar / con Copernicus"; "Datos parciales" con `title` visible; "Componentes del puntaje (WLC)"; "Variables meteorológicas del FWI"; "Sincronizar ahora"; mostrar el motivo del error; "Fuente: servidor / datos de demostración". | S |
| O-07 | Tooltips del mapa | `features/territory/components/TerritoryMapPanel.jsx`: 388, 444, 486 | Puntos cardinales en inglés ("SW", "W", "NW"): "12.3 km/h desde el SW". "Hectareas:" sin tilde. Decimal con punto en `toFixed` (L444) frente a es-CL. | `['N','NE','E','SE','S','SO','O','NO']`; "Hectáreas"; formatear con `Intl.NumberFormat('es-CL')`. | S |
| O-08 | Comunidad | `pages/CommunityPage.jsx`: 252, 542, 562, 575, 599, 617, 632, 649, 651, 696, 700, 717, 719, 739, 748, 794, 805, 826, 878, 906, 913, 939, 943, 960, 983, 1024, 1029, 1039, 1041, 1059, 1083, 1093, 1121 | "Coordinacion comunitaria", "Titulo", "Region", "Sin region", "Seleccione region", "asocialos", "Recargar modulo", "Catalogo de recursos", "boton", "aqui", "Telefono", "Organizacion", "Esta accion no se puede deshacer". | Aplicar tildes (Coordinación, Título, Región, asócialos, Recargar módulo, Catálogo, botón, aquí, Teléfono, Organización, acción). | S |
| O-09 | Mi cuenta | `pages/AccountPage.jsx`: 12, 103, 124, 128, 139, 144, 198, 212, 228, 251, 255, 267, 278, 281, 307 | "Telefono", "contrasena" (×9), "mayuscula", "minuscula", "numero", "simbolo", "Volveras", "verificacion", "region". | Corregir (contraseña, Teléfono, Volverás, verificación, región…). | S |
| O-10 | Control de accesos | `pages/AccessControlPage.jsx`: 192, 266, 268, 310, 375, 469, 479, 516, 561, 564, 594, 643 | "Gestion de roles", "Acceso modulo", "Configuracion guardada (pendiente de sincronizacion con backend)", "restriccion", "revision", "verificacion", "via". | Corregir tildes; cambiar "backend" por "servidor". | S |
| O-11 | Reportes ciudadanos | `pages/CitizenReportsPage.jsx`: 211, 229, 242, 259, 264, 382, 394, 451, 464, 496, 540 | "geolocalizacion", "ubicacion" (×2), "region" (×3), "maximo" (×2), "Aun", "accion", "imagenes". | Corregir tildes. | S |
| O-12 | Páginas administrativas menores | `pages/RegionsPage.jsx`: 53, 114, 117, 137, 148, 160, 231, 232 · `pages/AlertsPage.jsx`: 172, 236 · `pages/RulesPage.jsx`: 276, 279 · `pages/HomePage.jsx`: 8, 12 · `pages/ForestLossPage.jsx`: 94-96, 177, 183, 195, 204, 215, 221, 227, 280 | "Codigo", "Region actualizada/creada/eliminada", "Gestion", "Como funcionan", "Logica OR", "prevencion", "Biobio y La Araucania", cabecera "Anio", "Ha perdidas", "Perdida Forestal". `ForestLossPage` no está enrutada (`router/AppRouter.jsx`), baja prioridad. | Corregir tildes (Código, Región, Gestión, Cómo, Lógica, prevención, Biobío, La Araucanía, Año, Pérdida…). | S |
| O-13 | Dashboard (TSX) | `features/dashboard/components/AlertsBar.tsx`: 16, 22 · `AlertsDonut.tsx`: 19, 25 · `CriticalRegionsTable.tsx`: 52, 62 · `DashboardFilters.tsx`: 37 · `DashboardKpiCards.tsx`: 25, 29, 31 · `DataFreshnessChip.tsx`: 45 · `IndicatorSeriesChart.tsx`: 28, 47 · `LossTrendChart.tsx`: 26, 32, 42 · `SyncNowButton.tsx`: 21 · `WeatherSyncButton.tsx`: 31, 41 · `services/dashboardApiService.ts`: 170, 193, 217, 270, 303, 318, 326, 342, 412, 422 | "categoria", "informacion", "grafico", "criticas", "Ha perdidas", "region", "Todavia", "Periodo mas critico", "numericos", "combinacion", "perdida", "Sincronizacion". Visibles en `/territorio` con sesión. | Corregir tildes. | S |
| O-14 | Texto interno o de desarrollo visible | `pages/auth/RegisterPage.jsx`: 86 · `pages/HomePage.jsx`: 11 · `pages/CitizenReportsPage.jsx`: 326 · `pages/CommunityPage.jsx`: 547 | Subtítulo público de registro: "Registro minimo para entorno de desarrollo NoFires"; Home: "Esta iteracion prioriza…"; "Origen de datos: backend de reportes / fallback local de continuidad operativa". | Registro: "Crea tu cuenta para reportar y recibir alertas". Retirar o reescribir las otras para usuario final ("Datos en línea" / "Modo sin conexión"). | S |
| O-15 | Nombre de lo mismo: focos satelitales | `features/territory/components/TerritoryMapPanel.jsx`: 73, 82, 92, 584, 591, 606 · `features/territory/components/ComunaRiskPanel.jsx`: 14, 20, 30 · `features/territory/components/RegionalSummary.jsx`: 20 · `features/territory/utils/reportPrint.js`: 61, 67, 219 · `pages/DashboardPage.tsx`: 226, 232, 247, 275 | FIRMS aparece como "Focos activos" (chip, panel, PDF), "Detecciones FIRMS" (leyenda), "Focos FIRMS 48 h" (resumen), "DETECTADOS HOY" y "Focos FIRMS (NASA)" (dashboard). | Elegir un término ("Focos de calor FIRMS") y usarlo en chip, leyenda, panel, resumen, PDF y dashboard. | S |
| O-16 | Nombre de lo mismo: vegetación | `features/territory/components/TerritoryMapPanel.jsx`: 592, 593 frente a 583, 585, 607, 608 · `features/territory/constants/colorScales.js`: 115, 125 | Chips "Índice veg." y "Humedad veg." (abreviaturas) frente a "Índice vegetación (NDVI)" y "Humedad vegetación (NDMI)" en leyenda y panel; falta "de" ("Humedad de la vegetación"). | Chips: "NDVI (vegetación)" y "NDMI (humedad)"; completo en leyenda: "Humedad de la vegetación (NDMI)". | S |
| O-17 | Nombre de lo mismo: monitoreo | `components/layout/Navbar.jsx`: 25 · `components/auth/AuthCard.jsx`: 15 · `index.html`: 6 · `pages/RegionsPage.jsx`: 62 frente a `pages/TerritoryPage.jsx`: 47 · `components/layout/Footer.jsx`: 4 · `features/territory/utils/reportPrint.js`: 196, 333 | "Monitoreo y Alerta Temprana Forestal" / "Monitoreo Forestal" / "Monitoreo activo" frente a "Monitorización territorial" / "Monitorización forestal". La página se llama "Territorio" en el menú y "Monitorización territorial" en el título. | Unificar en "Monitorización" y alinear el título de la página con el menú ("Territorio"). | S |
| O-18 | Nombre de lo mismo: puntaje y nivel | `features/territory/components/ComunaRiskPanel.jsx`: 80 · `features/territory/components/RegionalSummary.jsx`: 19, 43 · `features/alerts/components/AlertsReadView.jsx`: 125, 150, 194, 361, 431 · `pages/AlertsPage.jsx`: 189 | "Score", "puntaje" y "riesgo" para lo mismo; encabezado "Nivel riesgo" (sin "de") frente al filtro "Nivel de riesgo". | "Puntaje de riesgo" y "Nivel de riesgo" en todas partes. | S |
| O-19 | Formato de ventanas de tiempo | `features/alerts/components/AlertsReadView.jsx`: 27, 28 · `features/territory/components/RegionalSummary.jsx`: 20, 21 | "Últimas 24h", "Últimas 48h" frente a "48 h" y "7 d"; además "Última semana" y "30 días". | Formato único: "24 h", "48 h", "7 días". | S |
| O-20 | Formato de números y rangos | `features/territory/constants/colorScales.js`: 11, 22, 33, 44, 119-121, 129, 149-153 · `features/territory/components/ComunaRiskPanel.jsx`: 27-29 | Decimales con punto ("0-0.2", "> 0.5") en NDVI/NDMI y con coma en la leyenda de área quemada ("0,5–2 %"); guion simple en rangos ("0-49") y etiqueta ambigua "-0.1-0.1 (leve)" que se lee como resta. | Coma decimal y raya de rango en todo: "−0,1 a 0,1", "0 a 0,2", "0–49". | S |

### Baja

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| O-21 | Datos de respaldo y demostración | `features/reports/hooks/useCitizenReportsData.js`: 53-55 · `features/community/hooks/useCommunityData.js`: 32, 33, 49, 53, 66, 75, 81, 94, 95, 102 · `features/territory/hooks/useTerritoryLayers.js`: 105, 150, 161, 175, 190 | Nombres sin tilde que se ven si el backend falla: "Biobio", "Nuble", "La Araucania", "Concepcion", "Alerta critica", "Guia de prevencion", "Proteccion Civil". | Corregir los literales de respaldo. | S |
| O-22 | Título de documento | `index.html`: 6 | Un único `<title>` ("NoFires - Monitoreo Forestal") para todas las rutas; no hay `document.title` por página. | Título por ruta ("Territorio · NoFires", "Alertas · NoFires"). | S |

---

## 5. Categoría 2 — Contraste y color

Razones calculadas con la fórmula WCAG 2.1 (texto normal 4,5:1; texto grande o en negrita grande y componentes de interfaz 3:1). ΔE = CIEDE2000 (por debajo de ~10 en recuadros de 10 px resultan difíciles de distinguir).

### Alta

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| C-01 | Mapa de riesgo comunal y su leyenda | `features/territory/components/TerritoryMapPanel.jsx`: 115-126, 1356-1365 · `features/territory/constants/colorScales.js`: 7-50 | Relleno con opacidad 0,34 (0,10 sin datos). Sobre tierra OSM: Preventivo/Alto ΔE 9,5; Alto/Crítico ΔE 10,6; Normal frente a tierra ΔE 4,9; "sin datos" frente a Normal ΔE 3,8 (mismo color, mismo borde). La leyenda pinta los cuadros opacos (ΔE entre niveles 26-40) y no coinciden con lo que ve el usuario. Los trazos `#78350f` / `#7c2d12` (ΔE 4,1) apenas se distinguen. | Subir la opacidad a ~0,55 para Preventivo-Crítico, dar a "sin datos" un patrón rayado gris (no azul), y dibujar los cuadros de la leyenda con la misma opacidad y trazo que el mapa. | M |
| C-02 | Flechas de viento | `features/territory/components/TerritoryMapPanel.jsx`: 193-203, 406-415 · `features/territory/constants/colorScales.js`: 70-74 | La flecha usa el mismo color que el relleno de su comuna. Contraste flecha/relleno: 1,04:1 (< 10 km/h), 1,10 (10-20), 1,31 (20-35), 2,04 (35-50), 3,07 (> 50). En las tres primeras categorías, que son las más frecuentes, la dirección es casi invisible (solo queda un `drop-shadow` de 1 px). | Flecha en color fijo oscuro (`#0f172a`) con halo blanco y la velocidad solo en el relleno. | S |
| C-03 | Texto gris claro del panel de comuna | `features/territory/territory.css`: 1047-1051 (`.panel-comp-weight`), 1053-1058 (`.panel-chart-empty`), 1075-1079 (`.panel-meta`) · `features/territory/components/ComunaRiskPanel.jsx`: 378 · `features/territory/components/TerritoryMapPanel.jsx`: 1094 | `#94a3b8` sobre `#f8fafc` = 2,45:1 (sobre blanco 2,56:1), a 9,8-11 px. Afecta "Actualizado dd/mm hh:mm", los pesos (52 %, 33 %…), "Sin historial disponible aún" y la nota "La sincronización puede tardar…". | Usar `#475569` (7,6:1 sobre blanco) o mínimo `#64748b` (4,55:1). | S |
| C-04 | Mensaje de éxito de Copernicus | `features/territory/territory.css`: 1096-1101 | `#16a34a` sobre `#f8fafc` = 3,15:1 a 10,1 px: es el resultado de la sincronización (NDVI/NDMI/modo). | `#166534` (6,8:1) y subir a 12 px. | S |
| C-05 | Tarjeta de reporte ciudadano | `features/territory/components/TerritoryMapPanel.jsx`: 635-640, 657 · `features/territory/territory.css`: 793-806, 850-853 | Sobre fondo oscuro (~`#1f2637`): "Incendio activo" `#dc2626` 3,13:1, "Otro" y la hora `#64748b` 3,17:1, categoría en 12 px negrita (no cuenta como texto grande). | Colores claros para fondo oscuro: Incendio `#f87171` (≥ 5:1), Otro/hora `#cbd5e1`. | S |

### Media

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| C-06 | Tooltips oscuros del mapa | `features/territory/components/TerritoryMapPanel.jsx`: 841 · `features/territory/territory.css`: 646-660 (`.comp-info-close`) | "Sin datos de riesgo aún" `#64748b` sobre tooltip oscuro 3,02:1; botón × de ayuda `#64748b` sobre `#1e293b` 3,07:1. | Texto `#cbd5e1` y botón × `#e2e8f0`. | S |
| C-07 | Texto secundario `#94a3b8` en otras pantallas | `styles/global.css`: 1593-1598 (`.alerts-operational-num`), 2135-2139 (`.field-optional`) · `pages/DashboardPage.tsx`: 285-286, 290, 612 | 2,56:1 (2,34:1 sobre `#f1f5f9`): posición en el ranking público de `/alertas`, "(opcional)", "Sin detecciones hoy", "comunas" (8,4 px); `#dc2626` sobre `#fef2f2` 4,41:1. | `#64748b` o más oscuro. | S |
| C-08 | Chat: presencia | `features/community/chat/ChatUserPanel.jsx`: 5-8, 48 · `features/community/chat/ChatUserPanel.css`: 25-33, 54-60, 74-80 | Iniciales en blanco sobre `#22c55e` 2,28:1, `#f59e0b` 2,15:1, `#94a3b8` 2,56:1 a 8,4 px; etiquetas de grupo y "Sin usuarios" `#94a3b8` 2,45:1 a 9,1-10,1 px. El estado se codifica por color de avatar y punto de 8 px (la etiqueta del grupo lo respalda). | Iniciales en `#0f172a` o fondos más oscuros; etiquetas `#475569`; forma distinta por estado. | S |
| C-09 | Bordes de campos y botones | `styles/global.css`: 63-72, 98-102 · `features/territory/territory.css`: 88-99, 223-237 | Borde de `input/select/textarea` `#cbd5e1` sobre blanco 1,48:1; `.btn-secondary` `#94a3b8` 2,56:1; chips de capas `#cbd5e1` 1,48:1. WCAG 1.4.11 pide 3:1 para identificar el control (los campos no tienen otra pista). Afecta también a login. | Borde `#64748b` (4,76:1) o `#94a3b8` con fondo gris suave en campos. | S |
| C-10 | Indicador de foco | `styles/layout.css`: 146-151, 255-260 | `.notification-bell-btn` y `.notification-item-btn` quitan el contorno (`outline: none`) y dejan un borde `#93c5fd` (1,80:1) o `#bfdbfe` sobre `#eff6ff` (1,31:1). Los demás controles de `/territorio` sí tienen foco `#1d4ed8` (6,7:1). | Reutilizar `outline: 3px solid #1d4ed8; outline-offset: 2px`. | S |
| C-11 | Misma paleta para significados distintos | `features/territory/components/TerritoryMapPanel.jsx`: 57, 58, 63, 64, 74, 83, 637, 638 · `features/territory/constants/colorScales.js`: 34, 96, 97, 128 · `pages/DashboardPage.tsx`: 311-313 · `pages/CitizenReportsPage.jsx`: 86-91 | `#dc2626` = capa Alertas = Temp. aire > 35 °C = NDMI "severa" = reporte Incendio (FIRMS "hoy" `#ef4444` es ΔE 7,6 de Alertas); `#f97316` = FIRMS reciente = reporte Foco = Temp. 30-35; `#b45309` = nivel Alto = Temp. del suelo. Los reportes tienen colores distintos en el mapa (Humo gris `#94a3b8`) y en el dashboard (Humo `#f59e0b`). Estados de reporte con la paleta de riesgo: "Descartado" en rojo crítico, "Recibido" en naranja alto. | Una paleta por concepto: Alertas, Focos y Reportes con tonos y formas distintas (círculo/rombo/cuadrado); estados de reporte con neutros y verde/azul. | M |
| C-12 | Mapa de `/alertas` | `features/alerts/components/AlertsOperationalMap.jsx`: 39-44, 97-125 | Alto `#dc2626` y Crítico `#b91c1c` ΔE 7,7; Preventivo cae en el `else` verde `#15803d` (como "bajo"); alerta seleccionada `#3b82f6` y reporte `#2563eb` ΔE 10,9; sin leyenda; el Alto es rojo aquí y marrón/naranja en `/territorio` y en las insignias. | Añadir leyenda, mapear Preventivo, y reutilizar `ALERT_LEVEL_CONFIG` con tonos distinguibles. | M |
| C-13 | Colores de nivel usados como texto | `features/territory/constants/colorScales.js`: 7-50 · `features/territory/components/RegionalSummary.jsx`: 38, 50 · `features/territory/components/ComunaRiskPanel.jsx`: 303, 331 · `styles/global.css`: 405-413 | Texto de Preventivo `#78350f` y Alto `#7c2d12` ΔE 4,1 (casi iguales); Crítico `#3f1d0b` se lee como negro, sin señal de alarma. Insignias Preventivo/Medio `#fef9c3`/`#fef3c7` ΔE 3,4. Contraste bueno (9-15:1), el problema es la diferenciación. | Añadir texto/ícono (▲, ●) o usar tonos separados: ámbar, naranja, rojo oscuro. | S |
| C-14 | Escalas cerca del gris "sin datos" y rangos contiguos | `features/territory/constants/colorScales.js`: 70-71, 84, 92-93, 104-105, 119-120, 147 · `constants/landcoverClasses.js`: 24 | Frente a `NEUTRAL_FILL #cbd5e1`: Humedad 50-70 `#bdc9e1` ΔE 5,6; NDVI 0-0,2 `#b3cde3` ΔE 6,0; Temp. `< 10` `#bfdbfe` ΔE 8,1; "Sin área quemada" `#f1f5f9` ΔE 8,1; "No observado" blanco ΔE 11,2. Rangos contiguos: Viento `#a7f3d0`/`#5eead4` ΔE 8,9; Temp. aire `#bfdbfe`/`#93c5fd` ΔE 8,0 y `#fb923c`/`#f97316` ΔE 7,7. Con opacidad 0,55 del relleno se atenúan aún más. | Gris con trama o borde punteado para "sin datos"; separar rangos con pasos de luminosidad ≥ ΔE 15. | M |
| C-15 | Humedad: dos escalas opuestas | `features/territory/constants/colorScales.js`: 81-85, 128-130 | Humedad relativa: crítico = azul oscuro, alto = casi blanco (`#f1eef6`, invisible sobre el mapa). NDMI: seco = rojo, húmedo = azul. Dos capas de "humedad" con codificación inversa. | Misma lógica en ambas: seco = cálido, húmedo = azul. | S |
| C-16 | Leyenda del gráfico de 30 días | `features/territory/components/ComunaRiskPanel.jsx`: 82-83, 351-353 | Las líneas de referencia usan `ALERT_LEVEL_CONFIG.PREVENTIVO.color` (`#78350f`) y `ALTO.color` (`#7c2d12`), pero la leyenda dibuja `#ca8a04` y `#ea580c`; no existe línea para Crítico (85). | Leyenda con los mismos colores y añadir la línea de 85. | S |
| C-17 | Notas del mural | `styles/global.css`: 783-786, 806-811 | `#64748b` sobre `#fee2e2` 3,90:1, `#ffedd5` 4,15:1, `#fef3c7` 4,27:1, `#dcfce7` 4,33:1 a 10,1 px (fecha y autor). | `#475569`. | S |

### Baja

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| C-18 | Cobertura del suelo (leyenda oficial) | `features/territory/constants/landcoverClasses.js`: 7-24 | Pares similares: Bosque achaparrado/Estepa ΔE 8,2; Pastizal/Pastura 8,5; Infraestructura/Otra sin vegetación 10,0; Afloramiento/Arena 10,5. Son colores oficiales de MapBiomas. | Mantener el color oficial; reforzar con nombre y porcentaje en el tooltip (ya existe). | S |
| C-19 | Marcador del chip desactivado | `features/territory/territory.css`: 257-260 | "○" `#94a3b8` sobre blanco 2,56:1; el estado se distingue igualmente por la forma (✓ frente a ○). | `#64748b`. | S |
| C-20 | Textos al límite | `styles/global.css`: 1845-1850 · `features/territory/territory.css`: 787-790 · `features/territory/utils/reportPrint.js`: 202, 340 | `.auth-brand-note` 4,35:1; insignia ENHANCED 4,45:1 a 9,1 px; "Datos calculados al" `#888` 3,54:1 a 10 px en el PDF. | Oscurecer un paso. | S |

---

## 6. Categoría 3 — Legibilidad

### Alta

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| L-01 | Texto informativo ≤ 10 px en `/territorio` | `features/territory/territory.css`: 773 (0,65 rem = 9,1 px, insignia de modo), 944 (9,1 px), 963 (0,68 rem = 9,5 px, "⚠ parcial"), 840 (0,7 rem, estado del reporte), 1050 (peso), 1062 (leyenda del gráfico), 1141 y 1152 (fuente MapBiomas y años del gráfico), 1076-1104 (0,72 rem = 10,1 px: fecha, éxito, error), 702-706, 756-769 (tooltip), 614 (icono ⓘ 10 px), 633 (ayuda 11 px) · `features/territory/components/ComunaRiskPanel.jsx`: 76, 77 (ejes 10 px), 378 · `features/territory/components/TerritoryMapPanel.jsx`: 1094 | Aviso de calidad "⚠ parcial", el modo STANDARD/ENHANCED, el año inicial y final del historial y la fecha de actualización están entre 9 y 10 px. | Mínimo 12 px (0,875 rem a base 14 px) para texto informativo; 11 px solo para decoración. | M |
| L-02 | Iconos de ayuda ⓘ | `features/territory/territory.css`: 613-622 · `features/territory/components/ComunaRiskPanel.jsx`: 168-174 · `features/alerts/components/AlertsReadView.jsx`: 286, 300, 316, 334, 344 · `styles/global.css`: 457-460 · `pages/DashboardPage.tsx`: 190 | `.comp-info-icon`: ≈ 10 × 10 px. En `/alertas` los ⓘ son `<summary>&#9432;</summary>` sin nombre accesible (se lee "circled latin small letter i"), ≈ 11 px, y el desplegable es `position:absolute` de 220 px. | Botón de 32-44 px con `aria-label="Qué significa…"`; en `/alertas`, `aria-label` en cada `summary`. | S |

### Media

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| L-03 | Escala tipográfica global | `styles/global.css`: 6-8 · `styles/layout.css`: 43, 305-311, 418-426 | Base 14 px: navegación superior 10,9 px (0,78 rem), menú lateral 11,5 px, subtítulo de marca 9,5 px (0,68 rem), chips de capas 11,2 px, leyendas 10,6 px. | Base 16 px o redefinir la escala (cuerpo 0,9375 rem, secundario ≥ 0,875 rem). | M |
| L-04 | Áreas táctiles menores a 44 px | `styles/global.css`: 84-91 (`.btn` ≈ 31 px), 110-113 (`.btn-sm` ≈ 25 px), 1452-1464 (32 px) · `styles/layout.css`: 132-144 (campana 30 px), 305-311 (`.nav-link` ≈ 25 px), 366-379 (26 px), 418-426 (≈ 26 px) · `components/DataTable.jsx`: 90-94 | Solo `/territorio` sube a 44 px en móvil (`territory.css`: 155-175, 279-283, 348-370, 598-603); los saltos globales (720/992/1180 px) no cambian tamaños. | Regla `@media (pointer: coarse)` con `min-height: 44px` para `.btn`, `.nav-link`, `.sidebar-link`, botones de tabla e iconos. | M |
| L-05 | Controles sin nombre accesible o sin teclado | `components/DataTable.jsx`: 61, 90-94 · `features/territory/components/ComunaRiskPanel.jsx`: 180-182 · `pages/DashboardPage.tsx`: 194 · `features/alerts/components/AlertsReadView.jsx`: 414-418 · `features/territory/components/TerritoryMapPanel.jsx`: 744, 759, 769, 779 | Paginación "«‹›»" sin `aria-label`; botón × de la ayuda sin nombre; `th` ordenable con `onClick` sin foco ni teclado; `li` de prioridad con `onClick`; SVG del clima con `aria-label` pero sin `role="img"`. | `aria-label` en cada control; `<button>` dentro de `th`; `role="button" tabIndex=0` o `<button>` en el `li`; `role="img"` en los SVG. | S |
| L-06 | Truncados que ocultan información | `features/territory/territory.css`: 1020-1025 (`.panel-comp-label`), 822-830 (`.report-card-desc`) · `features/territory/components/ComunaRiskPanel.jsx`: 326 · `styles/global.css`: 813-822, 1229-1234 · `pages/CommunityPage.jsx`: 857-858 · `features/community/chat/ChatUserPanel.css`: 61-68 | "Humedad vegetación (NDMI)" no cabe en la columna de 86-112 px y se corta con "…" sin `title`; la descripción del reporte ciudadano se corta a 2 líneas y la tarjeta es la única vista; títulos de recursos con elipsis sin `title`; nombres del chat con `title` solo en el botón (para el propio usuario dice "Tú"). | `title` con el texto completo, permitir 2 líneas en etiquetas, botón "Ver más" en la tarjeta de reporte. | S |
| L-07 | Tabla de componentes sin encabezados | `features/territory/components/ComunaRiskPanel.jsx`: 318-337 · `features/territory/territory.css`: 1011-1018 | Cinco columnas (etiqueta, ⓘ, barra, valor 0-100, peso %) sin títulos; el usuario no distingue "valor del componente" de "peso". | Fila de encabezado "Componente · Valor (0-100) · Peso" o `title` en cada cifra. | S |
| L-08 | Botones de distinto tamaño en la misma fila | `features/territory/components/TerritoryMapPanel.jsx`: 1083-1090 · `features/territory/territory.css`: 223-237, 312-339 · `features/alerts/components/AlertsReadView.jsx`: 215, 251-259 · `features/territory/components/ComunaRiskPanel.jsx`: 362-391 | Controles de `/territorio`: chips de 11,2 px / 28 px, selector de año 11,5 px / 28 px, "Actualizar capas" `.btn` de 14 px / ≈ 31 px. En `/alertas`: botones de periodo `.btn-sm` (11,2 px, mín. 110 px) junto a `.btn` de 14 px. Pie del panel de comuna: `.btn-sm` de 10,9 px. | Una escala: botón estándar 13-14 px con altura 32 px y variante compacta del mismo alto en toda la fila. | S |
| L-09 | Resumen regional en una sola línea | `features/territory/components/RegionalSummary.jsx`: 14-22 · `features/territory/territory.css`: 520-550 | Seis datos separados por "·" en un párrafo de 11,2 px con `overflow-wrap: anywhere`; en pantallas medias se parte en mitad de un dato. | Mostrar como pares etiqueta/valor (chips o `<dl>`), con la línea del nivel destacada. | S |
| L-10 | Más texto ≤ 10 px fuera de `/territorio` | `styles/layout.css`: 43, 85, 108, 169, 222, 268, 302 · `features/community/chat/ChatUserPanel.css`: 27, 56, 76 · `styles/global.css`: 117, 809, 1226, 1482, 1739 · `pages/DashboardPage.tsx`: 138, 286, 300, 381, 417, 612 | Subtítulo de marca 9,5 px, insignia de notificaciones 9 px, nivel de notificación 9,5 px, hora 10,1 px, avatar de chat 8,4 px, `.btn-xs` y "Eliminar" contacto 10,1 px, etiquetas del dashboard 8,4-10,1 px. | Piso de 12 px para texto informativo. | M |

### Baja

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| L-11 | Mayúsculas sostenidas | `features/territory/territory.css`: 708-714, 976-985 · `pages/DashboardPage.tsx`: 226-241 · `styles/global.css`: 153-160 | Títulos de sección y tooltips en mayúsculas con letra espaciada a 10,5 px ("DETECCIONES (7 DÍAS)", "COMPONENTES WLC"). | Mayúscula inicial y negrita. | S |
| L-12 | Ayudas largas en `/alertas` | `features/alerts/components/AlertsReadView.jsx`: 268-273, 358-364 | Párrafos de 3-5 líneas a 11-12 px en `card-subtitle` y listas con cinco reglas; los ⓘ de métricas abren cuadros de 220 px que pueden salirse en móvil (`styles/global.css`: 462-472). | Resumen de una línea y "Ver detalle". | S |
| L-13 | Informe imprimible | `features/territory/utils/reportPrint.js`: 79, 100-104, 159-161, 202, 340, 360 | Notas, pies y encabezados de tabla a 10 px. | Mínimo 11 px (8 pt) para impresión. | S |

---

## 7. Categoría 4 — Coherencia de datos mostrados

### Alta

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| D-01 | Resumen regional (`/territorio`) | `features/territory/utils/regionalSummary.js`: 41-47 · `features/territory/components/RegionalSummary.jsx`: 18-19 · `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/ComunaRiskServiceImpl.java`: 74, 477-492 | "20 en nivel alto" junto a "Máx: … 30/100" (Ñuble, datos de hoy). Causa: el nivel sube por FWI ≥ 20 (override), no por puntaje. Ver sección 3. | Texto con motivo, "Puntaje máx.", ⓘ con las reglas, tooltip "Activado por FWI". | S |
| D-02 | Leyenda de riesgo | `features/territory/constants/colorScales.js`: 11, 22, 33, 44, 45 · `features/territory/components/TerritoryMapPanel.jsx`: 1357, 1363 | "Alto (70-84)" mientras las comunas Alto muestran 21-30/100; solo Crítico tiene nota y es un `title` (no táctil). La nota de Crítico menciona solo el foco FIRMS de hoy, no FWI ≥ 45, ≥ 4 focos ni FRP ≥ 60. | Rangos con "o" más la regla, nota visible (ver sección 3). | S |
| D-03 | Informe PDF (regional y comunal) | `features/territory/utils/reportPrint.js`: 30-36, 137-142, 152-156 · `features/territory/components/ComunaRiskPanel.jsx`: 27 | Tabla "Score WLC": Normal 0-20, Preventivo 20-40, Alto 40-60, Crítico > 60; el sistema usa 50/70/85 (`colorScales.js:11-44`, `ComunaRiskServiceImpl.java:69-73`). Escala FWI 11/21/38/50 ("21-38 Alto") frente a las reglas reales (≥ 20 → Alto en comunas, ≥ 45 → Crítico): una comuna con FWI 20,6 y nivel Alto aparece como "Moderado" en la fila "FWI". | Tomar los umbrales de una constante compartida y describir los overrides. | S |
| D-04 | Reglas distintas por región y por comuna (backend) | `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/ComunaRiskServiceImpl.java`: 74, 486-487 · `Producto/backend/simfat-backend/src/main/java/com/simfat/backend/service/impl/TerritoryRiskServiceImpl.java`: 50-52, 240-248 | Mismo FWI, distinto nivel: región 20 → Preventivo, 30 → Alto; comuna 20 → Alto. Hoy Biobío regional: PREVENTIVO (0,19) y 22/33 comunas Alto. El "Estado de Alerta Regional" del PDF contradice el mapa. Sin FWI ≥ 30 en las tres regiones hoy. | Decidir una sola escala (propuesta: 20 Preventivo, 30 Alto, 45 Crítico) y reutilizarla en ambos servicios; mientras tanto, explicar la diferencia. | M |

### Media

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| D-05 | Ventanas de tiempo de FIRMS | `features/territory/components/ComunaRiskPanel.jsx`: 30 · `features/territory/components/RegionalSummary.jsx`: 20 · `features/territory/hooks/useTerritoryLayers.js`: 274-283 · `pages/DashboardPage.tsx`: 232-235 · `pages/RulesPage.jsx`: 357 | Cuatro ventanas para lo mismo: panel de comuna "últimas 24 h" (el backend usa 48 h, `ComunaRiskServiceImpl.java:230`); resumen "48 h"; capa del mapa por defecto 3 días; dashboard "DETECCIONES (7 DÍAS)" calculado con esa misma capa de 3 días (`features.length - todayCount`). La leyenda del mapa ("Detecciones FIRMS recientes N") no indica ventana. | Corregir "24 h" a "48 h", indicar la ventana en leyenda ("últimos 3 días") y etiquetar el dashboard según su dato real o ampliar el rango. | S |
| D-06 | Dos "scores" distintos | `features/alerts/components/AlertsReadView.jsx`: 345, 348, 431 | "Mayor prioridad" y "Score: N" en la lista son el puntaje de prioridad (sin escala), mientras el puntaje de riesgo de comuna es "N/100" en la misma página (L39). | Llamarlo "Prioridad" y dar la escala, o convertir a 0-100. | S |
| D-07 | "Focos activos hoy" cuenta comunas | `pages/DashboardPage.tsx`: 265-277 y 226 | El título dice "focos" pero cuenta comunas (`FIRMS_HOY` es una entidad por comuna); la tarjeta vecina "DETECTADOS HOY" sí cuenta detecciones. | "Comunas con foco hoy". | S |
| D-08 | Titulares que se contradicen en `/alertas` | `features/alerts/components/AlertsReadView.jsx`: 298-311, 330-339, 455-459 | Con alertas = 0 (hoy `/api/alerts/public?regionId=biobio` devuelve `[]`) se muestra "Sin alertas en el periodo" y "Críticas / Altas 0 / 0", mientras "Alertas operativas por comuna" lista 22 comunas en Alto. Las diferencias solo se explican en un ⓘ. | Texto fijo bajo el título: "Eventos = detecciones registradas; Comunas en alerta = nivel por FWI/puntaje". | S |
| D-09 | Enlace de notificación pierde la región | `components/layout/NotificationBell.jsx`: 50 · `router/AppRouter.jsx`: 85 | Navega a `/alerts?regionId=…`, que redirige a `/alertas` sin conservar la búsqueda (a diferencia de `/monitoreo`, que sí la conserva, L37-39 y L63). | `Navigate` con `location.search` o enlazar directo a `/alertas`. | S |

### Baja

| ID | Dónde | Archivo:línea | Problema y evidencia | Propuesta | Esf. |
|---|---|---|---|---|---|
| D-10 | Frecuencia de actualización FIRMS | `pages/DashboardPage.tsx`: 229 · `Producto/backend/simfat-backend/src/main/resources/application.properties`: 98 | El texto dice "Se actualiza cada 12 horas"; el valor por defecto del servicio es 12 h (`NasaFirmsServiceImpl.java:66`) pero el `.properties` fija `*/5` minutos. | Indicar "periódicamente" o leer la frecuencia de la configuración. | S |
| D-11 | Umbral descrito distinto al código | `features/alerts/components/AlertsReadView.jsx`: 362, 363 | "si el FWI supera 20" mientras el código usa ≥ 20 (L486). | "FWI de 20 o más". | S |

---

## 8. Limitaciones de la auditoría

- Análisis estático: no se vio la interfaz renderizada. Alturas y anchos (≈ 31 px, 10 × 10 px) son estimaciones a partir de `font-size`, `line-height` y `padding`.
- Los fondos translúcidos (tooltips, rellenos de mapa) se calcularon sobre `#f2efe9`; el resultado real depende del teselado de OpenStreetMap (agua, zonas urbanas, relieve) y de la opacidad final.
- ΔE (CIEDE2000) es una guía, no un criterio normativo; se recomienda validar C-01, C-02, C-14 y C-15 en navegador, con y sin simulación de daltonismo.
- No se probó lector de pantalla ni navegación solo con teclado; los hallazgos L-02 y L-05 se basan en el marcado.
- Los datos numéricos de la sección 3 son de las consultas del 2026-10-11 y cambian con el FWI diario; la conclusión (el corte es FWI = 20) es estable.
- Pendiente de revisión visual en navegador: puntos de quiebre móviles (640/720/992/1080/1180 px), desbordes de los popovers de ayuda, impresión del PDF, tema y zoom al 200 %.
- No se auditaron textos que llegan del backend (nombres de región, mensajes de error de la API, `label` del feature de riesgo regional "Biobio") ni los archivos `.test.*`.
- `ForestLossPage` no tiene ruta y `RegionsPage` está oculta del menú: se incluyen por cobertura, con prioridad baja.
