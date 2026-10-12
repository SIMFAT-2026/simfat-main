import { ALERT_LEVEL_CONFIG } from '../constants/colorScales';
import { FWI_ALTO, FWI_CRITICO, alertTrigger } from './alertTrigger';

// Display order of the alert levels, most severe first.
const LEVEL_ORDER = ['CRITICO', 'ALTO', 'PREVENTIVO', 'NORMAL'];
const TOP_LIMIT = 5;

function validCount(value) {
  return Number.isFinite(value) && value >= 0 ? value : null;
}

const FWI_THRESHOLD_BY_LEVEL = { ALTO: FWI_ALTO, CRITICO: FWI_CRITICO };

// " (19 por FWI ≥ 20)" for the comunas of `level` raised by a trigger rather
// than by their score; empty when none were.
function triggerBreakdown(entries, level) {
  const atLevel = entries.filter((entry) => entry.level === level);
  const parts = [];
  const byFwi = atLevel.filter((entry) => entry.trigger === 'fwi').length;
  const byFirms = atLevel.filter((entry) => entry.trigger === 'firms').length;
  if (byFwi > 0) parts.push(`${byFwi} por FWI ≥ ${FWI_THRESHOLD_BY_LEVEL[level]}`);
  if (byFirms > 0) parts.push(`${byFirms} por focos FIRMS`);
  return parts.length ? ` (${parts.join(', ')})` : '';
}

function plural(count) {
  return `${count} ${count === 1 ? 'comuna' : 'comunas'}`;
}

// Regional summary derived from the per-comuna scores the map paints, so it
// can never contradict the choropleth. Scores are 0-1; percentages use the
// same `(score * 100).toFixed(0)` rounding as the rest of the territory UI, and
// ordering/ties compare that displayed value. `comunaTotal` is the number of
// comunas in the region (GeoJSON features); comunas without a score, or with an
// unknown alert level, count as "without data" and never as Normal.
export function regionalSummary({ comunaScores, comunaNames, comunaTotal, firmsCount48h, reports7d } = {}) {
  const names = comunaNames || {};
  const scored = Object.entries(comunaScores || {}).filter(
    ([, score]) => score && typeof score.scoreComposite === 'number'
  );
  const entries = scored
    .filter(([, score]) => LEVEL_ORDER.includes(score.alertLevel))
    .map(([comunaId, score]) => {
      const level = score.alertLevel;
      const pct = (score.scoreComposite * 100).toFixed(0);
      return {
        trigger: alertTrigger({
          alertLevel: level,
          score: score.scoreComposite,
          fwiRaw: score.fwiRaw,
          firmsCount: score.firmsCount,
          firmsFrpMean: score.firmsFrpMean,
          alertDriver: score.alertDriver
        }).kind,
        comunaId,
        name: names[comunaId] || score.nombreComuna || comunaId,
        rounded: Number(pct),
        pct,
        level,
        label: ALERT_LEVEL_CONFIG[level].label
      };
    })
    .sort((a, b) => b.rounded - a.rounded || a.name.localeCompare(b.name, 'es'));

  const toItem = ({ comunaId, name, pct, level, label }) => ({ comunaId, name, pct, level, label });

  const distribution = LEVEL_ORDER
    .map((level) => ({
      level,
      label: ALERT_LEVEL_CONFIG[level].label,
      count: entries.filter((entry) => entry.level === level).length
    }))
    .filter((item) => item.count > 0);

  const total = Number.isFinite(comunaTotal) && comunaTotal > 0 ? comunaTotal : null;
  const withoutDataCount = total !== null
    ? Math.max(0, total - entries.length)
    : scored.length - entries.length;

  let comunasText = '';
  if (entries.length > 0) {
    comunasText = withoutDataCount > 0 && total !== null
      ? `${entries.length} de ${total} comunas con puntaje`
      : plural(entries.length);
  }

  const highestAlert = distribution.find((item) => item.level !== 'NORMAL');
  let alertText = 'Sin datos por comuna';
  if (entries.length > 0) {
    if (highestAlert) {
      alertText = `${highestAlert.count} en nivel ${highestAlert.label.toLowerCase()}${triggerBreakdown(entries, highestAlert.level)}`;
    } else {
      alertText = withoutDataCount > 0
        ? 'Sin comunas en alerta entre las que tienen puntaje'
        : 'Sin comunas en alerta';
    }
  }

  return {
    comunaCount: entries.length,
    comunaTotal: total,
    withoutDataCount,
    comunasText,
    alertText,
    distribution,
    max: entries.length ? toItem(entries[0]) : null,
    maxTies: entries.length ? entries.filter((entry) => entry.rounded === entries[0].rounded).length - 1 : 0,
    top: entries.slice(0, TOP_LIMIT).map(toItem),
    firmsCount48h: validCount(firmsCount48h),
    reports7d: validCount(reports7d)
  };
}
