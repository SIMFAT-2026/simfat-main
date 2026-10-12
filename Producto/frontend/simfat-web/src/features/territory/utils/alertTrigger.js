// Explains why a comuna sits at its alert level. The alert level is NOT just the
// score band: the backend (ComunaRiskServiceImpl#resolveAlertLevel) also raises it
// from triggers. This helper mirrors that rule order so the UI can say which one
// applied. Keep the constants in sync with the backend.
//
//   CRITICO: FIRMS detection today | FWI >= 45 | score >= 0.85
//            | FIRMS count >= 4 | FIRMS mean FRP >= 60
//   ALTO:    FWI >= 20 | score >= 0.70

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
  'focos FIRMS cercanos o intensos elevan a Crítico.';

const NONE =Object.freeze({ kind: null, text: '' });
const BY_SCORE = Object.freeze({ kind: 'score', text: '' });
const BY_FIRMS = Object.freeze({ kind: 'firms', text: 'Activado por focos FIRMS' });

const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);

// es-CL decimal comma, one decimal.
function formatFwi(fwiRaw) {
  return fwiRaw.toFixed(1).replace('.', ',');
}

function byFwi(fwiRaw) {
  return { kind: 'fwi', text: `Activado por FWI ${formatFwi(fwiRaw)} (peligro meteorológico)` };
}

// `score` is 0-1. Returns { kind: 'score' | 'fwi' | 'firms' | null, text }.
// `text` is empty when the score explains the level (or nothing can be said).
export function alertTrigger({ alertLevel, score, fwiRaw, firmsCount, firmsFrpMean } = {}) {
  const hasScore = isNumber(score);
  const hasFwi = isNumber(fwiRaw);

  if (alertLevel === 'NORMAL' || alertLevel === 'PREVENTIVO') {
    return BY_SCORE;
  }

  if (alertLevel === 'CRITICO') {
    if (hasScore && score >= SCORE_CRITICO) return BY_SCORE;
    if (hasFwi && fwiRaw >= FWI_CRITICO) return byFwi(fwiRaw);
    if (
      (isNumber(firmsCount) && firmsCount >= FIRMS_COUNT_CRITICO) ||
      (isNumber(firmsFrpMean) && firmsFrpMean >= FIRMS_FRP_CRITICO)
    ) {
      return BY_FIRMS;
    }
    // Below the score threshold with no FWI or cluster cause, the remaining
    // backend cause is a FIRMS detection from today, which the client cannot see.
    return hasScore ? BY_FIRMS : NONE;
  }

  if (alertLevel === 'ALTO') {
    if (hasScore && score >= SCORE_ALTO) return BY_SCORE;
    if (hasFwi && fwiRaw >= FWI_ALTO) return byFwi(fwiRaw);
    return hasScore ? BY_SCORE : NONE;
  }

  return NONE;
}
