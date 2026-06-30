package com.simfat.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
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

    /**
     * Comuna polygon used for sync-time point-in-polygon attribution of FIRMS
     * detections ($geoIntersects). Nullable: a comuna whose source GeoJSON
     * feature fails structural validation at seed time is persisted without
     * geometry rather than aborting startup (see MonitoredComunasConfig).
     * The 2dsphere index is sparse by virtue of the field being nullable —
     * comunas without geometry are simply absent from the index.
     */
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonMultiPolygon geometry;

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

    public GeoJsonMultiPolygon getGeometry() { return geometry; }
    public void setGeometry(GeoJsonMultiPolygon geometry) { this.geometry = geometry; }
}
