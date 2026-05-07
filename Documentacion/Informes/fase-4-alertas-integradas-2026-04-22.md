# Fase 4 - Alertas Integradas

- Fecha: 2026-04-22
- Version: 1.0
- Repositorio: `simfat-web`

## Alcance implementado

1. Integracion de Alertas con contexto territorial:
   - Mapa operativo dentro de `AlertsPage` con capas de alertas y reportes ciudadanos.
   - Priorizacion territorial por score (riesgo + reportes cercanos + recencia).

2. Filtros compartidos:
   - Region, nivel de riesgo y rango de fechas (`desde/hasta`).
   - Boton de salto a territorio manteniendo contexto (`/territorio?regionId=...&focus=alerts`).

3. Estado URL sincronizado:
   - `AlertsPage` guarda filtros en query params (`regionId`, `level`, `from`, `to`).
   - Permite compartir una vista exacta para coordinacion operativa.

4. Insights QA/operacion:
   - Chips metricos en alertas:
     - alertas filtradas
     - criticas/altas
     - reportes totales/validados
     - regiones con alertas
     - score de mayor prioridad

## Archivos modificados clave

- `src/pages/AlertsPage.jsx`
- `src/features/alerts/components/AlertsOperationalMap.jsx`
- `src/services/alertsService.js`
- `src/api/endpoints.js`
- `src/pages/TerritoryPage.jsx`
- `src/features/territory/hooks/useTerritoryLayers.js`

## Contrato backend esperado (siguiente sprint)

- `GET /api/alerts/map?regionId=&from=&to=&level=`
  - Respuesta minima: lista de alertas georreferenciadas (`latitud`, `longitud`, `nivelRiesgo`, `fechaEvento`).

Nota:
- El frontend ya incluye fallback controlado para continuidad cuando `alerts/map` no este disponible.

## QA ejecutado en esta fase

```bash
npm run lint
npm run build
```

Resultado: ambos comandos exitosos.

## Actualizacion adicional de cierre (misma iteracion)

- Se mejoro experiencia de `Reportes ciudadanos`:
  - se reemplazo visualizador embebido por galeria modal con miniaturas;
  - al hacer click en miniatura se muestra imagen en tamano completo.
- Se fortalecio continuidad de persistencia:
  - frontend reintenta creacion de reporte sin adjuntos si falla envio con archivos;
  - backend agrega tolerancia a fallo de upload para no perder registro principal.
- Pendiente operacional declarado:
  - completar configuracion correcta de variables Supabase Storage en entorno local/evaluacion.
