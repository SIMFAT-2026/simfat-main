// Single place that turns backend enum values (CRITICO, GUIA, RECIBIDO...) into
// the labels users read. Unknown values are humanized rather than printed raw.

function humanize(value) {
  const text = String(value).trim().replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function makeLabeler(labels) {
  return (value) => {
    if (value === null || value === undefined || value === '') return '';
    const key = String(value).toUpperCase();
    return labels[key] ?? humanize(value);
  };
}

export const levelLabel = makeLabeler({
  NORMAL: 'Normal',
  BAJO: 'Bajo',
  MEDIO: 'Medio',
  PREVENTIVO: 'Preventivo',
  ALTO: 'Alto',
  CRITICO: 'Crítico'
});

export const priorityLabel = makeLabeler({
  BAJA: 'Baja',
  MEDIA: 'Media',
  ALTA: 'Alta',
  CRITICA: 'Crítica'
});

export const resourceCategoryLabel = makeLabeler({
  GUIA: 'Guía',
  PROTOCOLO: 'Protocolo',
  CAPACITACION: 'Capacitación',
  MATERIAL: 'Material'
});

export const reportCategoryLabel = makeLabeler({
  HUMO: 'Humo',
  FOCO: 'Foco',
  INCENDIO: 'Incendio',
  QUEMA: 'Quema',
  INFRAESTRUCTURA: 'Infraestructura',
  IGNICION_POTENCIAL: 'Ignición potencial',
  INFRAESTRUCTURA_ELECTRICA: 'Infraestructura eléctrica',
  COMBUSTIBLE_VEGETAL: 'Combustible vegetal',
  OTRO: 'Otro'
});

const reportStatusBase = makeLabeler({
  RECIBIDO: 'Recibido',
  VALIDADO: 'Validado',
  DERIVADO: 'Derivado',
  DESCARTADO: 'Descartado'
});

export function reportStatusLabel(status, { stale = false } = {}) {
  const label = reportStatusBase(status);
  return stale && label ? `${label} (vencido)` : label;
}

export const modeLabel = makeLabeler({
  STANDARD: 'Estándar',
  ENHANCED: 'Con Copernicus'
});
