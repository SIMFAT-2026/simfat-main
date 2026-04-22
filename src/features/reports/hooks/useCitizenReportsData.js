import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createCitizenReport,
  deleteCitizenReport,
  getCitizenReports,
  getRegions,
  updateCitizenReportStatus
} from '../../../services';

const MOCK_REPORTS = [
  {
    id: 'report-1',
    regionId: 'biobio',
    category: 'HUMO',
    description: 'Se observa columna de humo cercana a zona forestal.',
    latitude: -36.81,
    longitude: -73.04,
    status: 'RECIBIDO',
    photoCount: 2,
    createdAt: '2026-04-21T14:30:00Z'
  },
  {
    id: 'report-2',
    regionId: 'araucania',
    category: 'FOCO',
    description: 'Foco visible en ladera, avance lento por viento moderado.',
    latitude: -38.74,
    longitude: -72.6,
    status: 'VALIDADO',
    photoCount: 1,
    createdAt: '2026-04-21T16:45:00Z'
  }
];

function createId(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

function normalizeRegions(rawRegions = []) {
  const normalized = rawRegions.map((region) => ({
    id: String(region.id || ''),
    nombre: region.nombre || '-'
  }));

  if (normalized.length > 0) {
    return normalized;
  }

  return [
    { id: 'biobio', nombre: 'Biobio' },
    { id: 'araucania', nombre: 'La Araucania' }
  ];
}

function normalizeStatus(value) {
  const normalized = String(value || '').toUpperCase();
  const allowed = ['RECIBIDO', 'VALIDADO', 'DERIVADO', 'DESCARTADO'];
  return allowed.includes(normalized) ? normalized : 'RECIBIDO';
}

function normalizeReports(items = []) {
  return items.map((item, index) => ({
    id: String(item.id || createId(`report-${index}`)),
    regionId: String(item.regionId || item.region_id || ''),
    category: String(item.category || item.categoria || 'OTRO').toUpperCase(),
    description: item.description || item.descripcion || '',
    latitude: Number(item.latitude ?? item.latitud ?? item.lat ?? 0),
    longitude: Number(item.longitude ?? item.longitud ?? item.lng ?? 0),
    status: normalizeStatus(item.status || item.estado),
    photoCount: Number(item.photoCount ?? item.photosCount ?? item.fotos ?? 0) || 0,
    createdAt: item.createdAt || item.created_at || item.fechaCreacion || new Date().toISOString()
  }));
}

export function useCitizenReportsData() {
  const [regions, setRegions] = useState([]);
  const [reports, setReports] = useState([]);
  const [source, setSource] = useState('backend');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError('');

    try {
      const [regionsData, reportsData] = await Promise.all([getRegions(), getCitizenReports()]);
      setRegions(normalizeRegions(Array.isArray(regionsData) ? regionsData : []));
      setReports(normalizeReports(Array.isArray(reportsData) ? reportsData : []));
      setSource('backend');
    } catch {
      setRegions(normalizeRegions([]));
      setReports(normalizeReports(MOCK_REPORTS));
      setSource('fallback');
      setError('Backend de reportes no disponible. Se muestran datos locales de continuidad.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const createReport = useCallback(
    async ({ payload, files }) => {
      if (source === 'backend') {
        try {
          const created = await createCitizenReport({ payload, files });
          const normalized = normalizeReports([created])[0];
          setReports((prev) => [normalized, ...prev]);
          return normalized;
        } catch {
          // fallback local controlado
        }
      }

      const local = {
        id: createId('report'),
        regionId: payload.regionId,
        category: String(payload.category || 'OTRO').toUpperCase(),
        description: payload.description || '',
        latitude: Number(payload.latitude),
        longitude: Number(payload.longitude),
        status: 'RECIBIDO',
        photoCount: files.length,
        createdAt: new Date().toISOString()
      };
      setReports((prev) => [local, ...prev]);
      return local;
    },
    [source]
  );

  const setReportStatus = useCallback(
    async (id, status) => {
      const normalizedStatus = normalizeStatus(status);

      if (source === 'backend') {
        try {
          const updated = await updateCitizenReportStatus(id, normalizedStatus);
          const normalized = normalizeReports([updated])[0];
          setReports((prev) => prev.map((item) => (item.id === id ? normalized : item)));
          return;
        } catch {
          // fallback local controlado
        }
      }

      setReports((prev) => prev.map((item) => (item.id === id ? { ...item, status: normalizedStatus } : item)));
    },
    [source]
  );

  const removeReport = useCallback(
    async (id) => {
      if (source === 'backend') {
        try {
          await deleteCitizenReport(id);
        } catch {
          // fallback local controlado
        }
      }

      setReports((prev) => prev.filter((item) => item.id !== id));
    },
    [source]
  );

  return useMemo(
    () => ({
      regions,
      reports,
      source,
      loading,
      error,
      reload: loadAll,
      createReport,
      setReportStatus,
      removeReport
    }),
    [regions, reports, source, loading, error, loadAll, createReport, setReportStatus, removeReport]
  );
}
