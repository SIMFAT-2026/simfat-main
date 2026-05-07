export function formatNumber(value) {
  return new Intl.NumberFormat('es-CL').format(value);
}
