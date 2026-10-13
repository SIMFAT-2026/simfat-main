import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import {
  MAPBIOMAS_FIRE_CITATION,
  MAPBIOMAS_LANDCOVER_CITATION
} from '../utils/mapbiomasCitations';

vi.mock('react-leaflet', () => ({ GeoJSON: () => null }));
vi.mock('../hooks/useComunaMapbiomas', () => ({
  useComunaMapbiomas: () => ({
    loading: false,
    stale: false,
    error: null,
    retry: () => {},
    data: {
      landCover: null,
      fire: null,
      fireHistoryAvailable: false,
      indices: null,
      attribution: {
        text: `${MAPBIOMAS_LANDCOVER_CITATION}\n${MAPBIOMAS_FIRE_CITATION}`,
        license: 'CC BY 4.0',
        sourceUrl: 'https://chile.mapbiomas.org/'
      }
    }
  })
}));

const { LandcoverLegend } = await import('./LandcoverLayer.jsx');
const { default: ComunaMapbiomasSection } = await import('./ComunaMapbiomasSection.jsx');

function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

describe('LANDCOVER legend citation', () => {
  const layer = { year: 2024, values: {} };

  it('falls back to the official LANDCOVER citation, never FIRE', () => {
    const html = renderToStaticMarkup(createElement(LandcoverLegend, { state: { layer } }));
    expect(html).toContain(escapeHtml(MAPBIOMAS_LANDCOVER_CITATION));
    expect(html).not.toContain(escapeHtml(MAPBIOMAS_FIRE_CITATION));
  });

  it('prefers layer.citation when present', () => {
    const html = renderToStaticMarkup(
      createElement(LandcoverLegend, { state: { layer: { ...layer, citation: 'Custom citation' } } })
    );
    expect(html).toContain('Custom citation');
    expect(html).not.toContain(escapeHtml(MAPBIOMAS_LANDCOVER_CITATION));
  });
});

describe('comuna detail attribution', () => {
  it('renders the official citation alone, license and link in a separate element', () => {
    const html = renderToStaticMarkup(createElement(ComunaMapbiomasSection, { comunaId: 'X', year: 2024 }));
    const citation = html.match(/<p class="mb-source mb-citation">([\s\S]*?)<\/p>/);
    expect(citation).not.toBeNull();
    expect(citation[1]).toBe(escapeHtml(`${MAPBIOMAS_LANDCOVER_CITATION}\n${MAPBIOMAS_FIRE_CITATION}`));
    expect(citation[1]).not.toContain('CC BY');
    const license = html.match(/<p class="mb-source mb-license">([\s\S]*?)<\/p>/);
    expect(license).not.toBeNull();
    expect(license[1]).toContain('CC BY 4.0');
    expect(license[1]).toContain('Fuente');
  });
});
