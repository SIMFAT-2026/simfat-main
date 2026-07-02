import { useEffect } from 'react';
import L from 'leaflet';
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet';
import marker2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: marker2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow
});

function FitMapBounds({ bounds }) {
  const map = useMap();
  useEffect(() => {
    if (!Array.isArray(bounds) || bounds.length !== 2) return;
    map.fitBounds(bounds, { padding: [18, 18] });
  }, [bounds, map]);
  return null;
}

function FlyToSelected({ alertId, alerts }) {
  const map = useMap();
  useEffect(() => {
    if (!alertId) return;
    const alert = alerts.find((a) => a.id === alertId);
    if (!alert) return;
    const lat = Number(alert.latitud);
    const lon = Number(alert.longitud);
    if (Number.isFinite(lat) && Number.isFinite(lon)) {
      map.setView([lat, lon], 12);
    }
  }, [alertId, alerts, map]);
  return null;
}

function riskColor(level) {
  if (level === 'CRITICO') return '#b91c1c';
  if (level === 'ALTO') return '#dc2626';
  if (level === 'MEDIO') return '#f59e0b';
  return '#15803d';
}

function normalizeCoordinate(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function toBounds(alerts, reports) {
  const points = [...alerts, ...reports]
    .map((item) => [normalizeCoordinate(item.latitud || item.latitude), normalizeCoordinate(item.longitud || item.longitude)])
    .filter((item) => item[0] !== null && item[1] !== null);

  if (points.length === 0) return null;

  let minLat = points[0][0];
  let maxLat = points[0][0];
  let minLng = points[0][1];
  let maxLng = points[0][1];

  points.forEach(([lat, lng]) => {
    minLat = Math.min(minLat, lat);
    maxLat = Math.max(maxLat, lat);
    minLng = Math.min(minLng, lng);
    maxLng = Math.max(maxLng, lng);
  });

  return [[minLat, minLng], [maxLat, maxLng]];
}

// Chile territory zones (mainland + Easter Island + Juan Fernández + Desventuradas)
function withinChile(lat, lon) {
  const mainland = lat >= -57 && lat <= -17 && lon >= -77 && lon <= -65;
  const easterIsland = lat >= -28 && lat <= -26 && lon >= -110 && lon <= -108;
  const juanFernandez = lat >= -35 && lat <= -31 && lon >= -81 && lon <= -77;
  const desventuradas = lat >= -27 && lat <= -25 && lon >= -81 && lon <= -79;
  return mainland || easterIsland || juanFernandez || desventuradas;
}

function AlertsOperationalMap({ alerts, reports, selectedAlertId, center = [-37.9, -72.4], zoom = 7 }) {
  const safeAlerts = alerts.filter((item) => {
    const lat = Number(item.latitud);
    const lon = Number(item.longitud);
    return Number.isFinite(lat) && Number.isFinite(lon) && withinChile(lat, lon);
  });
  const safeReports = reports.filter((item) => {
    const lat = Number(item.latitude);
    const lon = Number(item.longitude);
    return Number.isFinite(lat) && Number.isFinite(lon) && withinChile(lat, lon);
  });
  const bounds = toBounds(safeAlerts, safeReports);

  return (
    <div className="alerts-map-wrapper">
      <MapContainer className="alerts-map" center={center} zoom={zoom} scrollWheelZoom>
        <TileLayer attribution="&copy; OpenStreetMap contributors" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <FitMapBounds bounds={bounds} />
        <FlyToSelected alertId={selectedAlertId} alerts={safeAlerts} />

        {safeAlerts.map((alert) => {
          const isSelected = alert.id === selectedAlertId;
          return (
            <CircleMarker
              key={`alert-${alert.id}`}
              center={[Number(alert.latitud), Number(alert.longitud)]}
              radius={isSelected ? 13 : 8}
              pathOptions={{
                color: isSelected ? '#1e40af' : '#0f172a',
                fillColor: isSelected ? '#3b82f6' : riskColor(alert.nivelRiesgo),
                fillOpacity: isSelected ? 1 : 0.88,
                weight: isSelected ? 3 : 1
              }}
            >
              <Popup>
                <strong>{alert.nivelRiesgo}</strong> | {alert.descripcion || 'Alerta territorial'}
              </Popup>
            </CircleMarker>
          );
        })}

        {safeReports.map((report) => (
          <CircleMarker
            key={`report-${report.id}`}
            center={[Number(report.latitude), Number(report.longitude)]}
            radius={6}
            pathOptions={{
              color: '#1e293b',
              fillColor: '#2563eb',
              fillOpacity: 0.72,
              weight: 1
            }}
          >
            <Popup>
              <strong>{report.category}</strong> | {report.description || 'Reporte ciudadano'}
            </Popup>
          </CircleMarker>
        ))}
      </MapContainer>
    </div>
  );
}

export default AlertsOperationalMap;
