# MER Especifico — Direccion de Viento y Serie Horaria (Open-Meteo)

Fecha: 2026-06-19
Sprint: capa de viento + fixes dashboard/Copernicus
Estado: vigente

---

## 1. Entidades modificadas (schema evolution — sin migracion, MongoDB es schemaless)

### TerritoryWeatherObservation (MongoDB — coleccion `territory_weather_observations`)

Campos nuevos agregados al documento existente:

```
TerritoryWeatherObservation
  ... (campos existentes: regionId, observedAt, source, fwi, ffmc, dmc, dc, isi, bui, dsr,
       lat, lon, tempMax, humidityMin, windMax, precip, soilTemp, ingestedAt)

  + windDirection         Double                  -- direccion dominante del dia (grados 0-360,
                                                       convencion meteorologica: de donde viene)
  + hourlyTimestamps      List<LocalDateTime>     -- timestamps horarios (past_hours=24 +
                                                       forecast_hours=24 = 48 puntos)
  + hourlyWindSpeed       List<Double>            -- velocidad viento por hora (km/h), paralelo
                                                       a hourlyTimestamps
  + hourlyWindDirection   List<Double>            -- direccion viento por hora (grados),
                                                       paralelo a hourlyTimestamps
```

**Nota de implementacion:** las 3 listas horarias son arrays paralelos (mismo indice = mismo
punto en el tiempo) en lugar de una lista de objetos embebidos, para minimizar cambios sobre el
modelo existente. Si en una futura iteracion se necesita serializar/deserializar estos puntos
fuera del contexto de esta clase, conviene refactorizar a una lista de un tipo embebido
`HourlyWindPoint { timestamp, speed, direction }`.

**Por que no requiere migracion:** MongoDB no aplica un schema rigido — los documentos
existentes sin estos 4 campos simplemente los devuelven como `null`/vacios al deserializar, sin
romper lecturas. No hay backfill retroactivo: los documentos viejos no tendran estos campos hasta
que se vuelva a sincronizar esa comuna (cron cada 12h o sync manual).

**Indices:** sin cambios — los indices compuestos existentes (`idx_weather_region_observed_desc`,
`uk_weather_region_observed`) siguen aplicando igual, ya que los campos nuevos no participan en
ninguna query de busqueda/ordenamiento.

---

## 2. Repositorios — metodos nuevos (sin cambio de schema)

### ComunaRiskSnapshotRepository (sin cambio de schema en `ComunaRiskSnapshot`)

Metodo nuevo via `@Aggregation` (pipeline Mongo nativo, no derivado por nombre de metodo):

```java
@Aggregation(pipeline = {
    "{ $sort: { comunaId: 1, computedAt: -1 } }",
    "{ $group: { _id: '$comunaId', alertLevel: { $first: '$alertLevel' } } }",
    "{ $match: { alertLevel: { $in: ['ALTO', 'CRITICO'] } } }",
    "{ $count: 'n' }"
})
Long countComunasWithHighOrCriticalAlertLevel();
```

Reemplaza el conteo historico (`HeatAlertEvent.count()`) usado por el KPI `totalAlertas` del
dashboard — ver `2026-06-19_informe_sprint_clima_viento_y_fixes.md` seccion 2.2.

---

## 3. Relaciones

Sin cambios en relaciones logicas existentes. `TerritoryWeatherObservation.regionId` sigue
siendo un campo overloaded (puede contener un `regionId` real cuando lo escribe el sync regional
programado, o un `comunaId` cuando lo escribe el sync por comuna en `ComunaRiskServiceImpl`) —
comportamiento preexistente, no introducido en este sprint, documentado aqui porque es
relevante para entender por que `TerritoryController.windValueMap()` puede unir esta coleccion
con `ComunaInfo` usando el mismo campo.

---

## 4. Diagrama ASCII — solo coleccion afectada en este sprint

```
territory_weather_observations
  id PK
  regionId            -- region O comuna (overloaded, ver seccion 3)
  observedAt
  source
  fwi · ffmc · dmc · dc · isi · bui · dsr
  lat · lon
  tempMax · humidityMin · windMax · precip · soilTemp
  NEW windDirection           (Double)
  NEW hourlyTimestamps        (List<LocalDateTime>)
  NEW hourlyWindSpeed         (List<Double>)
  NEW hourlyWindDirection     (List<Double>)
  ingestedAt
```
