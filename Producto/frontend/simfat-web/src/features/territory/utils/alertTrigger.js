// Explains why a comuna sits at its alert level. The alert level is NOT just the
// score band: the backend (ComunaRiskServiceImpl#resolveAlert) also raises it from
// triggers and now returns WHY in the `alertDriver` field of the comuna risk payload
// (SCORE | FWI | FIRMS_TODAY | FIRMS_CLUSTER | FIRMS_FRP). That field is the source
// of truth. Only for old snapshots (alertDriver null) does this helper fall back to a
// client mirror of the backend rule order, which is a best-effort guess:
//
//   CRITICO: FIRMS detection today | FWI >= 45 | score >= 0.85
//            | FIRMS count >= 4 | FIRMS mean FRP >= 60
//   ALTO:    FWI >= 20 | score >= 0.70
//
// The mirror uses the DEFAULT thresholds (score 0.70 / 0.85); if the backend overrides
// them through configuration (territory.riesgo.umbral.*) the fallback may differ.

export const FWI_ALTO = 20;
export const FWI_CRITICO = 45;
export const SCORE_ALTO = 0.7;
export const SCORE_CRITICO = 0.85;
export const FIRMS_COUNT_CRITICO = 4;
export const FIRMS_FRP_CRITICO = 60;

// Copy for the regional summary's info disclosure.
export const ALERT_RULE_EXPLANATION =
  'El puntaje (0–100) combina clima (FWI), vegetación, focos y reportes. ' +
  'El nivel de alerta sube por el puntaje o por disparadores: FWI ≥ 20 eleva a Alto; ' +
  'FWI ≥ 45 o un foco FIRMS detectado hoy elevan a Crítico, igual que varios focos recientes o muy intensos.';

const NONE = Object.freeze({ kind: null, text: '' });
const BY_SCORE = Object.freeze({ kind: 'score', text: '' });
const BY_FIRMS_PROBABLE = Object.freeze({ kind: 'firms', text: 'Activado probablemente por focos FIRMS' });
const FIRMS_TEXT_BY_DRIVER = Object.freeze({
  FIRMS_TODAY: Object.freeze({ kind: 'firms', text: 'Activado por un foco FIRMS detectado hoy' }),
  FIRMS_CLUSTER: Object.freeze({ kind: 'firms', text: 'Activado por varios focos FIRMS recientes' }),
  FIRMS_FRP: Object.freeze({ kind: 'firms', text: 'Activado por focos FIRMS de alta intensidad' })
});

const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);

// es-CL decimal comma, one decimal.
function formatFwi(fwiRaw) {
  return fwiRaw.toFixed(1).replace('.', ',');
}

function byFwi(fwiRaw) {
  if (!isNumber(fwiRaw)) return { kind: 'fwi', text: 'Activado por FWI (peligro meteorológico)' };
  return { kind: 'fwi', text: `Activado por FWI ${formatFwi(fwiRaw)} (peligro meteorológico)` };
}

// Trigger announced by the backend, or null when the driver is missing/unknown.
function fromDriver(alertDriver, fwiRaw) {
  if (alertDriver === 'SCORE') return BY_SCORE;
  if (alertDriver === 'FWI') return byFwi(fwiRaw);
  return FIRMS_TEXT_BY_DRIVER[alertDriver] || null;
}

// `score` is 0-1. Returns { kind: 'score' | 'fwi' | 'firms' | null, text }.
// `text` is empty when the score explains the level (or nothing can be said).
export function alertTrigger({ alertLevel, score, fwiRaw, firmsCount, firmsFrpMean, alertDriver } = {}) {
  const hasScore = isNumber(score);
  const hasFwi = isNumber(fwiRaw);

  if (alertLevel === 'NORMAL' || alertLevel === 'PREVENTIVO') {
    return BY_SCORE;
  }

  if (alertLevel === 'CRITICO' || alertLevel === 'ALTO') {
    const announced = fromDriver(alertDriver, fwiRaw);
    if (announced) return announced;
  }

  // Fallback for snapshots without alertDriver: mirror of the backend order.
  if (alertLevel === 'CRITICO') {
    if (hasFwi && fwiRaw >= FWI_CRITICO) return byFwi(fwiRaw);
    if (hasScore && score >= SCORE_CRITICO) return BY_SCORE;
    if (
      (isNumber(firmsCount) && firmsCount >= FIRMS_COUNT_CRITICO) ||
      (isNumber(firmsFrpMean) && firmsFrpMean >= FIRMS_FRP_CRITICO)
    ) {
      return BY_FIRMS_PROBABLE;
    }
    // Below the score threshold with no FWI or cluster cause, the remaining
    // backend cause is a FIRMS detection from today, which the client cannot see.
    return hasScore ? BY_FIRMS_PROBABLE : NONE;
  }

  if (alertLevel === 'ALTO') {
    if (hasFwi && fwiRaw >= FWI_ALTO) return byFwi(fwiRaw);
    if (hasScore && score >= SCORE_ALTO) return BY_SCORE;
    return hasScore ? BY_SCORE : NONE;
  }

  return NONE;
}
