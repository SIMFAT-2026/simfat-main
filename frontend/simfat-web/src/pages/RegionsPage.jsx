import { useEffect, useMemo, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useFeedback } from '../hooks';
import { createRegion, deleteRegion, getRegions, updateRegion } from '../services';
import { asNumberOrNull } from '../utils/data';
import { mapValidationErrors } from '../utils/errors';

const initialForm = {
  nombre: '',
  codigo: '',
  zona: '',
  hectareasBosqueReferencia: ''
};

function RegionsPage() {
  const [regions, setRegions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  const [form, setForm] = useState(initialForm);
  const [editingId, setEditingId] = useState('');
  const [deleteId, setDeleteId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const feedback = useFeedback();

  async function loadRegions() {
    setLoading(true);
    setError(null);

    try {
      const data = await getRegions();
      setRegions(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadRegions();
  }, []);

  const columns = useMemo(
    () => [
      { key: 'nombre', header: 'Nombre' },
      { key: 'codigo', header: 'Codigo' },
      { key: 'zona', header: 'Zona' },
      {
        key: 'hectareasBosqueReferencia',
        header: 'Ha Referencia',
        render: (row) => row.hectareasBosqueReferencia ?? '-'
      }
    ],
    []
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

  function startEdit(region) {
    setEditingId(region.id);
    setForm({
      nombre: region.nombre || '',
      codigo: region.codigo || '',
      zona: region.zona || '',
      hectareasBosqueReferencia:
        region.hectareasBosqueReferencia !== undefined && region.hectareasBosqueReferencia !== null
          ? String(region.hectareasBosqueReferencia)
          : ''
    });
    feedback.clear();
    setValidationErrors({});
  }

  async function onSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    feedback.clear();
    setValidationErrors({});

    const payload = {
      nombre: form.nombre.trim(),
      codigo: form.codigo.trim(),
      zona: form.zona.trim(),
      hectareasBosqueReferencia: asNumberOrNull(form.hectareasBosqueReferencia)
    };

    try {
      if (editingId) {
        await updateRegion(editingId, payload);
        feedback.showSuccess('Region actualizada correctamente.');
      } else {
        await createRegion(payload);
        feedback.showSuccess('Region creada correctamente.');
      }

      resetForm();
      await loadRegions();
    } catch (err) {
      setValidationErrors(mapValidationErrors(err.validationErrors));
      feedback.showError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function confirmDelete() {
    if (!deleteId) {
      return;
    }

    try {
      await deleteRegion(deleteId);
      feedback.showSuccess('Region eliminada correctamente.');
      setDeleteId('');
      await loadRegions();
    } catch (err) {
      feedback.showError(err.message);
      setDeleteId('');
    }
  }

  return (
    <section className="page-container">
      <SectionTitle title="Regiones" subtitle="Gestion de regiones del sistema SIMFAT" />

      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}

      <form className="form-grid" onSubmit={onSubmit}>
        <label>
          Nombre
          <input name="nombre" value={form.nombre} onChange={onInputChange} required />
          {validationErrors.nombre ? <small className="field-error">{validationErrors.nombre}</small> : null}
        </label>

        <label>
          Codigo
          <input name="codigo" value={form.codigo} onChange={onInputChange} required />
          {validationErrors.codigo ? <small className="field-error">{validationErrors.codigo}</small> : null}
        </label>

        <label>
          Zona
          <input name="zona" value={form.zona} onChange={onInputChange} required />
          {validationErrors.zona ? <small className="field-error">{validationErrors.zona}</small> : null}
        </label>

        <label>
          Hectareas de referencia
          <input
            name="hectareasBosqueReferencia"
            type="number"
            step="0.01"
            value={form.hectareasBosqueReferencia}
            onChange={onInputChange}
          />
          {validationErrors.hectareasBosqueReferencia ? (
            <small className="field-error">{validationErrors.hectareasBosqueReferencia}</small>
          ) : null}
        </label>

        <div className="form-actions">
          <button className="btn" type="submit" disabled={submitting}>
            {submitting ? 'Guardando...' : editingId ? 'Actualizar' : 'Crear'}
          </button>
          {editingId ? (
            <button type="button" className="btn btn-secondary" onClick={resetForm}>
              Cancelar edicion
            </button>
          ) : null}
        </div>
      </form>

      {loading ? <LoadingSpinner label="Cargando regiones..." /> : null}
      {!loading && error ? <ErrorMessage error={error} onRetry={loadRegions} /> : null}
      {!loading && !error && regions.length === 0 ? <EmptyState title="Sin regiones" /> : null}
      {!loading && !error && regions.length > 0 ? (
        <DataTable
          columns={columns}
          rows={regions}
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
        title="Eliminar region"
        message="Esta accion no se puede deshacer."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteId('')}
      />
    </section>
  );
}

export default RegionsPage;
