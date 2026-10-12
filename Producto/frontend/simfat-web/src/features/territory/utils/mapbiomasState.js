// Pure state machine for one on-demand MapBiomas layer (BURNED or LANDCOVER).
// selectedYear undefined means "let the backend pick its data-derived default";
// once a layer loads, the explicit year it returned is kept (Q28).

export const initialMapbiomasState = {
  selectedYear: undefined,
  regionId: null,
  layer: null,
  loading: false,
  error: '',
  // Available years {minYear, maxYear}, remembered from the last payload so the
  // year control keeps its bounds while another year is loading.
  range: null,
  // Year the backend picked on its own (Q28): what "Último" returns to.
  defaultYear: undefined
};

// True while `layer` belongs to a different year than the one requested (kept
// on screen, dimmed, until the requested year arrives). Derived on every
// render instead of stored, so there is no frame between `select-year` and the
// `request` dispatch where the old layer shows undimmed. selectedYear
// undefined ("Último") targets the known default year: a layer already at that
// year is not stale, so a same-data refetch does not dim the map.
export function isStaleLayer(state, regionId) {
  if (!state || state.layer == null || state.regionId !== regionId) return false;
  const target = state.selectedYear ?? state.defaultYear;
  return Number.isInteger(target) && state.layer.year !== target;
}

function rangeFrom(layer, fallback) {
  const { minYear, maxYear } = layer || {};
  return Number.isInteger(minYear) && Number.isInteger(maxYear) && minYear <= maxYear
    ? { minYear, maxYear }
    : fallback;
}

// Every failure maps to this fixed Spanish message: raw error text (e.g.
// "Network Error") never reaches the UI.
export const LOAD_ERROR_MESSAGE = 'No fue posible cargar el área quemada.';
const LOAD_ERROR_MESSAGES = {
  BURNED: LOAD_ERROR_MESSAGE,
  LANDCOVER: 'No fue posible cargar la cobertura del suelo.'
};
const MISSING_LAYER_MESSAGE = 'No hay datos de esta capa para el año y la región seleccionados.';

function isCurrentRequest(state, action) {
  return state.regionId === action.regionId && state.selectedYear === action.requestedYear;
}

export function mapbiomasReducer(state, action) {
  switch (action.type) {
    case 'select-year': {
      if (action.year === null || action.year === undefined) {
        return state.selectedYear === undefined ? state : { ...state, selectedYear: undefined };
      }
      if (!Number.isInteger(action.year) || action.year === state.selectedYear) {
        return state;
      }
      if (state.layer !== null && state.layer.year === action.year) {
        // Back to the year already on screen: the hook issues no new request
        // (its in-flight one is aborted), so settle here instead of staying
        // stale and loading forever.
        return { ...state, selectedYear: action.year, loading: false };
      }
      return { ...state, selectedYear: action.year };
    }
    case 'request': {
      // A loaded layer survives within the same region only (never across
      // regions). If it is for another year than the one requested it is kept
      // (see isStaleLayer): consumers must derive every label from layer.year, so
      // nothing claims the new year before its data arrives.
      const keepLayer = state.layer !== null && state.regionId === action.regionId;
      return {
        ...state,
        regionId: action.regionId,
        layer: keepLayer ? state.layer : null,
        loading: true,
        error: ''
      };
    }
    case 'success': {
      if (!isCurrentRequest(state, action)) {
        return state;
      }
      if (!action.layer) {
        return { ...state, layer: null, loading: false, error: MISSING_LAYER_MESSAGE };
      }
      return {
        ...state,
        layer: action.layer,
        selectedYear: Number.isInteger(action.layer.year) ? action.layer.year : state.selectedYear,
        range: rangeFrom(action.layer, state.range),
        defaultYear:
          action.requestedYear === undefined && Number.isInteger(action.layer.year)
            ? action.layer.year
            : state.defaultYear,
        loading: false,
        error: ''
      };
    }
    case 'failure': {
      if (!isCurrentRequest(state, action)) {
        return state;
      }
      return { ...state, layer: null, loading: false, error: LOAD_ERROR_MESSAGES[action.indicator] || LOAD_ERROR_MESSAGE };
    }
    default:
      return state;
  }
}
