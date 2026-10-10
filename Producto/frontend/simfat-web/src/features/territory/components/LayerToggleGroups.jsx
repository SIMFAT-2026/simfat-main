import { useEffect, useId, useState } from 'react';
import { LAYER_TOGGLE_GROUPS, isToggleGroupExpanded } from '../constants/indicators';

const NARROW_QUERY = '(max-width: 640px)';

// True while the viewport is phone-sized; drives the collapsible group default.
function useNarrowViewport() {
  const [narrow, setNarrow] = useState(() =>
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia(NARROW_QUERY).matches
      : false
  );

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined;
    const query = window.matchMedia(NARROW_QUERY);
    const update = () => setNarrow(query.matches);
    update();
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);

  return narrow;
}

function ToggleGroup({ group, visibleIndicators, narrow, renderToggle }) {
  const baseId = useId();
  const [override, setOverride] = useState(null);
  const expanded = isToggleGroupExpanded({ group, visibleIndicators, narrow, override });
  const titleId = `${baseId}-title`;
  const bodyId = `${baseId}-body`;

  return (
    <div className="territory-toggle-group" role="group" aria-labelledby={titleId}>
      <div className="territory-toggle-group-header">
        <h4 id={titleId} className="territory-toggle-group-title">
          {group.collapsible ? (
            <button
              type="button"
              className="territory-toggle-group-button"
              aria-expanded={expanded}
              aria-controls={bodyId}
              onClick={() => setOverride(!expanded)}
            >
              <span aria-hidden="true">{expanded ? '▾' : '▸'}</span> {group.title}
            </button>
          ) : (
            group.title
          )}
        </h4>
        <span className="territory-toggle-group-subtitle">{group.subtitle}</span>
      </div>
      {group.note && <p className="territory-toggle-group-note">ⓘ {group.note}</p>}
      <div id={bodyId} className="territory-layer-toggles" hidden={!expanded}>
        {group.indicators.map(renderToggle)}
      </div>
    </div>
  );
}

// The layer toggles, grouped by time semantics (see LAYER_TOGGLE_GROUPS).
// `renderToggle(indicator)` renders one checkbox label.
function LayerToggleGroups({ visibleIndicators, renderToggle }) {
  const narrow = useNarrowViewport();

  return (
    <div className="territory-toggle-groups">
      {LAYER_TOGGLE_GROUPS.map((group) => (
        <ToggleGroup
          key={group.id}
          group={group}
          visibleIndicators={visibleIndicators}
          narrow={narrow}
          renderToggle={renderToggle}
        />
      ))}
    </div>
  );
}

export default LayerToggleGroups;
