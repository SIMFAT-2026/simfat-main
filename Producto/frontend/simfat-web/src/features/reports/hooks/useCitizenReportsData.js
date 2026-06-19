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
    photos: [
      { name: 'humo-1.jpg', previewUrl: '' },
      { name: 'humo-2.jpg', previewUrl: '' }
    ],
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
    photos: [{ name: 'foco-1.jpg', previewUrl: '' }],
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

function normalizePhotos(item = {}) {
  const photoList = [];
  const rawPhotos = Array.isArray(item.photos) ? item.photos : [];
  const rawUrls = Array.isArray(item.photoUrls) ? item.photoUrls : [];

  rawPhotos.forEach((photo, index) => {
    if (typeof photo === 'string') {
      const isUrl = /^https?:\/\//i.test(photo);
      const fromUrl = isUrl ? photo.split('/').pop() || `foto-${index + 1}` : photo;
      photoList.push({
        name: fromUrl,
        previewUrl: isUrl ? photo : rawUrls[index] || ''
      });
      return;
    }

    if (photo && typeof photo === 'object') {
      photoList.push({
        name: photo.name || `foto-${index + 1}`,
        previewUrl: photo.previewUrl || photo.url || ''
      });
    }
  });

  if (photoList.length === 0 && Number(item.photoCount ?? item.photosCount ?? item.fotos ?? 0) > 0) {
    return Array.from({ length: Number(item.photoCount ?? item.photosCount ?? item.fotos ?? 0) }, (_, index) => ({
      name: `foto-${index + 1}`,
      previewUrl: ''
    }));
  }

  return photoList;
}

function toPreviewPhotos(files = []) {
  return files.map((file) => ({
    name: file.name,
    previewUrl: window.URL.createObjectURL(file)
  }));
}

function normalizeReports(items = []) {
  return items.map((item, index) => {
    const photos = normalizePhotos(item);
    return {
      id: String(item.id || createId(`report-${index}`)),
      regionId: String(item.regionId || item.region_id || ''),
      comunaId: String(item.comunaId || item.comuna_id || ''),
      category: String(item.category || item.categoria || 'OTRO').toUpperCase(),
      subCategory: item.subCategory || item.sub_categoria || '',
      description: item.description || item.descripcion || '',
      latitude: Number(item.latitude ?? item.latitud ?? item.lat ?? 0),
      longitude: Number(item.longitude ?? item.longitud ?? item.lng ?? 0),
      status: normalizeStatus(item.status || item.estado),
      photos,
      photoCount: photos.length,
      createdAt: item.createdAt || item.created_at || item.fechaCreacion || new Date().toISOString(),
      validatedAt: item.validatedAt || null,
      staleCount: Number(item.staleCount ?? 0),
      staleSince: item.staleSince || null,
      discardReason: item.discardReason || ''
    };
  });
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
      const previewPhotos = toPreviewPhotos(files);

      if (source === 'backend') {
        try {
          const created = await createCitizenReport({ payload, files });
          const normalized = normalizeReports([created])[0];
          if (!normalized.photos.length && previewPhotos.length > 0) {
            normalized.photos = previewPhotos;
            normalized.photoCount = previewPhotos.length;
          }
          setReports((prev) => [normalized, ...prev]);
          return normalized;
        } catch {
          // segundo intento: persistir reporte sin adjuntos para no perder el registro operativo
          try {
            const createdWithoutFiles = await createCitizenReport({ payload, files: [] });
            const normalized = normalizeReports([createdWithoutFiles])[0];
            if (previewPhotos.length > 0) {
              normalized.photos = previewPhotos;
              normalized.photoCount = previewPhotos.length;
            }
            setReports((prev) => [normalized, ...prev]);
            return normalized;
          } catch {
            // fallback local controlado
          }
        }
      }

      const local = {
        id: createId('report'),
        regionId: payload.regionId,
        comunaId: payload.comunaId || '',
        category: String(payload.category || 'OTRO').toUpperCase(),
        subCategory: payload.subCategory || '',
        description: payload.description || '',
        latitude: Number(payload.latitude),
        longitude: Number(payload.longitude),
        status: 'RECIBIDO',
        photos: previewPhotos,
        photoCount: previewPhotos.length,
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
      setReports((prev) => prev.map((item) => (item.id === id ? { ...item, status: normalizedStatus } : item)));

      if (source === 'backend') {
        try {
          const updated = await updateCitizenReportStatus(id, normalizedStatus);
          const normalized = normalizeReports([updated])[0];
          setReports((prev) => prev.map((item) => (item.id === id ? normalized : item)));
          return;
        } catch {
          // si falla backend se mantiene actualizacion local para continuidad
        }
      }
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
