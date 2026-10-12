# Handoff — integración MapBiomas (sesión siguiente)

Fecha de cierre: 2026-10-12 · Cambio SDD: `mapbiomas-integration` · Premio MapBiomas Chile 2026, Categoría 2.

Hitos: **feature freeze 17 oct** · entrega **27 oct** · informe ≤ 10.000 palabras en español.

> Para retomar en una conversación nueva: leer este archivo, luego `mem_context` y `mem_search "release/mapbiomas-integration-deploy"` en engram (proyecto `simfat-main`).

---

## 1. Estado actual

- **Todo está en producción.** PR #53 (`feature/mapbiomas-integration → main`) mergeado con merge commit `767caa6` el 2026-10-12. La cadena #22–#52 se colapsó en el tracker con merge commits.
- **Rollback:** revertir el merge commit `767caa6`.
- **URLs:**
  - Frontend: https://nofires-latam.vercel.app (proyecto Vercel `nofires-web`; también https://simfat-web-stg.vercel.app).
  - Backend: https://simfat-backend-production.up.railway.app
- **Smoke test de producción (OK):** `layers` BURNED/LANDCOVER con año, `GET /api/territory/public/mapbiomas/{comunaId}`, cita con "Colección" en UTF-8, `Cache-Control: public, max-age=300`, 400 con id inválido, `alertDriver` presente en `/api/territory/risk-score/comunas/{regionId}` (null hasta el próximo recálculo).
- **Verificación previa al merge:** suite backend 499 tests OK (`mvn -o clean test`), frontend 306 tests + lint + build OK.

## 2. Pendientes, en orden

1. **#43 — RLS de Supabase (migración Flyway V7)**, como despliegue separado. Rama `fix/supabase-rls-v7` (worktree `C:/wt/rls`). Ya verificado: las 13 tablas públicas son de `postgres` y Railway usa `POSTGRES_USER=postgres.jfgciagihzcvmbcvfexr`. Mergear con merge commit (no rebase) y comprobar en Supabase que los advisors de seguridad queden en verde.
2. **`MAPBIOMAS_WEIGHT`: decidir si se activa en producción.** Hoy vale 0 (MapBiomas solo como contexto visual). El backtest preregistrado respalda wM = 0,50 solo como *no inferior* (dif +0,017, IC [−0,012; 0,045]) con un baseline débil (AUC 0,41). Pertinencia vale 50 % y pide MapBiomas como insumo principal. Decisión del usuario; si se activa, es una variable de Railway (no requiere código) y la UI ya explica el peso.
3. **Capa de "factor dominante" + tooltip con todas las variables.** Propuesta aceptada en principio: colorear cada comuna por el componente que más aporta a su puntaje (FWI/clima, NDMI, FIRMS, reportes) y mostrar en el tooltip todas las variables (FWI, temperatura, humedad, viento, NDVI, NDMI). Evitar índices inventados por el usuario. "Condiciones actuales" debería ser exclusivo (una coropleta a la vez); las flechas de viento se pueden combinar.
4. **Informe S5 para el Premio** (español, ≤ 10.000 palabras): contar el resultado no inferior con honestidad; aclarar que el riesgo es actual y no depende del año elegido; explicar la regla FWI ≥ 20 → Alto y el `alertDriver`; accesibilidad (paleta probada con daltonismo simulado).

### Pendientes menores
- Agregar `nofires-latam.vercel.app` como **dominio del proyecto** `nofires-web` (hoy fue un alias manual reapuntado; un próximo despliegue podría dejarlo atrás).
- Badges del tooltip y del panel de comuna muestran "Normal" para un nivel desconocido; el mapa ya lo pinta como "sin datos".
- Hallazgos medios/bajos de la auditoría fuera de Territorio: `Documentacion/Informes/2026-10-11_auditoria_detalles_ux.md`.
- Unificar el vocabulario de `alertDriver` regional (`FIRMS_HOY`, `SCORE_WLC`) con el comunal.
- H3 (requiere rasters `annual_burned_coverage`) y recalibración de umbrales FWI (Tabla 3 / §14, requiere snapshots de producción que consulta el usuario).
- Actualizar el runbook local: promover un usuario a ADMIN en la columna legacy `roles` ya no da permisos (el RBAC moderno devuelve `roleCodes: []`).
- Detectado al documentar (verificar antes de actuar):
  - Un `regionId` desconocido en `/api/territory/public/layers` cae silenciosamente a `biobio`.
  - `FwiThresholdTable` carga `fwi-thresholds-v1.json`, pero nadie lo consume; `ComunaRiskServiceImpl` usa constantes (20/45/50).
  - El cron comunal se lee de `territory.riesgo.comunal.cron` (default `0 30 1,13 * * *`), que no está declarado en `application.properties`.
  - El endpoint de puntajes por comuna expone `alertDriver`, pero no los campos MapBiomas; el peso efectivo sale del endpoint de detalle.

## 3. Decisiones vigentes (no reabrir sin motivo)

| Tema | Decisión |
|---|---|
| Resultado del backtest | Se respeta el preregistro: wM = 0,50 "no inferior"; no se puede afirmar mejora. |
| Regla de alerta comunal | Se mantiene FWI ≥ 20 → Alto (`FWI_ALTO`); la UI separa puntaje de nivel y muestra el disparador. No mover umbrales sin datos de producción. |
| Resumen regional | Derivado de los puntajes por comuna (mismo modelo del mapa); el puntaje regional antiguo (FWI en un solo punto) no se muestra. |
| Paleta de riesgo | Azul petróleo `#1f78a0` → amarillo `#fff176` → naranjo `#e6550d` → vino `#7f0016`; "sin datos" gris punteado. Prohibido verde vs rojo/naranjo (daltonismo). Tests de CVD en `riskPalette.test.js`. |
| Controles de capas | Chips en línea "Riesgo y eventos" y "MapBiomas"; "Condiciones actuales" en popover "Más capas"; selector de año en línea `[‹][año ▾][›] ↺`. |
| Capa MapBiomas al cambiar año | Se mantiene la capa anterior atenuada mientras carga; leyenda y tooltips siempre muestran el año de los datos visibles. |
| Hotfix de `/comunidad` | Salió junto con la cadena (no por separado). |

## 4. Reglas de trabajo

- SDD interactivo, engram, TDD estricto (ver el test fallar antes de implementar).
- Revisión ciega con dos jueces + agente de fixes antes de cada PR; suite backend completa con Mongo local antes de PRs de backend.
- Commits convencionales **sin** atribución a IA. Nunca stagear `Documentacion/PORTAFOLIO_PLAN_NoFires.md`.
- Sin push ni merge sin OK explícito. Los merges los ejecuta el usuario (el clasificador de Claude Code los bloquea). Método: **merge commit**, nunca rebase/squash en cadenas.
- Contenedores locales `simfat-mongo-test` / `simfat-postgres-test`: solo `stop`, nunca `rm`. Sin acceso a `.env*` ni a credenciales.
- Artefactos en inglés (código, PRs); documentación del repo y UI en español con tildes.
- Experimentos de UI en commits únicos y revertibles.

## 5. Entorno local (para pruebas en navegador)

- Contenedores: `docker start simfat-mongo-test simfat-postgres-test`.
- Backend (puerto 8081, el 8080 lo ocupa fluentia): ver `Documentacion/Informes/2026-06-19_runbook_entorno_local_docker.md`. Tras `TaskStop`, matar el `java.exe` huérfano que retiene el 8081 (verificar que su línea de comando contenga `SimfatBackendApplication`).
- Frontend: `VITE_API_URL=http://localhost:8081 npm run dev -- --port 5173 --strictPort` en `Producto/frontend/simfat-web`.
- Usuario de prueba: `POST /api/auth/dev/seed-users` (perfil `dev`).

## 6. Documentos relacionados

- Estado y decisiones abiertas: `Documentacion/Informes/2026-10-10_estado_integracion_mapbiomas.md`.
- Auditoría UX: `Documentacion/Informes/2026-10-11_auditoria_detalles_ux.md`.
- Modelo de datos: `Documentacion/MER/2026-10-12_mer_integracion_mapbiomas_v1.md`.
- Arquitectura: `Documentacion/UML/2026-10-12_arquitectura_integracion_mapbiomas_v1.md`.
- Estudio: `Producto/analytics/study/PREREGISTRATION.md`, `DEVIATIONS.md`, `outputs/`.
