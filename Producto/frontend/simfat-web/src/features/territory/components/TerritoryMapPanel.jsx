import { useEffect } from 'react';
import L from 'leaflet';
import { GeoJSON, MapContainer, TileLayer, useMap } from 'react-leaflet';
import marker2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import EmptyState from '../../../components/EmptyState';
import ErrorMessage from '../../../components/ErrorMessage';
import LoadingSpinner from '../../../components/LoadingSpinner';

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: marker2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow
});

const INDICATOR_COLORS = {
  NDVI: '#16a34a',
  NDMI: '#0ea5e9',
  LOSS: '#f97316',
  ALERTS: '#dc2626',
  REPORTS: '#7c3aed'
};

function FitRegionBounds({ bounds }) {
  const map = useMap();

  useEffect(() => {
    if (Array.isArray(bounds) && bounds.length === 2) {
      map.fitBounds(bounds, { padding: [20, 20] });
    }
  }, [bounds, map]);

  return null;
}

function featureLabel(feature) {
  const props = feature?.properties || {};
  return props.label || props.name || props.indicator || 'Punto territorial';
}

function featureMeta(feature) {
  const props = feature?.properties || {};
  if (props.value !== undefined && props.value !== null) {
    return `Valor: ${props.value}`;
  }
  if (props.hectares !== undefined && props.hectares !== null) {
    return `Hectareas: ${props.hectares}`;
  }
  if (props.level) {
    return `Nivel: ${props.level}`;
  }
  if (props.category) {
    return `Categoria: ${props.category}`;
  }
  return '';
}

function toPointStyle(indicator) {
  return {
    radius: indicator === 'ALERTS' ? 8 : 7,
    fillColor: INDICATOR_COLORS[indicator] || '#64748b',
    color: '#0f172a',
    weight: 1,
    opacity: 0.85,
    fillOpacity: 0.8
  };
}

function indicatorCount(regionData, indicator) {
  return regionData?.layers?.[indicator]?.features?.length || 0;
}

function TerritoryMapPanel({
  regionOptions,
  selectedRegionId,
  setSelectedRegionId,
  visibleIndicators,
  toggleIndicator,
  regionData,
  loading,
  refreshing,
  error,
  onRetry
}) {
  return (
    <article className="dashboard-card territory-map-card">
      <div className="territory-header">
        <h3>Mapa territorial interactivo</h3>
        <button type="button" className="btn btn-secondary" onClick={onRetry} disabled={loading || refreshing}>
          {refreshing ? 'Actualizando...' : 'Actualizar capas'}
        </button>
      </div>

      <div className="filter-bar territory-controls">
        <label>
          Region
          <select value={selectedRegionId} onChange={(event) => setSelectedRegionId(event.target.value)}>
            {regionOptions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.label}
              </option>
            ))}
          </select>
        </label>

        <div className="territory-layer-toggles">
          {['NDVI', 'NDMI', 'LOSS', 'ALERTS', 'REPORTS'].map((indicator) => (
            <label key={indicator} className="territory-toggle">
              <input
                type="checkbox"
                checked={visibleIndicators.includes(indicator)}
                onChange={() => toggleIndicator(indicator)}
              />
              <span>{indicator}</span>
            </label>
          ))}
        </div>
      </div>

      {loading ? <LoadingSpinner label="Cargando capas territoriales..." /> : null}
      {!loading && error ? <ErrorMessage error={{ message: error }} onRetry={onRetry} /> : null}
      {!loading && !error && !regionData ? (
        <EmptyState title="Sin datos territoriales" description="No hay capas para la region seleccionada." />
      ) : null}

      {!loading && !error && regionData ? (
        <div className="territory-map-wrapper">
          <MapContainer
            className="territory-map"
            center={regionData.center}
            zoom={regionData.zoom}
            scrollWheelZoom
          >
            <TileLayer
              attribution="&copy; OpenStreetMap contributors"
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <FitRegionBounds bounds={regionData.bounds} />

            {visibleIndicators.map((indicator) => (
              <GeoJSON
                key={`${regionData.regionId}-${indicator}`}
                data={regionData.layers[indicator]}
                pointToLayer={(feature, latlng) => L.circleMarker(latlng, toPointStyle(indicator))}
                onEachFeature={(feature, layer) => {
                  const label = featureLabel(feature);
                  const meta = featureMeta(feature);
                  layer.bindPopup(`${label}${meta ? ` | ${meta}` : ''}`);
                }}
              />
            ))}
          </MapContainer>

          <div className="territory-legend">
            <h4>Leyenda de capas</h4>
            <ul>
              {['NDVI', 'NDMI', 'LOSS', 'ALERTS', 'REPORTS'].map((indicator) => (
                <li key={indicator}>
                  <span
                    className="territory-color-dot"
                    style={{ backgroundColor: INDICATOR_COLORS[indicator] || '#64748b' }}
                  />
                  <span>{indicator}</span>
                  <strong>{indicatorCount(regionData, indicator)}</strong>
                </li>
              ))}
            </ul>
            <p className="territory-meta">
              Fuente: {regionData.source === 'backend' ? 'Backend' : 'Mock local'} | Ultima actualizacion:{' '}
              {new Date(regionData.generatedAt).toLocaleString('es-CL')}
            </p>
          </div>
        </div>
      ) : null}
    </article>
  );
}

export default TerritoryMapPanel;
