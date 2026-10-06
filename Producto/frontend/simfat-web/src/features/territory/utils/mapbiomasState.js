// Pure state machine for one on-demand MapBiomas layer (BURNED or LANDCOVER).
// selectedYear undefined means "let the backend pick its data-derived default";
// once a layer loads, the explicit year it returned is kept (Q28).

export const initialMapbiomasState = {
  selectedYear: undefined,
  regionId: null,
  layer: null,
  loading: false,
  error: ''
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
      return { ...state, selectedYear: action.year };
    }
    case 'request': {
      // A loaded layer survives only if it is exactly the region and year being
      // requested, so data for one year is never shown under another.
      const keepLayer =
        state.layer !== null && state.regionId === action.regionId && state.layer.year === state.selectedYear;
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
        loading: false,
        error: ''
      };
    }
    case 'failure': {
      if (!isCurrentRequest(state, action)) {
        return state;
      }
      return { ...state, layer: null, loading: false, error: action.message };
    }
    default:
      return state;
  }
}
