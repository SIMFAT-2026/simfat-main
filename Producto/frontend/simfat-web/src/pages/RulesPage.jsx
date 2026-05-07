import { useEffect, useMemo, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useFeedback } from '../hooks';
import { createRule, deleteRule, getRegions, getRules, updateRule } from '../services';
import { asNumberOrNull } from '../utils/data';
import { mapValidationErrors } from '../utils/errors';

const initialForm = {
  nombre: '',
  regionId: '',
  umbralPorcentajePerdida: '',
  umbralEventosCalor: '',
  activa: 'true'
};

function RulesPage() {
  const [regions, setRegions] = useState([]);
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [editingId, setEditingId] = useState('');
  const [validationErrors, setValidationErrors] = useState({});
  const [deleteId, setDeleteId] = useState('');
  const feedback = useFeedback();

  async function loadRegions() {
    const data = await getRegions();
    setRegions(Array.isArray(data) ? data : []);
  }

  async function loadRules() {
    setLoading(true);
    setError(null);

    try {
      const data = await getRules();
      setRules(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    async function init() {
      try {
        await Promise.all([loadRegions(), loadRules()]);
      } catch (err) {
        setError(err);
        setLoading(false);
      }
    }

    init();
  }, []);

  const regionMap = useMemo(() => {
    return regions.reduce((acc, region) => {
      acc[region.id] = region.nombre;
      return acc;
    }, {});
  }, [regions]);

  const columns = useMemo(
    () => [
      { key: 'nombre', header: 'Nombre' },
      { key: 'regionId', header: 'Region', render: (row) => (row.regionId ? regionMap[row.regionId] || row.regionId : 'Global') },
      { key: 'umbralPorcentajePerdida', header: 'Umbral % perdida' },
      { key: 'umbralEventosCalor', header: 'Umbral eventos calor' },
      { key: 'activa', header: 'Activa', render: (row) => (row.activa ? 'Si' : 'No') }
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

  function startEdit(rule) {
    setEditingId(rule.id);
    setForm({
      nombre: rule.nombre || '',
      regionId: rule.regionId || '',
      umbralPorcentajePerdida: String(rule.umbralPorcentajePerdida || ''),
      umbralEventosCalor: String(rule.umbralEventosCalor || ''),
      activa: String(Boolean(rule.activa))
    });
    setValidationErrors({});
    feedback.clear();
  }

  async function onSubmit(event) {
    event.preventDefault();
    feedback.clear();
    setValidationErrors({});

    const payload = {
      nombre: form.nombre.trim(),
      regionId: form.regionId || null,
      umbralPorcentajePerdida: asNumberOrNull(form.umbralPorcentajePerdida),
      umbralEventosCalor: asNumberOrNull(form.umbralEventosCalor),
      activa: form.activa === 'true'
    };

    try {
      if (editingId) {
        await updateRule(editingId, payload);
        feedback.showSuccess('Regla actualizada correctamente.');
      } else {
        await createRule(payload);
        feedback.showSuccess('Regla creada correctamente.');
      }

      resetForm();
      await loadRules();
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
      await deleteRule(deleteId);
      feedback.showSuccess('Regla eliminada correctamente.');
      setDeleteId('');
      await loadRules();
    } catch (err) {
      feedback.showError(err.message);
      setDeleteId('');
    }
  }

  return (
    <section className="page-container">
      <SectionTitle title="Reglas" subtitle="Configuracion de reglas de alerta" />

      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}

      <form className="form-grid" onSubmit={onSubmit}>
        <label>
          Nombre
          <input name="nombre" value={form.nombre} onChange={onInputChange} required />
          {validationErrors.nombre ? <small className="field-error">{validationErrors.nombre}</small> : null}
        </label>

        <label>
          Region (opcional)
          <select name="regionId" value={form.regionId} onChange={onInputChange}>
            <option value="">Global</option>
            {regions.map((region) => (
              <option key={region.id} value={region.id}>
                {region.nombre}
              </option>
            ))}
          </select>
        </label>

        <label>
          Umbral porcentaje perdida
          <input
            name="umbralPorcentajePerdida"
            type="number"
            step="0.01"
            value={form.umbralPorcentajePerdida}
            onChange={onInputChange}
            required
          />
          {validationErrors.umbralPorcentajePerdida ? (
            <small className="field-error">{validationErrors.umbralPorcentajePerdida}</small>
          ) : null}
        </label>

        <label>
          Umbral eventos calor
          <input name="umbralEventosCalor" type="number" value={form.umbralEventosCalor} onChange={onInputChange} required />
          {validationErrors.umbralEventosCalor ? <small className="field-error">{validationErrors.umbralEventosCalor}</small> : null}
        </label>

        <label>
          Activa
          <select name="activa" value={form.activa} onChange={onInputChange}>
            <option value="true">Si</option>
            <option value="false">No</option>
          </select>
          {validationErrors.activa ? <small className="field-error">{validationErrors.activa}</small> : null}
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

      {loading ? <LoadingSpinner label="Cargando reglas..." /> : null}
      {!loading && error ? <ErrorMessage error={error} onRetry={loadRules} /> : null}
      {!loading && !error && rules.length === 0 ? <EmptyState title="Sin reglas" /> : null}
      {!loading && !error && rules.length > 0 ? (
        <DataTable
          columns={columns}
          rows={rules}
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
        title="Eliminar regla"
        message="Confirma la eliminacion de la regla seleccionada."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteId('')}
      />
    </section>
  );
}

export default RulesPage;
