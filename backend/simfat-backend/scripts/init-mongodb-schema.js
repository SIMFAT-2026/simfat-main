// ==========================================
// Simfat Backend - MongoDB schema (Dashboard/OpenEO)
// Idempotent script for mongosh
// ==========================================

const dbName = process.env.MONGODB_DB || "simfat";
const targetDb = db.getSiblingDB(dbName);

function ensureCollectionWithValidator(name, validator) {
  const exists = targetDb.getCollectionInfos({ name }).length > 0;
  if (!exists) {
    targetDb.createCollection(name, { validator });
    print(`Created collection: ${name}`);
  } else {
    targetDb.runCommand({
      collMod: name,
      validator,
      validationLevel: "moderate",
      validationAction: "error"
    });
    print(`Updated validator: ${name}`);
  }
}

function ensureIndex(collection, keys, options = {}) {
  targetDb.getCollection(collection).createIndex(keys, options);
}

ensureCollectionWithValidator("regions", {
  $jsonSchema: {
    bsonType: "object",
    required: ["nombre", "codigo", "zona", "hectareasBosqueReferencia"],
    properties: {
      nombre: { bsonType: "string", maxLength: 120 },
      codigo: { bsonType: "string", maxLength: 20 },
      zona: { bsonType: "string", maxLength: 50 },
      hectareasBosqueReferencia: { bsonType: ["double", "int", "long", "decimal"], minimum: 0 },
      aoiBbox: {
        bsonType: ["array", "null"],
        items: { bsonType: ["double", "int", "long", "decimal"] },
        minItems: 4,
        maxItems: 4
      }
    }
  }
});

ensureCollectionWithValidator("alert_rules", {
  $jsonSchema: {
    bsonType: "object",
    required: ["nombre", "umbralPorcentajePerdida", "umbralEventosCalor", "activa"],
    properties: {
      nombre: { bsonType: "string", maxLength: 120 },
      regionId: { bsonType: ["string", "null"] },
      umbralPorcentajePerdida: { bsonType: ["double", "int", "long", "decimal"], minimum: 0 },
      umbralEventosCalor: { bsonType: ["int", "long"], minimum: 0 },
      activa: { bsonType: "bool" }
    }
  }
});

ensureCollectionWithValidator("forest_loss_records", {
  $jsonSchema: {
    bsonType: "object",
    required: ["regionId", "anio", "hectareasPerdidas", "fuente", "fechaRegistro"],
    properties: {
      regionId: { bsonType: "string" },
      anio: { bsonType: ["int", "long"], minimum: 1900 },
      hectareasPerdidas: { bsonType: ["double", "int", "long", "decimal"], minimum: 0 },
      porcentajePerdida: { bsonType: ["double", "int", "long", "decimal", "null"], minimum: 0 },
      fuente: { bsonType: "string", maxLength: 120 },
      fechaRegistro: { bsonType: "date" }
    }
  }
});

ensureCollectionWithValidator("heat_alert_events", {
  $jsonSchema: {
    bsonType: "object",
    required: ["regionId", "fechaEvento", "nivelRiesgo", "latitud", "longitud", "fuente"],
    properties: {
      regionId: { bsonType: "string" },
      fechaEvento: { bsonType: "date" },
      nivelRiesgo: { enum: ["BAJO", "MEDIO", "ALTO", "CRITICO"] },
      latitud: { bsonType: ["double", "int", "long", "decimal"], minimum: -90, maximum: 90 },
      longitud: { bsonType: ["double", "int", "long", "decimal"], minimum: -180, maximum: 180 },
      fuente: { bsonType: "string", maxLength: 120 },
      descripcion: { bsonType: ["string", "null"], maxLength: 500 }
    }
  }
});

ensureCollectionWithValidator("openeo_indicator_observations", {
  $jsonSchema: {
    bsonType: "object",
    properties: {
      regionId: { bsonType: ["string", "null"] },
      indicator: { enum: ["NDVI", "NDMI", null] },
      observedAt: { bsonType: ["date", "null"] },
      value: { bsonType: ["double", "int", "long", "decimal", "null"] },
      unit: { bsonType: ["string", "null"] },
      aoi: { bsonType: ["string", "null"] },
      quality: { bsonType: ["string", "null"] },
      source: { bsonType: ["string", "null"] },
      ingestedAt: { bsonType: ["date", "null"] }
    }
  }
});

ensureCollectionWithValidator("openeo_job_runs", {
  $jsonSchema: {
    bsonType: "object",
    properties: {
      jobId: { bsonType: ["string", "null"] },
      regionId: { bsonType: ["string", "null"] },
      indicator: { enum: ["NDVI", "NDMI", null] },
      periodStart: { bsonType: ["date", "null"] },
      periodEnd: { bsonType: ["date", "null"] },
      status: { bsonType: ["string", "null"] },
      requestedAt: { bsonType: ["date", "null"] },
      updatedAt: { bsonType: ["date", "null"] },
      finishedAt: { bsonType: ["date", "null"] },
      errorCode: { bsonType: ["string", "null"] },
      errorMessage: { bsonType: ["string", "null"] },
      source: { bsonType: ["string", "null"] }
    }
  }
});

ensureCollectionWithValidator("dashboard_region_snapshots", {
  $jsonSchema: {
    bsonType: "object",
    properties: {
      regionId: { bsonType: ["string", "null"] },
      latestNdvi: { bsonType: ["double", "int", "long", "decimal", "null"] },
      latestNdmi: { bsonType: ["double", "int", "long", "decimal", "null"] },
      ndviTrend30d: { bsonType: ["double", "int", "long", "decimal", "null"] },
      ndmiTrend30d: { bsonType: ["double", "int", "long", "decimal", "null"] },
      heatAlerts7d: { bsonType: ["long", "int", "null"] },
      forestLossCurrentPct: { bsonType: ["double", "int", "long", "decimal", "null"] },
      criticality: { bsonType: ["string", "null"] },
      computedAt: { bsonType: ["date", "null"] },
      dataFreshnessSeconds: { bsonType: ["long", "int", "null"] }
    }
  }
});

// Indexes already represented in model + repository query patterns
ensureIndex("regions", { codigo: 1 }, { unique: true, name: "uk_regions_codigo" });

ensureIndex("alert_rules", { activa: 1 }, { name: "idx_alert_rules_activa" });
ensureIndex("alert_rules", { regionId: 1, activa: 1 }, { name: "idx_alert_rules_regionId_activa" });

ensureIndex("forest_loss_records", { regionId: 1 }, { name: "idx_forest_loss_regionId" });
ensureIndex("forest_loss_records", { anio: 1 }, { name: "idx_forest_loss_anio" });
ensureIndex("forest_loss_records", { regionId: 1, anio: 1 }, { name: "idx_forest_loss_regionId_anio" });

ensureIndex("heat_alert_events", { regionId: 1 }, { name: "idx_heat_alert_regionId" });
ensureIndex("heat_alert_events", { regionId: 1, fechaEvento: -1 }, { name: "idx_heat_alert_regionId_fechaEvento_desc" });
ensureIndex("heat_alert_events", { nivelRiesgo: 1 }, { name: "idx_heat_alert_nivelRiesgo" });

ensureIndex(
  "openeo_indicator_observations",
  { regionId: 1, indicator: 1, observedAt: -1 },
  { name: "idx_region_indicator_observedAt_desc" }
);
ensureIndex(
  "openeo_indicator_observations",
  { regionId: 1, indicator: 1, observedAt: 1 },
  { unique: true, name: "uk_region_indicator_observedAt" }
);
ensureIndex(
  "openeo_indicator_observations",
  { indicator: 1, observedAt: -1 },
  { name: "idx_indicator_observedAt_desc" }
);
ensureIndex(
  "openeo_indicator_observations",
  { regionId: 1, indicator: 1, ingestedAt: -1 },
  { name: "idx_region_indicator_ingestedAt_desc" }
);

ensureIndex("openeo_job_runs", { jobId: 1 }, { unique: true, name: "uk_openeo_job_runs_jobId" });
ensureIndex("openeo_job_runs", { regionId: 1 }, { name: "idx_openeo_job_runs_regionId" });
ensureIndex("openeo_job_runs", { indicator: 1 }, { name: "idx_openeo_job_runs_indicator" });
ensureIndex("openeo_job_runs", { status: 1 }, { name: "idx_openeo_job_runs_status" });
ensureIndex("openeo_job_runs", { status: 1, updatedAt: -1 }, { name: "idx_status_updatedAt" });
ensureIndex(
  "openeo_job_runs",
  { regionId: 1, indicator: 1, periodStart: 1, periodEnd: 1, status: 1 },
  { name: "idx_jobrun_region_indicator_period_status" }
);

ensureIndex("dashboard_region_snapshots", { regionId: 1 }, { unique: true, name: "uk_dashboard_snapshot_regionId" });
ensureIndex("dashboard_region_snapshots", { computedAt: -1 }, { name: "idx_computedAt_desc" });

print(`MongoDB schema ready on database: ${dbName}`);
