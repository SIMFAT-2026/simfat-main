package com.simfat.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "comunas")
public class ComunaInfo {

    @Id
    private String id;

    private String nombre;
    private String provincia;

    @Indexed
    private String regionId;

    private String regionGadm;
    private String gadmGid;

    private Double centerLat;
    private Double centerLon;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getRegionGadm() { return regionGadm; }
    public void setRegionGadm(String regionGadm) { this.regionGadm = regionGadm; }

    public String getGadmGid() { return gadmGid; }
    public void setGadmGid(String gadmGid) { this.gadmGid = gadmGid; }

    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }

    public Double getCenterLon() { return centerLon; }
    public void setCenterLon(Double centerLon) { this.centerLon = centerLon; }
}
