import { useId } from 'react';
import { LAYER_TOGGLE_GROUPS, activeLayerSummary, layerGroupInfoLines } from '../constants/indicators';
import { useDisclosure } from './useDisclosure';

function InfoButton() {
  const { open, buttonRef, panelRef, toggle } = useDisclosure();
  const panelId = useId();

  return (
    <span className="territory-disclosure">
      <button
        ref={buttonRef}
        type="button"
        className="territory-info-button"
        aria-label="Qué muestra cada grupo de capas"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
      >
        <span aria-hidden="true">ⓘ</span>
      </button>
      <div ref={panelRef} id={panelId} className="territory-popover territory-info-panel" hidden={!open}>
        {layerGroupInfoLines().map((line) => (
          <p key={line}>{line}</p>
        ))}
      </div>
    </span>
  );
}

function MoreLayers({ group, visibleIndicators, renderToggle }) {
  const { open, buttonRef, panelRef, toggle } = useDisclosure();
  const panelId = useId();
  const titleId = `${panelId}-title`;
  const summary = activeLayerSummary({ group, visibleIndicators });

  return (
    <span className="territory-disclosure">
      <button
        ref={buttonRef}
        type="button"
        className="territory-more-button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
      >
        Más capas {summary && <span className="territory-more-count">{summary}</span>}
        <span aria-hidden="true"> {open ? '▴' : '▾'}</span>
      </button>
      <div
        ref={panelRef}
        id={panelId}
        className="territory-popover territory-more-panel"
        role="group"
        aria-labelledby={titleId}
        hidden={!open}
      >
        <strong id={titleId} className="territory-popover-title">{group.title}</strong>
        <span className="territory-popover-context">{group.context}</span>
        <div className="territory-chips">{group.indicators.map(renderToggle)}</div>
      </div>
    </span>
  );
}

// Compact layer toggles: inline chip rows for the always-visible groups, a
// "Más capas" popover for the rest and an info button explaining the groups.
// `renderToggle(indicator)` renders one chip (checkbox label);
// `renderYearControl(indicator)` optionally renders the inline control that
// stays glued to that chip (they wrap together as one unit).
function LayerToggleGroups({ visibleIndicators, renderToggle, renderYearControl }) {
  const inline = LAYER_TOGGLE_GROUPS.filter((group) => group.placement === 'inline');
  const popover = LAYER_TOGGLE_GROUPS.filter((group) => group.placement === 'popover');

  return (
    <div className="territory-toggle-groups">
      {inline.map((group) => (
        <div key={group.id} className="territory-toggle-row" role="group" aria-label={group.title}>
          <span className="territory-toggle-row-label" aria-hidden="true">{group.title}:</span>
          <div className="territory-chips">
            {group.indicators.map((indicator) => {
              const control = renderYearControl?.(indicator) ?? null;
              if (!control) return renderToggle(indicator);
              return (
                <span key={indicator} className="territory-chip-pair">
                  {renderToggle(indicator)}
                  {control}
                </span>
              );
            })}
          </div>
        </div>
      ))}
      <div className="territory-toggle-row territory-toggle-extras">
        {popover.map((group) => (
          <MoreLayers key={group.id} group={group} visibleIndicators={visibleIndicators} renderToggle={renderToggle} />
        ))}
        <InfoButton />
      </div>
    </div>
  );
}

export default LayerToggleGroups;
