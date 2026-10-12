import { CLIMATE_SCALES } from '../constants/colorScales';

// Wind arrows sit on top of the wind choropleth, which is already coloured by speed.
// Colouring the arrow by speed too made it vanish on its own fill, so speed is
// encoded by arrow size and the arrow itself is a fixed dark shape with a white halo.
export const WIND_ARROW = { fill: '#1e293b', halo: '#ffffff' };
export const WIND_ARROW_PATH = 'M12 2 L20 21 L12 16.5 L4 21 Z';
export const WIND_ARROW_HALO_WIDTH = 2.2;

// Same class edges as CLIMATE_SCALES.WIND.bins (< 10, 10-20, 20-35, 35-50, > 50 km/h).
const SIZES_PX = [14, 18, 22, 26, 30];

function binIndex(speed) {
  if (typeof speed !== 'number' || !Number.isFinite(speed)) return 0;
  const index = CLIMATE_SCALES.WIND.bins.findIndex((bin) => speed < bin.max);
  return index === -1 ? SIZES_PX.length - 1 : index;
}

export function windArrowSize(speed) {
  return SIZES_PX[binIndex(speed)];
}

// Arrow points up at 0deg; `bearingTo` rotates it to where the wind is heading.
export function windArrowHtml(speed, bearingTo) {
  const size = windArrowSize(speed);
  return (
    `<svg class="wind-arrow" width="${size}" height="${size}" viewBox="0 0 24 24" ` +
    `style="transform: rotate(${bearingTo}deg);" aria-hidden="true">` +
    `<path d="${WIND_ARROW_PATH}" fill="${WIND_ARROW.fill}" ` +
    `stroke="${WIND_ARROW.halo}" stroke-width="${WIND_ARROW_HALO_WIDTH}" stroke-linejoin="round" paint-order="stroke"/>` +
    '</svg>'
  );
}

export const WIND_ARROW_LEGEND = CLIMATE_SCALES.WIND.bins.map((bin, index) => ({
  label: bin.label,
  size: SIZES_PX[index]
}));
