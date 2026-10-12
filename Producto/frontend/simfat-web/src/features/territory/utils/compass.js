const COMPASS_LABELS = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];

// Open-Meteo's winddirection is the bearing the wind comes FROM (meteorological
// convention). The label describes that source bearing, in Spanish abbreviations.
export function compassLabelFrom(directionFromDeg) {
  const index = Math.round(directionFromDeg / 45) % 8;
  return COMPASS_LABELS[index];
}
