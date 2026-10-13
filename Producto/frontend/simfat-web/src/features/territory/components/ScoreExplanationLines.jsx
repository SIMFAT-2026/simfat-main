import { buildScoreExplanation } from '../utils/scoreExplanation';

// Presentational only: all logic lives in buildScoreExplanation.
export function ScoreRawLine({ score }) {
  const { rawLine } = buildScoreExplanation(score);
  if (!rawLine) return null;
  return <div className="comuna-tooltip-raw">{rawLine}</div>;
}

export function ScoreCompositionLine({ score }) {
  const { compositionLine } = buildScoreExplanation(score);
  if (!compositionLine) return null;
  return <div className="comuna-tooltip-composition">{compositionLine}</div>;
}
