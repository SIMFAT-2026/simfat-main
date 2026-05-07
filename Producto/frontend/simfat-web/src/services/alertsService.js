import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getAlerts(filters = {}) {
  if (filters.regionId) {
    const response = await axiosClient.get(`${API_ENDPOINTS.alerts}/region/${filters.regionId}`);
    return extractData(response.data);
  }

  const response = await axiosClient.get(API_ENDPOINTS.alerts);
  return extractData(response.data);
}

export async function createAlert(payload) {
  const response = await axiosClient.post(API_ENDPOINTS.alerts, payload);
  return extractData(response.data);
}

export async function updateAlert(id, payload) {
  const response = await axiosClient.put(`${API_ENDPOINTS.alerts}/${id}`, payload);
  return extractData(response.data);
}

export async function deleteAlert(id) {
  const response = await axiosClient.delete(`${API_ENDPOINTS.alerts}/${id}`);
  return extractData(response.data);
}

function normalizeDate(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString();
}

function withinRange(value, from, to) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return false;
  }

  if (from) {
    const fromDate = new Date(from);
    if (!Number.isNaN(fromDate.getTime()) && date < fromDate) {
      return false;
    }
  }

  if (to) {
    const toDate = new Date(to);
    if (!Number.isNaN(toDate.getTime()) && date > toDate) {
      return false;
    }
  }

  return true;
}

function normalizeAlert(item) {
  return {
    id: String(item.id || ''),
    regionId: String(item.regionId || item.region_id || ''),
    fechaEvento: normalizeDate(item.fechaEvento || item.fecha_evento || item.createdAt),
    nivelRiesgo: String(item.nivelRiesgo || item.nivel_riesgo || 'BAJO').toUpperCase(),
    latitud: Number(item.latitud ?? item.latitude ?? item.lat ?? 0),
    longitud: Number(item.longitud ?? item.longitude ?? item.lng ?? 0),
    fuente: item.fuente || item.source || '',
    descripcion: item.descripcion || item.description || ''
  };
}

export async function getAlertsMap(filters = {}) {
  try {
    const response = await axiosClient.get(API_ENDPOINTS.alertsMap, {
      params: {
        regionId: filters.regionId || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
        level: filters.level || undefined
      }
    });
    const data = extractData(response.data);
    const rows = Array.isArray(data) ? data : data?.alerts || data?.features || [];
    return rows.map(normalizeAlert);
  } catch {
    const rows = await getAlerts({ regionId: filters.regionId });
    const normalized = (Array.isArray(rows) ? rows : []).map(normalizeAlert);
    return normalized.filter((alert) => {
      const byLevel = !filters.level || alert.nivelRiesgo === String(filters.level).toUpperCase();
      const byDate = withinRange(alert.fechaEvento, filters.from, filters.to);
      return byLevel && byDate;
    });
  }
}
