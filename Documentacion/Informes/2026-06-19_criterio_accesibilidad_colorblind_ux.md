# Criterio de Accesibilidad — Paleta de Colores para Daltonismo (UX)

Fecha: 2026-06-19
Origen: solicitud de Jennifer (Directora AIFBN)
Estado: **especificacion para implementar — NO implementado en este sprint**
Asignado a: Codex
Alcance: solo frontend (`Producto/frontend/simfat-web`), capas visuales del mapa territorial y
del dashboard que comunican nivel de riesgo o intensidad mediante color

---

## 1. Por que importa (contexto, no opcional)

Aproximadamente 1 de cada 12 hombres (~8%) y 1 de cada 200 mujeres tiene algun tipo de
deficiencia en la vision del color (CVD — color vision deficiency). Las mas comunes son
**deuteranopia** y **protanopia** (confusion rojo-verde), seguidas de **tritanopia** (azul-amarillo,
mucho mas rara). En un sistema cuyo proposito central es comunicar **nivel de riesgo de
incendio**, que un usuario con CVD no pueda distinguir "NORMAL" de "ALTO" no es un detalle
cosmetico — es una falla funcional del sistema.

## 2. Paletas actuales con problema confirmado (rojo-verde)

Estas paletas existen HOY en el codigo (duplicadas entre `TerritoryMapPanel.jsx` y
`ComunaRiskPanel.jsx` — ver seccion 5) y usan progresiones rojo→amarillo→verde, el patron mas
problematico para deuteranopia/protanopia:

### `ALERT_LEVEL_CONFIG` (la mas critica — es el nivel de riesgo principal)

```
NORMAL:     #16a34a  (verde)
PREVENTIVO: #ca8a04  (amarillo/ambar)
ALTO:       #ea580c  (naranja)
CRITICO:    #dc2626  (rojo)
```

Riesgo real: con deuteranopia, NORMAL (verde) y CRITICO (rojo) pueden percibirse con luminancia
similar y tono confuso; ALTO y CRITICO (naranja vs rojo) son aun mas dificiles de diferenciar
porque estan en el mismo rango de tono.

### `VEGETATION_SCALES.NDVI`

```
< 0:      #dc2626  (rojo)
0-0.2:    #f97316  (naranja)
0.2-0.5:  #facc15  (amarillo)
> 0.5:    #16a34a  (verde)
```

Mismo problema: 4 categorias en el eje rojo-naranja-amarillo-verde.

### `CLIMATE_SCALES.HUMIDITY`

```
< 20:  #dc2626 (rojo, critico)
20-30: #f97316 (naranja)
30-50: #facc15 (amarillo)
50-70: #86efac (verde claro)
> 70:  #16a34a (verde)
```

Mismo patron, 5 categorias.

## 3. Paletas que probablemente ya son razonables (validar igual, no asumir)

- `CLIMATE_SCALES.WIND` (gradiente verde-agua → azul oscuro) y `CLIMATE_SCALES.AIR_TEMP`
  (azul → amarillo → naranja → rojo) evitan el eje rojo-verde puro. Igual deben pasar por el
  simulador (seccion 4) antes de darlas por buenas — "probablemente bien" no es lo mismo que
  "verificado".
- `VEGETATION_SCALES.NDMI` (rojo → amarillo → azul) tambien evita la confusion rojo-verde directa.

## 4. Criterio de aceptacion (testeable, no prescriptivo de colores exactos)

No se exige una paleta especifica de reemplazo — se exige que el resultado final cumpla:

1. **Verificacion con simulador de CVD obligatoria** antes de dar por cerrada cualquier paleta
   tocada. Herramientas sugeridas: Chrome DevTools → Rendering → "Emulate vision deficiencies"
   (soporta protanopia, deuteranopia, tritanopia, achromatopsia directamente en el navegador) o
   el simulador online Coblis. Cada paleta debe revisarse en al menos protanopia y deuteranopia.
2. **No depender solo del tono (hue) para distinguir categorias adyacentes en el eje de riesgo**
   (`ALERT_LEVEL_CONFIG` es prioridad 1). Opciones validas, no son mutuamente excluyentes:
   - Cambiar a una paleta secuencial/divergente validada para CVD (ej. esquemas "ColorBrewer"
     con la opcion "colorblind safe" activada — https://colorbrewer2.org)
   - Agregar una señal visual redundante que no dependa del color: icono, patron de relleno
     (rayado vs solido), o el texto del nivel siempre visible (ya existe parcialmente:
     `panel-level`, `risk-score-level` muestran el texto "Alto"/"Critico" junto al color — pero
     el choropleth del MAPA (`comunaBaseStyle`) solo usa el relleno de color, sin texto. Esa es
     la superficie de mayor riesgo real.
3. **Contraste de texto sobre fondo de color (WCAG AA minimo 4.5:1)** donde haya texto/iconos
   sobre un relleno de color (ej. `panel-score-row`, badges de nivel). Verificar con cualquier
   checker de contraste (ej. WebAIM Contrast Checker) tras elegir la paleta final.
4. **Leyenda siempre visible y no solo dependiente de color**: las leyendas actuales
   (`territory-legend`, `RISK_SCORE_LEGEND`) ya combinan color + texto — mantener ese patron,
   no regresar a leyendas solo-color.

## 5. Nota tecnica para quien implemente (Codex)

`ALERT_LEVEL_CONFIG` y los objetos de `CLIMATE_SCALES`/`VEGETATION_SCALES` estan **duplicados**
entre `Producto/frontend/simfat-web/src/features/territory/components/TerritoryMapPanel.jsx` y
`.../ComunaRiskPanel.jsx`. Si se actualiza la paleta en un archivo y no en el otro, quedan
inconsistentes (el panel comunal mostraria un color distinto al choropleth del mapa para el
mismo nivel). Se sugiere, al tocar esto, extraer ambas constantes a un modulo compartido (ej.
`src/features/territory/constants/colorScales.js`) en el mismo cambio — no es estrictamente
parte del criterio de accesibilidad, pero evita que el fix se rompa por drift entre archivos en
la proxima iteracion.

## 6. Fuera de alcance de este criterio

- Daltonismo en mapas de calor de terceros (ninguno en uso actualmente — ver discusion previa
  sobre Windy API, descartada).
- Modo de alto contraste general / lectores de pantalla — no fue parte de la solicitud de
  Jennifer; si AIFBN lo pide a futuro, requiere un criterio separado.
