# Plan de Pruebas Manuales UI — Sprint Clima/Viento

Fecha: 2026-06-19
Asignado a: Andres
Motivo: no existe suite de tests automatizados de frontend en `simfat-web` (sin `vitest`/`jest`
configurado en `package.json`); estas validaciones deben hacerse a mano contra el ambiente con
datos reales.
Ambiente: produccion (`simfat-web` en Vercel) + backend Railway

---

## 1. Capa de viento — flechas de direccion

**Donde:** Pagina Territorio → seleccionar region Araucania → activar toggle "Viento"

- [ ] Al activar el toggle, aparecen flechas sobre las comunas (no solo el relleno de color)
- [ ] Cada flecha tiene una orientacion visualmente distinta segun la comuna (no todas apuntan
  igual) — confirma que la rotacion usa el dato real, no un valor fijo
- [ ] Pasar el mouse sobre una flecha muestra un tooltip con velocidad (km/h) y un punto cardinal
  (ej. "desde el NE")
- [ ] El color de la flecha cambia segun la velocidad (comparar una comuna con viento fuerte vs
  una con viento calmo, usando la leyenda de "Viento" que ya existe)

## 2. Slider horario de viento

**Donde:** misma vista, debajo de la barra de controles (aparece solo si el toggle Viento esta
activo)

- [ ] El slider aparece con la etiqueta "Direccion del viento — [hora]"
- [ ] Mover el slider de izquierda a derecha cambia la orientacion de las flechas en el mapa
  (no solo el texto de la etiqueta)
- [ ] El rango total del slider es de aproximadamente 48 horas, **no semanas** — si se ve una
  fecha varios dias en el futuro (ej. mas de 2 dias desde hoy), es un bug, reportar inmediato
- [ ] Cuando el slider esta en una posicion que cruza la medianoche, la etiqueta muestra fecha
  ademas de la hora (ej. "19/06 23:00"), no solo la hora sola
- [ ] El extremo derecho del slider (futuro) corresponde a pronostico, no a otra lectura pasada —
  comparar con una app de clima conocida (ej. Open-Meteo, Windy) para la misma comuna/hora, los
  valores de velocidad deberian ser razonablemente parecidos (no exactos, pero del mismo orden)

## 3. Persistencia del panel comunal al cambiar de comuna

**Donde:** Pagina Territorio, panel de detalle al hacer click en una comuna

- [ ] Click en una comuna (ej. Victoria) → click en "Confirmar con Copernicus" → esperar a que
  termine (~70s) → confirmar que aparece el resultado ENHANCED con NDVI/NDMI
- [ ] Sin cerrar nada, hacer click en OTRA comuna distinta → el panel debe cambiar a la nueva
  comuna (no debe seguir mostrando datos de Victoria)
- [ ] Volver a hacer click en Victoria → el resultado ENHANCED que se confirmo antes **debe seguir
  visible** (este era el bug original: se perdia al cambiar de comuna)
- [ ] Refrescar la pagina completa (F5) y volver a abrir Victoria → en este caso SI es esperable
  que se pierda el override en memoria (no hay persistencia entre sesiones), pero el ultimo
  snapshot guardado en backend deberia seguir reflejandose si ya paso el ciclo de sync

## 4. Boton de sync de clima (solo cuentas ADMIN)

**Donde:** Dashboard → barra superior, junto al boton "Sincronizar ahora" existente

- [ ] Con una cuenta ADMIN o SUPER_ADMIN: el boton "Sincronizar clima y viento" es visible
- [ ] Con una cuenta de rol distinto (ej. usuario verificado normal): el boton **no debe
  aparecer en absoluto** (no solo deshabilitado — oculto)
- [ ] Click en el boton (con region seleccionada en los filtros del dashboard) → aparece mensaje
  de confirmacion ("Sincronizacion de clima y viento iniciada" o similar)
- [ ] Sin region seleccionada, el boton debe mostrar un error claro en vez de fallar silenciosamente

## 5. Popup de detecciones FIRMS (cambio de Codex — validar igual)

**Donde:** Pagina Territorio → toggle "FIRMS" activo → click en un punto de deteccion

- [ ] El popup muestra FRP (potencia radiativa) con unidad MW
- [ ] El popup muestra el nivel de confianza (Alta/Nominal) con una explicacion breve, no solo la
  sigla
- [ ] El popup indica si la deteccion es de hoy o de un dia anterior
- [ ] El popup no se rompe visualmente con texto largo (probar con varios puntos distintos)

## 6. Regresion general (no relacionado directamente al sprint, pero tocado indirectamente)

- [ ] El choropleth de riesgo comunal (colores NORMAL/PREVENTIVO/ALTO/CRITICO) sigue funcionando
  igual que antes — el sprint no debia tocar esta logica, pero confirmar que no hay regresion
  visual por los cambios de CSS de Codex en la barra de controles
- [ ] Los layouts de Sidebar y MainLayout (cambios de Codex) se ven correctamente en al menos 2
  resoluciones distintas (desktop ancho y una ventana mas angosta tipo laptop 13")

---

## Como reportar hallazgos

Para cada item marcado como fallido, anotar: pagina, navegador, comuna/region usada, y si es
posible un screenshot. Enviar a David para triage antes de marcar el sprint como cerrado en el
checklist QA (`2026-06-19_checklist_qa_estado_actual_cu01_cu15_v2.md`).
