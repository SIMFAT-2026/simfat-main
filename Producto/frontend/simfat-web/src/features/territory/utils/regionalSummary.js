import { ALERT_LEVEL_CONFIG } from '../constants/colorScales';

// Display order of the alert levels, most severe first.
const LEVEL_ORDER = ['CRITICO', 'ALTO', 'PREVENTIVO', 'NORMAL'];
const TOP_LIMIT = 5;

function validCount(value) {
  return Number.isFinite(value) && value >= 0 ? value : null;
}

// Regional summary derived from the per-comuna scores the map paints, so it
// can never contradict the choropleth. Scores are 0-1; percentages use the
// same `(score * 100).toFixed(0)` rounding as the rest of the territory UI.
export function regionalSummary({ comunaScores, comunaNames, firmsCount48h, reports7d } = {}) {
  const names = comunaNames || {};
  const entries = Object.entries(comunaScores || {})
    .filter(([, score]) => score && typeof score.scoreComposite === 'number')
    .map(([comunaId, score]) => {
      const level = LEVEL_ORDER.includes(score.alertLevel) ? score.alertLevel : 'NORMAL';
      return {
        comunaId,
        name: names[comunaId] || score.nombreComuna || comunaId,
        raw: score.scoreComposite,
        pct: (score.scoreComposite * 100).toFixed(0),
        level,
        label: ALERT_LEVEL_CONFIG[level].label
      };
    })
    .sort((a, b) => b.raw - a.raw || a.name.localeCompare(b.name, 'es'));

  const toItem = ({ comunaId, name, pct, level, label }) => ({ comunaId, name, pct, level, label });

  const distribution = LEVEL_ORDER
    .map((level) => ({
      level,
      label: ALERT_LEVEL_CONFIG[level].label,
      count: entries.filter((entry) => entry.level === level).length
    }))
    .filter((item) => item.count > 0);

  const highestAlert = distribution.find((item) => item.level !== 'NORMAL');
  let alertText = 'Sin datos por comuna';
  if (entries.length > 0) {
    alertText = highestAlert
      ? `${highestAlert.count} en nivel ${highestAlert.label.toLowerCase()}`
      : 'Sin comunas en alerta';
  }

  return {
    comunaCount: entries.length,
    alertText,
    distribution,
    max: entries.length ? toItem(entries[0]) : null,
    maxTies: entries.filter((entry) => entry.raw === entries[0]?.raw).length - (entries.length ? 1 : 0),
    top: entries.slice(0, TOP_LIMIT).map(toItem),
    firmsCount48h: validCount(firmsCount48h),
    reports7d: validCount(reports7d)
  };
}
