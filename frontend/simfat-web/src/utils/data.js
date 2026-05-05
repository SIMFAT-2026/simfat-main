export function toArray(value) {
  return Array.isArray(value) ? value : [];
}

export function toSelectOptions(regions) {
  return toArray(regions).map((region) => ({
    value: region.id,
    label: `${region.nombre} (${region.codigo})`
  }));
}

export function asNumberOrNull(value) {
  if (value === '' || value === null || value === undefined) {
    return null;
  }

  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
}

export function boolFromSelect(value) {
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  return null;
}
