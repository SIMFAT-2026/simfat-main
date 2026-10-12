// Presentation constants for the citizen-report card on the territory map.
// The card is a dark translucent panel, so text colours are the light variants of
// the category hues (checked at 4.5:1 against the lightest possible backdrop).

export const REPORT_CARD_BACKGROUND = '#0f172a';

export const REPORT_CARD_COLORS = {
  category: {
    HUMO: '#cbd5e1',
    FOCO: '#fb923c',
    INCENDIO: '#f87171',
    OTRO: '#cbd5e1'
  },
  status: '#cbd5e1',
  time: '#cbd5e1'
};

// Roughly two lines of the card text; longer descriptions get an expand toggle.
export const REPORT_DESCRIPTION_PREVIEW_CHARS = 80;

export function isLongDescription(description) {
  if (typeof description !== 'string') return false;
  return description.trim().length > REPORT_DESCRIPTION_PREVIEW_CHARS;
}
