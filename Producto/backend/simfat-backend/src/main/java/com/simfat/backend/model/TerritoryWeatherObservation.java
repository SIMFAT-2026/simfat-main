package com.simfat.backend.model;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "territory_weather_observations")
@CompoundIndexes({
    @CompoundIndex(name = "idx_weather_region_observed_desc", def = "{'regionId': 1, 'observedAt': -1}"),
    @CompoundIndex(name = "uk_weather_region_observed", def = "{'regionId': 1, 'observedAt': 1}", unique = true)
})
public class TerritoryWeatherObservation {

    @Id
    private String id;

    private String regionId;
    private LocalDateTime observedAt;
    private String source;

    private Double fwi;
    private Double ffmc;
    private Double dmc;
    private Double dc;
    private Double isi;
    private Double bui;
    private Double dsr;

    private Double lat;
    private Double lon;

    // Climate variables (Slice A - Frente 1)
    private Double tempMax;
    private Double humidityMin;
    private Double windMax;
    private Double precip;
    private Double soilTemp;

    // Dominant wind direction for the day (degrees, 0-360, meteorological
    // convention: direction the wind blows FROM). Feeds the static wind
    // arrow overlay on the territory map.
    private Double windDirection;

    // Hourly wind series for "today" (past_hours backfill + remaining forecast
    // hours), parallel arrays indexed by hour. Powers the time slider that lets
    // users scrub through how wind direction shifted during the day.
    private List<LocalDateTime> hourlyTimestamps;
    private List<Double> hourlyWindSpeed;
    private List<Double> hourlyWindDirection;

    private LocalDateTime ingestedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Double getFwi() { return fwi; }
    public void setFwi(Double fwi) { this.fwi = fwi; }

    public Double getFfmc() { return ffmc; }
    public void setFfmc(Double ffmc) { this.ffmc = ffmc; }

    public Double getDmc() { return dmc; }
    public void setDmc(Double dmc) { this.dmc = dmc; }

    public Double getDc() { return dc; }
    public void setDc(Double dc) { this.dc = dc; }

    public Double getIsi() { return isi; }
    public void setIsi(Double isi) { this.isi = isi; }

    public Double getBui() { return bui; }
    public void setBui(Double bui) { this.bui = bui; }

    public Double getDsr() { return dsr; }
    public void setDsr(Double dsr) { this.dsr = dsr; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public LocalDateTime getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(LocalDateTime ingestedAt) { this.ingestedAt = ingestedAt; }

    public Double getTempMax() { return tempMax; }
    public void setTempMax(Double tempMax) { this.tempMax = tempMax; }

    public Double getHumidityMin() { return humidityMin; }
    public void setHumidityMin(Double humidityMin) { this.humidityMin = humidityMin; }

    public Double getWindMax() { return windMax; }
    public void setWindMax(Double windMax) { this.windMax = windMax; }

    public Double getPrecip() { return precip; }
    public void setPrecip(Double precip) { this.precip = precip; }

    public Double getSoilTemp() { return soilTemp; }
    public void setSoilTemp(Double soilTemp) { this.soilTemp = soilTemp; }

    public Double getWindDirection() { return windDirection; }
    public void setWindDirection(Double windDirection) { this.windDirection = windDirection; }

    public List<LocalDateTime> getHourlyTimestamps() { return hourlyTimestamps; }
    public void setHourlyTimestamps(List<LocalDateTime> hourlyTimestamps) { this.hourlyTimestamps = hourlyTimestamps; }

    public List<Double> getHourlyWindSpeed() { return hourlyWindSpeed; }
    public void setHourlyWindSpeed(List<Double> hourlyWindSpeed) { this.hourlyWindSpeed = hourlyWindSpeed; }

    public List<Double> getHourlyWindDirection() { return hourlyWindDirection; }
    public void setHourlyWindDirection(List<Double> hourlyWindDirection) { this.hourlyWindDirection = hourlyWindDirection; }
}
