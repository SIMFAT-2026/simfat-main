# Estado de la integración MapBiomas — 10 de octubre de 2026

Cambio SDD: `mapbiomas-integration` · Premio MapBiomas Chile 2026, Categoría 2.

| Hito | Fecha |
| --- | --- |
| Corte H1 | 12 oct |
| Feature freeze | 17 oct |
| Entrega | 27 oct |

Criterios de evaluación: pertinencia 50 %, consistencia 20 %, impacto 20 %, validación externa 10 %.

---

## 1. Resumen en una línea

El motor, los datos, el estudio preregistrado y las capas BURNED y año ya están construidos y revisados. **Nada de la cadena está mergeado a `main` todavía.** Falta LANDCOVER, el detalle por comuna, la pasada de UX, el informe y decidir el peso en producción.

---

## 2. Cómo funciona el score (para no confundirse)

```
score = clamp((1 − wM)·S_dyn + wM·S_mb)
S_mb  = 0.65·combustible + 0.35·historial
historial = clamp(0.60·burnedNorm + 0.40·recurrenceNorm) · recencia   (recencia = 1, R_MAX = 0)
```

- **S_dyn** es el riesgo dinámico actual: FWI y clima.
- **S_mb** es la susceptibilidad estructural de MapBiomas:
  - el combustible sale de la cobertura LULC Col2 más reciente;
  - el historial sale de Fuego Col1 **acumulado 2013–2025**, no de un año suelto.
- **wM = `MAPBIOMAS_WEIGHT`, que hoy vale 0.0.** En local y en producción MapBiomas **no entra al score**.

**Riesgo + Área quemada en el mapa son dos capas superpuestas, no un score combinado.** El slider de año solo cambia la capa BURNED; el score de riesgo no depende del año elegido. La línea de área quemada del tooltip de riesgo es contexto, no un componente del cálculo. La combinación igual suma información visual (riesgo actual junto a la quema de un año), pero hay que decirlo explícitamente en la UI y en el informe.

---

## 3. Hallazgos del estudio

### Backtest preregistrado (corrida única, commit e4b12de)

- **Regla decidida:** el mayor wM en 0.50 → 0.10 cuyo IC inferior sea ≥ −0.02 es "no inferior". Solo se puede decir "mejora" si el IC inferior es > 0.
- **Resultado:** wM = 0.50, **no inferior**. Diferencia +0.017, IC [−0.012, 0.045]. **No se puede afirmar mejora.**
- **Advertencia honesta:** el baseline está cerca del azar o por debajo. AP 0.762 vs prevalencia 0.805, AUC 0.41, Spearman −0.18. MapBiomas acerca las métricas al azar (AUC 0.44).
- **Decisión:** se respeta el resultado preregistrado y se reporta tal cual. Cambiarlo a posteriori sería *forking paths*.

### H1 (Fuego vs FIRMS)

- Se detectaron **332 de 337 cicatrices ≥ 250 ha (98.5 %, IC [96.9, 99.5])**.
- Spearman 0.518.
- Los dos jueces confirmaron las salidas.

### Datos

- **LULC Col2 (1999–2024):**
  - Gates de honestidad aprobados: área ±15 % (mediana 1.0007), duplicados ≤ 15 % (peor caso 3.96 %).
  - Solo clases hoja.
- **Fuego Col1 (2013–2025):**
  - 16 rasters con sha256 verificado.
  - La unión coincide con `accumulated_burned_v1` (diferencia 1e-16).
- **Visual:** el mapa refleja bien las temporadas 2017 y 2023 (megaincendios) frente a 2025.

### Desviaciones

- `DEVIATIONS.md` registra D1–D2 y N1–N23, en modo append-only.
- N23 cubre el hueco de la tabla DST de Chile antes de 2016-08, con impacto cero.

---

## 4. Estado de la cadena de PRs (todo abierto, nada en `main`)

| PR | Contenido |
| --- | --- |
| #27–#31 | FWI canadiense, estado, wiring, sync monitoreado, spin-up, tabla de umbrales |
| #32–#36 | Servicio de susceptibilidad, fracciones del pipeline, blend de riesgo, capas públicas |
| #37–#38 | Seed de cobertura y Fuego en todos los años |
| #39 | Preregistro (v1.0, sha256 541b9266…) |
| #40–#41 | Insumos del estudio, motor del backtest y corrida |
| #42 | H1 Fuego vs FIRMS |
| #44–#45 | S3-0 capa LANDCOVER (backend) y S3-1 plumbing frontend |
| #46 | S3-2 capa BURNED (choropleth, leyenda, tooltip, fix de toggles) |
| #47 | S3-3 fila de control de año |
| #43 | **Aparte, contra `main`:** V7 RLS de Supabase (verificado seguro, falta tu OK) |

---

## 5. Pendientes

### Antes del freeze (17 oct)

- [ ] **S3-4:** capa LANDCOVER con la paleta oficial. Se suma una entrada al control de año.
- [ ] **Pasada de UX** (después de S3-4):
  - Agrupar los toggles (Riesgo y alertas / Satélite / MapBiomas).
  - Agregar descripciones cortas.
  - Agregar una **nota de que el riesgo es actual y no depende del año elegido**.
  - Corregir "Region" sin tilde en Alertas, Reportes, Reglas y Pérdida forestal.
- [ ] **S3-5:** ruta de detalle `GET /api/territory/public/mapbiomas/{comunaId}`.
- [ ] **S3-6:** sección MapBiomas en el panel de comuna, con handlers de clic.
- [ ] **Merge de la cadena a `main` de abajo hacia arriba**, empezando antes del freeze. Despliega a Railway y Vercel, así que necesita tu OK explícito en cada paso.
- [ ] **PR #43** (V7 RLS): mergear con tu OK.

### Después del freeze, antes de la entrega

- [ ] **H3:** requiere los rasters `annual_burned_coverage`.
- [ ] **Recalibración de umbrales (Tabla 3 / §14):** requiere snapshots de producción que consultás vos.
- [ ] **Figuras e informe S5:** en español, ≤ 10.000 palabras, con el resultado no inferior contado honestamente.
- [ ] **S6:** admin.
- [ ] **Anexo 1:** firmado solo por David.

---

## 6. Decisiones abiertas

1. **¿Activar `MAPBIOMAS_WEIGHT` en producción?**
   - El estudio respalda wM = 0.50 solo como *no inferior*, con un baseline débil.
   - Dejarlo en 0 deja MapBiomas solo como capas de contexto, lo que **debilita la "pertinencia" (50 %)**.
   - Activarlo en 0.50 hace que MapBiomas sea un insumo principal, sin prometer mejora.
2. **Cómo comunicar Riesgo + Área quemada en la UI:** el texto exacto de la nota y dónde va (tooltip, leyenda o ambos).
3. **Orden y fecha para mergear la cadena a `main`:** una pasada controlada, idealmente antes del 17 oct.
4. **Tests de componentes con DOM** (jsdom y testing-library): ¿se agregan las dependencias o seguimos con helpers puros y prueba manual?
5. **Rangos de años por región:** hoy todas las regiones comparten 2013–2025. Solo importa si eso cambia.

---

## 7. Reglas de trabajo vigentes

- SDD interactivo, engram y TDD estricto.
- Revisión ciega con dos jueces antes de cada PR.
- Suite backend completa con Mongo local antes de cada PR de backend.
- Commits convencionales sin atribución a IA.
- Nada de push ni merge sin OK explícito.
- Nunca stagear `Documentacion/PORTAFOLIO_PLAN_NoFires.md`.
- Contenedores locales: solo `stop`, nunca `rm`.
