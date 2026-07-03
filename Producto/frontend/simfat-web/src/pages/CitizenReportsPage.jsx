import { useMemo, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import FilterBar from '../components/FilterBar';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useFeedback } from '../hooks';
import { useCitizenReportsData } from '../features/reports/hooks/useCitizenReportsData';
import { getComunasByRegion, getLabelForComuna } from '../data/territorioChile';

const REPORT_CATEGORIES = [
  'HUMO',
  'FOCO',
  'QUEMA',
  'INFRAESTRUCTURA',
  'IGNICION_POTENCIAL',
  'INFRAESTRUCTURA_ELECTRICA',
  'COMBUSTIBLE_VEGETAL',
  'OTRO'
];

// Catalogo cerrado de subcategorias por categoria. Espejo manual de
// SUBCATEGORIES_BY_CATEGORY en CitizenReportController.java - si Pablo trae
// una lista nueva, hay que actualizar ambos lados.
const REPORT_SUBCATEGORIES = {
  IGNICION_POTENCIAL: [
    'Quemas agrícolas',
    'Fogatas en zonas no habilitadas',
    'Parrillas o cocinillas en áreas vegetadas',
    'Quema de basura',
    'Quema de residuos forestales',
    'Uso de maquinaria que genera chispas',
    'Faenas de soldadura al aire libre',
    'Trabajos con esmeriles o galleteras',
    'Vehículos estacionados sobre pastizales secos',
    'Colillas de cigarro / personas fumando',
    'Fuegos artificiales',
    'Actividades recreativas con fuego',
    'Actos intencionales o sospecha de incendio provocado'
  ],
  INFRAESTRUCTURA_ELECTRICA: [
    'Cables eléctricos caídos',
    'Cables en contacto con vegetación',
    'Postes en mal estado',
    'Chispas provenientes de tendidos eléctricos',
    'Transformadores defectuosos',
    'Árboles con riesgo de caer sobre líneas eléctricas'
  ],
  COMBUSTIBLE_VEGETAL: [
    'Pastizales secos sin manejo',
    'Acumulación de ramas y residuos forestales',
    'Microbasurales con material combustible',
    'Sitios eriazos abandonados',
    'Plantaciones con exceso de material seco',
    'Matorrales densos cercanos a viviendas',
    'Ausencia de cortafuegos',
    'Cortafuegos deteriorados o interrumpidos',
    'Regeneración de especies exóticas creciendo sin control'
  ]
};

const REPORT_STATUSES = ['RECIBIDO', 'VALIDADO', 'DERIVADO', 'DESCARTADO'];
const MAX_FILES = 4;
const MAX_FILE_BYTES = 5 * 1024 * 1024;

const initialForm = {
  regionId: '',
  comunaId: '',
  category: 'HUMO',
  subCategory: '',
  description: '',
  latitude: '',
  longitude: ''
};

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value || '-';
  }
  return date.toLocaleString('es-CL');
}

function statusBadgeClass(status) {
  if (status === 'VALIDADO') return 'badge badge-low';
  if (status === 'DERIVADO') return 'badge badge-medium';
  if (status === 'DESCARTADO') return 'badge badge-critical';
  return 'badge badge-high';
}

function statusLabel(row) {
  if (row.status === 'DESCARTADO' && row.discardReason === 'VENCIDO') {
    return 'DESCARTADO (vencido)';
  }
  if (row.status === 'RECIBIDO' && row.staleCount > 0) {
    return 'RECIBIDO (vencido)';
  }
  return row.status;
}

function CitizenReportsPage() {
  const { regions, reports, source, loading, error, reload, createReport, setReportStatus, removeReport } = useCitizenReportsData();
  const feedback = useFeedback();

  const [form, setForm] = useState(initialForm);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [locating, setLocating] = useState(false);
  const [filters, setFilters] = useState({ regionId: '', status: '', category: '' });
  const [pendingDeleteId, setPendingDeleteId] = useState('');
  const [galleryReport, setGalleryReport] = useState(null);
  const [activePhotoIndex, setActivePhotoIndex] = useState(0);

  const monitoredRegions = useMemo(
    () => regions.filter((region) => getComunasByRegion(region.id).length > 0),
    [regions]
  );

  const regionMap = useMemo(
    () =>
      regions.reduce((acc, region) => {
        acc[region.id] = region.nombre;
        return acc;
      }, {}),
    [regions]
  );

  const formComunas = useMemo(() => getComunasByRegion(form.regionId), [form.regionId]);

  const filteredReports = useMemo(() => {
    return reports.filter((report) => {
      const byRegion = !filters.regionId || report.regionId === filters.regionId;
      const byStatus = !filters.status || report.status === filters.status;
      const byCategory = !filters.category || report.category === filters.category;
      return byRegion && byStatus && byCategory;
    });
  }, [reports, filters]);

  const columns = useMemo(
    () => [
      { key: 'createdAt', header: 'Creado', render: (row) => formatDate(row.createdAt) },
      { key: 'regionId', header: 'Region', render: (row) => regionMap[row.regionId] || row.regionId || '-' },
      { key: 'comunaId', header: 'Comuna', render: (row) => getLabelForComuna(row.comunaId) || row.comunaId || '-' },
      {
        key: 'category',
        header: 'Categoria',
        render: (row) => (row.subCategory ? `${row.category} — ${row.subCategory}` : row.category)
      },
      {
        key: 'status',
        header: 'Estado',
        render: (row) => <span className={statusBadgeClass(row.status)}>{statusLabel(row)}</span>
      },
      { key: 'latitude', header: 'Latitud', render: (row) => Number(row.latitude).toFixed(5) },
      { key: 'longitude', header: 'Longitud', render: (row) => Number(row.longitude).toFixed(5) },
      {
        key: 'photoCount',
        header: 'Fotos',
        render: (row) =>
          row.photoCount > 0 ? (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setGalleryReport(row);
                setActivePhotoIndex(0);
              }}
            >
              Ver ({row.photoCount})
            </button>
          ) : (
            '0'
          )
      },
      { key: 'description', header: 'Descripcion' }
    ],
    [regionMap]
  );

  function onInputChange(event) {
    const { name, value } = event.target;
    if (name === 'category') {
      setForm((prev) => ({ ...prev, category: value, subCategory: '' }));
      return;
    }
    if (name === 'regionId') {
      setForm((prev) => ({ ...prev, regionId: value, comunaId: '' }));
      return;
    }
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  function onFilterChange(event) {
    const { name, value } = event.target;
    setFilters((prev) => ({ ...prev, [name]: value }));
  }

  function onFilesChange(event) {
    const files = Array.from(event.target.files || []);
    setSelectedFiles(files.slice(0, MAX_FILES));
  }

  async function useCurrentLocation() {
    feedback.clear();
    setLocating(true);

    try {
      if (!window.navigator?.geolocation) {
        throw new Error('Tu navegador no soporta geolocalizacion.');
      }

      const position = await new Promise((resolve, reject) => {
        window.navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 8000,
          maximumAge: 0
        });
      });

      setForm((prev) => ({
        ...prev,
        latitude: String(position.coords.latitude.toFixed(6)),
        longitude: String(position.coords.longitude.toFixed(6))
      }));
      feedback.showSuccess('Ubicacion capturada correctamente.');
    } catch (err) {
      feedback.showError(err.message || 'No fue posible capturar la ubicacion.');
    } finally {
      setLocating(false);
    }
  }

  async function submitReport(event) {
    event.preventDefault();
    feedback.clear();
    setSubmitting(true);

    try {
      if (!form.regionId) {
        throw new Error('Selecciona una region.');
      }

      if (!form.comunaId) {
        throw new Error('Selecciona una comuna.');
      }

      if (!form.latitude || !form.longitude) {
        throw new Error('Debes indicar latitud y longitud.');
      }

      const subCategoryOptions = REPORT_SUBCATEGORIES[form.category];
      if (subCategoryOptions && !form.subCategory) {
        throw new Error('Selecciona una subcategoria.');
      }

      if (selectedFiles.length > MAX_FILES) {
        throw new Error(`Puedes adjuntar maximo ${MAX_FILES} fotos.`);
      }

      const hasOversized = selectedFiles.some((file) => file.size > MAX_FILE_BYTES);
      if (hasOversized) {
        throw new Error('Cada foto debe pesar maximo 5 MB.');
      }

      await createReport({
        payload: {
          regionId: form.regionId,
          comunaId: form.comunaId,
          category: form.category,
          subCategory: subCategoryOptions ? form.subCategory : '',
          description: form.description.trim(),
          latitude: Number(form.latitude),
          longitude: Number(form.longitude)
        },
        files: selectedFiles
      });

      setForm(initialForm);
      setSelectedFiles([]);
      feedback.showSuccess('Reporte ciudadano enviado correctamente.');
    } catch (err) {
      feedback.showError(err.message || 'No se pudo enviar el reporte.');
    } finally {
      setSubmitting(false);
    }
  }

  async function quickStatus(reportId, status) {
    feedback.clear();
    try {
      await setReportStatus(reportId, status);
      if (filters.status && filters.status !== status) {
        feedback.showSuccess(`Estado actualizado a ${status}. El reporte puede ocultarse por filtro actual.`);
      } else {
        feedback.showSuccess(`Estado actualizado a ${status}.`);
      }
    } catch (err) {
      feedback.showError(err.message || 'No se pudo actualizar el estado.');
    }
  }

  async function confirmDelete() {
    if (!pendingDeleteId) return;

    feedback.clear();
    try {
      await removeReport(pendingDeleteId);
      feedback.showSuccess('Reporte eliminado correctamente.');
    } catch (err) {
      feedback.showError(err.message || 'No se pudo eliminar el reporte.');
    } finally {
      setPendingDeleteId('');
    }
  }

  return (
    <section className="page-container">
      <SectionTitle
        title="Reportes ciudadanos"
        subtitle="Ingreso geolocalizado con evidencia y seguimiento operativo"
      />

      <p className="community-source-note">
        Origen de datos: {source === 'backend' ? 'backend de reportes' : 'fallback local de continuidad operativa'}.
      </p>

      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}
      {error ? <p className="feedback feedback-error">{error}</p> : null}

      <FilterBar>
        <label>
          Region
          <select name="regionId" value={filters.regionId} onChange={onFilterChange}>
            <option value="">Todas</option>
            {monitoredRegions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.nombre}
              </option>
            ))}
          </select>
        </label>

        <label>
          Estado
          <select name="status" value={filters.status} onChange={onFilterChange}>
            <option value="">Todos</option>
            {REPORT_STATUSES.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        </label>

        <label>
          Categoria
          <select name="category" value={filters.category} onChange={onFilterChange}>
            <option value="">Todas</option>
            {REPORT_CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </select>
        </label>

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={reload} disabled={loading}>
            {loading ? 'Recargando...' : 'Recargar'}
          </button>
        </div>
      </FilterBar>

      <article className="dashboard-card">
        <h3>Nuevo reporte</h3>
        <form className="form-grid" onSubmit={submitReport}>
          <label>
            Region
            <select name="regionId" value={form.regionId} onChange={onInputChange} required>
              <option value="">Seleccione region</option>
              {monitoredRegions.map((region) => (
                <option key={region.id} value={region.id}>
                  {region.nombre}
                </option>
              ))}
            </select>
          </label>

          <label>
            Comuna
            <select name="comunaId" value={form.comunaId} onChange={onInputChange} required disabled={!form.regionId}>
              <option value="">{form.regionId ? 'Seleccione comuna' : 'Seleccione una region primero'}</option>
              {formComunas.map((comuna) => (
                <option key={comuna.value} value={comuna.value}>
                  {comuna.label}
                </option>
              ))}
            </select>
          </label>

          <label>
            Categoria
            <select name="category" value={form.category} onChange={onInputChange}>
              {REPORT_CATEGORIES.map((category) => (
                <option key={category} value={category}>
                  {category}
                </option>
              ))}
            </select>
          </label>

          {REPORT_SUBCATEGORIES[form.category] ? (
            <label>
              Subcategoria
              <select name="subCategory" value={form.subCategory} onChange={onInputChange} required>
                <option value="">Seleccione subcategoria</option>
                {REPORT_SUBCATEGORIES[form.category].map((subCategory) => (
                  <option key={subCategory} value={subCategory}>
                    {subCategory}
                  </option>
                ))}
              </select>
            </label>
          ) : null}

          <label>
            Latitud
            <input name="latitude" type="number" step="0.000001" value={form.latitude} onChange={onInputChange} required />
          </label>

          <label>
            Longitud
            <input name="longitude" type="number" step="0.000001" value={form.longitude} onChange={onInputChange} required />
          </label>

          <label className="full-width">
            Descripcion
            <textarea name="description" rows={3} value={form.description} onChange={onInputChange} required />
          </label>

          <label className="full-width">
            Fotos (max {MAX_FILES}, 5 MB c/u)
            <input type="file" accept="image/*" multiple onChange={onFilesChange} />
            {selectedFiles.length > 0 ? <small>{selectedFiles.map((file) => file.name).join(' | ')}</small> : null}
          </label>

          <div className="form-actions">
            <button type="button" className="btn btn-secondary" onClick={useCurrentLocation} disabled={locating}>
              {locating ? 'Ubicando...' : 'Usar mi ubicacion'}
            </button>
            <button type="submit" className="btn" disabled={submitting}>
              {submitting ? 'Enviando...' : 'Enviar reporte'}
            </button>
          </div>
        </form>
      </article>

      <article className="dashboard-card">
        <h3>Seguimiento de reportes</h3>
        {loading ? <LoadingSpinner label="Cargando reportes..." /> : null}
        {!loading && filteredReports.length === 0 ? (
          <EmptyState title="Sin reportes" description="Aun no hay reportes para los filtros seleccionados." />
        ) : null}
        {!loading && filteredReports.length > 0 ? (
          <DataTable
            columns={columns}
            rows={filteredReports}
            rowKey="id"
            actions={(row) => (
              <div className="row-actions">
                <button type="button" className="btn btn-secondary" onClick={() => quickStatus(row.id, 'VALIDADO')}>
                  Validar
                </button>
                <button type="button" className="btn btn-secondary" onClick={() => quickStatus(row.id, 'DERIVADO')}>
                  Derivar
                </button>
                <button type="button" className="btn btn-secondary" onClick={() => quickStatus(row.id, 'DESCARTADO')}>
                  Descartar
                </button>
                <button type="button" className="btn btn-danger" onClick={() => setPendingDeleteId(row.id)}>
                  Eliminar
                </button>
              </div>
            )}
          />
        ) : null}
      </article>

      {!loading && error && source === 'backend' ? <ErrorMessage error={{ message: error }} onRetry={reload} /> : null}

      <ConfirmModal
        isOpen={Boolean(pendingDeleteId)}
        title="Eliminar reporte"
        message="Esta accion no se puede deshacer."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setPendingDeleteId('')}
      />

      {galleryReport ? (
        <div className="modal-backdrop" onClick={() => setGalleryReport(null)}>
          <article className="reports-gallery-dialog" onClick={(event) => event.stopPropagation()}>
            <div className="reports-photo-dialog-header">
              <strong>Galeria del reporte</strong>
              <button type="button" className="btn btn-secondary" onClick={() => setGalleryReport(null)}>
                Cerrar
              </button>
            </div>

            {galleryReport.photos?.length ? (
              <>
                <div className="reports-gallery-main">
                  {galleryReport.photos[activePhotoIndex]?.previewUrl ? (
                    <img
                      src={galleryReport.photos[activePhotoIndex].previewUrl}
                      alt={galleryReport.photos[activePhotoIndex].name}
                      className="reports-photo-dialog-image"
                    />
                  ) : (
                    <div className="reports-photo-no-preview">Vista previa no disponible</div>
                  )}
                </div>

                <div className="reports-gallery-thumbs">
                  {galleryReport.photos.map((photo, index) => (
                    <button
                      key={`${photo.name}-${index}`}
                      type="button"
                      className={`reports-gallery-thumb ${index === activePhotoIndex ? 'is-active' : ''}`}
                      onClick={() => setActivePhotoIndex(index)}
                    >
                      {photo.previewUrl ? <img src={photo.previewUrl} alt={photo.name} /> : <span>Sin vista previa</span>}
                    </button>
                  ))}
                </div>
              </>
            ) : (
              <EmptyState title="Sin fotos" description="Este reporte no registra imagenes asociadas." />
            )}
          </article>
        </div>
      ) : null}
    </section>
  );
}

export default CitizenReportsPage;
