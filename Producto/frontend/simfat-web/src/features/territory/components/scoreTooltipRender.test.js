import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { ScoreCompositionLine, ScoreRawLine } from './ScoreExplanationLines.jsx';

const score = {
  scoreComposite: 0.38,
  fwiRaw: 12.34,
  mapbiomasWeight: 0.5,
  components: { mapbiomas: 0.31, fwi: 0.07, firms: 0, reports: 0 }
};

describe('score tooltip lines render', () => {
  it('renders the composition line and no legacy chips or placeholders', () => {
    const html = renderToStaticMarkup(createElement(ScoreCompositionLine, { score }));
    expect(html).toContain('comuna-tooltip-composition');
    expect(html).toContain('Puntaje 38: MapBiomas 31');
    expect(html).toContain('susceptibilidad estructural');
    expect(html).not.toContain('comuna-tooltip-comp-chip');
    expect(html).not.toMatch(/sin datos/i);
  });

  it('renders the raw line with es-CL formatting', () => {
    const html = renderToStaticMarkup(createElement(ScoreRawLine, { score }));
    expect(html).toContain('FWI 12,3');
  });

  it('renders nothing when there is no explanation', () => {
    expect(renderToStaticMarkup(createElement(ScoreCompositionLine, { score: null }))).toBe('');
    expect(renderToStaticMarkup(createElement(ScoreRawLine, { score: { components: {} } }))).toBe('');
  });
});
