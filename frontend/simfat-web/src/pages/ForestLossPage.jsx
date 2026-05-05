import { useCallback, useEffect, useMemo, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import FilterBar from '../components/FilterBar';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useFeedback } from '../hooks';
import {
  createForestLossRecord,
  deleteForestLossRecord,
  getForestLossRecords,
  getRegions,
  updateForestLossRecord
} from '../services';
import { asNumberOrNull } from '../utils/data';
import { mapValidationErrors } from '../utils/errors';

const initialForm = {
  regionId: '',
  anio: '',
  hectareasPerdidas: '',
  porcentajePerdida: '',
  fuente: '',
  fechaRegistro: ''
};

function ForestLossPage() {
  const [regions, setRegions] = useState([]);
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [editingId, setEditingId] = useState('');
  const [validationErrors, setValidationErrors] = useState({});
  const [filterRegionId, setFilterRegionId] = useState('');
  const [filterYear, setFilterYear] = useState('');
  const [deleteId, setDeleteId] = useState('');
  const feedback = useFeedback();

  const loadRegions = useCallback(async () => {
    const data = await getRegions();
    setRegions(Array.isArray(data) ? data : []);
  }, []);

  const loadRecords = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await getForestLossRecords({
        regionId: filterRegionId || undefined,
        year: !filterRegionId ? filterYear || undefined : undefined
      });

      const list = Array.isArray(data) ? data : [];
      const filtered = filterRegionId && filterYear ? list.filter((item) => String(item.anio) === String(filterYear)) : list;
      setRecords(filtered);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [filterRegionId, filterYear]);

  useEffect(() => {
    async function init() {
      try {
        await Promise.all([loadRegions(), loadRecords()]);
      } catch (err) {
        setError(err);
        setLoading(false);
      }
    }

    init();
  }, [loadRegions, loadRecords]);

  useEffect(() => {
    loadRecords();
  }, [loadRecords]);

  const regionMap = useMemo(() => {
    return regions.reduce((acc, region) => {
      acc[region.id] = region.nombre;
      return acc;
    }, {});
  }, [regions]);

  const columns = useMemo(
    () => [
      { key: 'regionId', header: 'Region', render: (row) => regionMap[row.regionId] || row.regionId },
      { key: 'anio', header: 'Anio' },
      { key: 'hectareasPerdidas', header: 'Ha perdidas' },
      { key: 'porcentajePerdida', header: '% perdida' },
      { key: 'fuente', header: 'Fuente' },
      { key: 'fechaRegistro', header: 'Fecha registro' }
    ],
    [regionMap]
  );

  function onInputChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setValidationErrors((prev) => ({ ...prev, [name]: '' }));
  }

  function resetForm() {
    setForm(initialForm);
    setEditingId('');
    setValidationErrors({});
  }

  function startEdit(record) {
    setEditingId(record.id);
    setForm({
      regionId: record.regionId || '',
      anio: String(record.anio || ''),
      hectareasPerdidas: String(record.hectareasPerdidas || ''),
      porcentajePerdida: record.porcentajePerdida !== null && record.porcentajePerdida !== undefined ? String(record.porcentajePerdida) : '',
      fuente: record.fuente || '',
      fechaRegistro: record.fechaRegistro ? String(record.fechaRegistro).slice(0, 16) : ''
    });
    feedback.clear();
  }

  async function onSubmit(event) {
    event.preventDefault();
    feedback.clear();
    setValidationErrors({});

    const payload = {
      regionId: form.regionId,
      anio: asNumberOrNull(form.anio),
      hectareasPerdidas: asNumberOrNull(form.hectareasPerdidas),
      porcentajePerdida: asNumberOrNull(form.porcentajePerdida),
      fuente: form.fuente.trim(),
      fechaRegistro: form.fechaRegistro ? new Date(form.fechaRegistro).toISOString() : null
    };

    try {
      if (editingId) {
        await updateForestLossRecord(editingId, payload);
        feedback.showSuccess('Registro actualizado correctamente.');
      } else {
        await createForestLossRecord(payload);
        feedback.showSuccess('Registro creado correctamente.');
      }

      resetForm();
      await loadRecords();
    } catch (err) {
      setValidationErrors(mapValidationErrors(err.validationErrors));
      feedback.showError(err.message);
    }
  }

  async function confirmDelete() {
    if (!deleteId) {
      return;
    }

    try {
      await deleteForestLossRecord(deleteId);
      feedback.showSuccess('Registro eliminado correctamente.');
      setDeleteId('');
      await loadRecords();
    } catch (err) {
      feedback.showError(err.message);
      setDeleteId('');
    }
  }

  return (
    <section className="page-container">
      <SectionTitle title="Perdida Forestal" subtitle="Gestion de registros de perdida por region y anio" />

      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}

      <FilterBar>
        <label>
          Filtrar por region
          <select value={filterRegionId} onChange={(event) => setFilterRegionId(event.target.value)}>
            <option value="">Todas</option>
            {regions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.nombre}
              </option>
            ))}
          </select>
        </label>

        <label>
          Filtrar por anio
          <input type="number" value={filterYear} onChange={(event) => setFilterYear(event.target.value)} />
        </label>
      </FilterBar>

      <form className="form-grid" onSubmit={onSubmit}>
        <label>
          Region
          <select name="regionId" value={form.regionId} onChange={onInputChange} required>
            <option value="">Seleccione una region</option>
            {regions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.nombre}
              </option>
            ))}
          </select>
          {validationErrors.regionId ? <small className="field-error">{validationErrors.regionId}</small> : null}
        </label>

        <label>
          Anio
          <input name="anio" type="number" value={form.anio} onChange={onInputChange} required />
          {validationErrors.anio ? <small className="field-error">{validationErrors.anio}</small> : null}
        </label>

        <label>
          Hectareas perdidas
          <input name="hectareasPerdidas" type="number" step="0.01" value={form.hectareasPerdidas} onChange={onInputChange} required />
          {validationErrors.hectareasPerdidas ? <small className="field-error">{validationErrors.hectareasPerdidas}</small> : null}
        </label>

        <label>
          Porcentaje perdida
          <input name="porcentajePerdida" type="number" step="0.01" value={form.porcentajePerdida} onChange={onInputChange} />
          {validationErrors.porcentajePerdida ? <small className="field-error">{validationErrors.porcentajePerdida}</small> : null}
        </label>

        <label>
          Fuente
          <input name="fuente" value={form.fuente} onChange={onInputChange} required />
          {validationErrors.fuente ? <small className="field-error">{validationErrors.fuente}</small> : null}
        </label>

        <label>
          Fecha registro
          <input name="fechaRegistro" type="datetime-local" value={form.fechaRegistro} onChange={onInputChange} required />
          {validationErrors.fechaRegistro ? <small className="field-error">{validationErrors.fechaRegistro}</small> : null}
        </label>

        <div className="form-actions">
          <button className="btn" type="submit">
            {editingId ? 'Actualizar' : 'Crear'}
          </button>
          {editingId ? (
            <button type="button" className="btn btn-secondary" onClick={resetForm}>
              Cancelar edicion
            </button>
          ) : null}
        </div>
      </form>

      {loading ? <LoadingSpinner label="Cargando registros..." /> : null}
      {!loading && error ? <ErrorMessage error={error} onRetry={loadRecords} /> : null}
      {!loading && !error && records.length === 0 ? <EmptyState title="Sin registros" /> : null}
      {!loading && !error && records.length > 0 ? (
        <DataTable
          columns={columns}
          rows={records}
          rowKey="id"
          actions={(row) => (
            <div className="row-actions">
              <button type="button" className="btn btn-secondary" onClick={() => startEdit(row)}>
                Editar
              </button>
              <button type="button" className="btn btn-danger" onClick={() => setDeleteId(row.id)}>
                Eliminar
              </button>
            </div>
          )}
        />
      ) : null}

      <ConfirmModal
        isOpen={Boolean(deleteId)}
        title="Eliminar registro"
        message="Confirma la eliminacion del registro de perdida forestal."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteId('')}
      />
    </section>
  );
}

export default ForestLossPage;
