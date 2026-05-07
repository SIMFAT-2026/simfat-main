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
    if (!Array.isArray(bounds) || bounds.length !== 2) {
      return;
    }
    map.fitBounds(bounds, { padding: [18, 18] });
  }, [bounds, map]);

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

  if (points.length === 0) {
    return null;
  }

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

  return [
    [minLat, minLng],
    [maxLat, maxLng]
  ];
}

function AlertsOperationalMap({ alerts, reports, center = [-37.9, -72.4], zoom = 7 }) {
  const bounds = toBounds(alerts, reports);
  const safeAlerts = alerts.filter((item) => Number.isFinite(Number(item.latitud)) && Number.isFinite(Number(item.longitud)));
  const safeReports = reports.filter(
    (item) => Number.isFinite(Number(item.latitude)) && Number.isFinite(Number(item.longitude))
  );

  return (
    <div className="alerts-map-wrapper">
      <MapContainer className="alerts-map" center={center} zoom={zoom} scrollWheelZoom>
        <TileLayer attribution="&copy; OpenStreetMap contributors" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <FitMapBounds bounds={bounds} />

        {safeAlerts.map((alert) => (
          <CircleMarker
            key={`alert-${alert.id}`}
            center={[Number(alert.latitud), Number(alert.longitud)]}
            radius={8}
            pathOptions={{
              color: '#0f172a',
              fillColor: riskColor(alert.nivelRiesgo),
              fillOpacity: 0.88,
              weight: 1
            }}
          >
            <Popup>
              <strong>{alert.nivelRiesgo}</strong> | {alert.descripcion || 'Alerta territorial'}
            </Popup>
          </CircleMarker>
        ))}

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
