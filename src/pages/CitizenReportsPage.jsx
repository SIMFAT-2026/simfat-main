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

const REPORT_CATEGORIES = ['HUMO', 'FOCO', 'QUEMA', 'INFRAESTRUCTURA', 'OTRO'];
const REPORT_STATUSES = ['RECIBIDO', 'VALIDADO', 'DERIVADO', 'DESCARTADO'];
const MAX_FILES = 4;
const MAX_FILE_BYTES = 5 * 1024 * 1024;

const initialForm = {
  regionId: '',
  category: 'HUMO',
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

function CitizenReportsPage() {
  const { regions, reports, source, loading, error, reload, createReport, setReportStatus, removeReport } = useCitizenReportsData();
  const feedback = useFeedback();

  const [form, setForm] = useState(initialForm);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [locating, setLocating] = useState(false);
  const [filters, setFilters] = useState({ regionId: '', status: '', category: '' });
  const [pendingDeleteId, setPendingDeleteId] = useState('');

  const regionMap = useMemo(
    () =>
      regions.reduce((acc, region) => {
        acc[region.id] = region.nombre;
        return acc;
      }, {}),
    [regions]
  );

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
      { key: 'category', header: 'Categoria' },
      {
        key: 'status',
        header: 'Estado',
        render: (row) => <span className={statusBadgeClass(row.status)}>{row.status}</span>
      },
      { key: 'latitude', header: 'Latitud', render: (row) => Number(row.latitude).toFixed(5) },
      { key: 'longitude', header: 'Longitud', render: (row) => Number(row.longitude).toFixed(5) },
      { key: 'photoCount', header: 'Fotos' },
      { key: 'description', header: 'Descripcion' }
    ],
    [regionMap]
  );

  function onInputChange(event) {
    const { name, value } = event.target;
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

      if (!form.latitude || !form.longitude) {
        throw new Error('Debes indicar latitud y longitud.');
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
          category: form.category,
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
      feedback.showSuccess(`Estado actualizado a ${status}.`);
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
            {regions.map((region) => (
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
              {regions.map((region) => (
                <option key={region.id} value={region.id}>
                  {region.nombre}
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
            {selectedFiles.length > 0 ? (
              <small>{selectedFiles.map((file) => file.name).join(' | ')}</small>
            ) : null}
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
    </section>
  );
}

export default CitizenReportsPage;
