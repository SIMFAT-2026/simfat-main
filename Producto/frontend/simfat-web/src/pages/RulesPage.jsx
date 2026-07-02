import { useEffect, useMemo, useRef, useState } from 'react';
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
  umbralFwi: '',
  umbralNdmi: '',
  umbralNdvi: '',
  umbralFirmsCount: '',
  umbralReportesCiudadanos: '',
  activa: 'true'
};

const THRESHOLD_PRESETS = {
  umbralFwi: {
    description: 'Escala 0-100+. Dispara cuando el FWI regional supera el umbral.',
    presets: [
      { label: 'Moderado', value: 11 },
      { label: 'Alto', value: 21 },
      { label: 'Muy alto', value: 38 },
      { label: 'Extremo', value: 50 }
    ]
  },
  umbralNdmi: {
    description: 'Escala -1 a +1. Dispara cuando el NDMI cae por debajo del umbral (mas seco = mas riesgo).',
    presets: [
      { label: 'Estres leve', value: 0.1 },
      { label: 'Estres moderado', value: -0.1 },
      { label: 'Estres severo', value: -0.3 }
    ]
  },
  umbralNdvi: {
    description: 'Escala 0 a +1. Dispara cuando el NDVI cae por debajo del umbral (menos vegetacion = mas riesgo).',
    presets: [
      { label: 'Vegetacion moderada', value: 0.5 },
      { label: 'Vegetacion escasa', value: 0.2 },
      { label: 'Sin vegetacion', value: 0.0 }
    ]
  },
  umbralFirmsCount: {
    description: 'Numero de detecciones activas FIRMS. Dispara cuando el conteo regional supera el umbral.',
    presets: [
      { label: 'Bajo', value: 1 },
      { label: 'Moderado', value: 5 },
      { label: 'Alto', value: 15 },
      { label: 'Critico', value: 30 }
    ]
  },
  umbralReportesCiudadanos: {
    description: 'Numero de reportes ciudadanos activos. Dispara cuando los reportes superan el umbral.',
    presets: [
      { label: 'Bajo', value: 3 },
      { label: 'Moderado', value: 10 },
      { label: 'Alto', value: 25 }
    ]
  }
};

function ThresholdHints({ fieldName, onSelect }) {
  const config = THRESHOLD_PRESETS[fieldName];
  if (!config) return null;

  return (
    <div className="threshold-hints">
      <small className="threshold-hints-desc">{config.description}</small>
      <div className="threshold-hints-presets">
        {config.presets.map((preset) => (
          <button
            key={preset.label}
            type="button"
            className="btn btn-secondary btn-xs"
            onClick={() => onSelect(fieldName, preset.value)}
          >
            {preset.label}: {preset.value}
          </button>
        ))}
      </div>
    </div>
  );
}

function summarizeThresholds(rule) {
  const parts = [];
  if (rule.umbralFwi != null) parts.push(`FWI >= ${rule.umbralFwi}`);
  if (rule.umbralNdmi != null) parts.push(`NDMI <= ${rule.umbralNdmi}`);
  if (rule.umbralNdvi != null) parts.push(`NDVI <= ${rule.umbralNdvi}`);
  if (rule.umbralFirmsCount != null) parts.push(`FIRMS >= ${rule.umbralFirmsCount}`);
  if (rule.umbralReportesCiudadanos != null) parts.push(`Reportes >= ${rule.umbralReportesCiudadanos}`);
  return parts.length > 0 ? parts.join(' · ') : 'Sin umbrales configurados';
}

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
  const formRef = useRef(null);

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

  useEffect(() => {
    if (editingId && formRef.current) {
      formRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, [editingId]);

  const regionMap = useMemo(() => {
    return regions.reduce((acc, region) => {
      acc[region.id] = region.nombre;
      return acc;
    }, {});
  }, [regions]);

  const columns = useMemo(
    () => [
      { key: 'nombre', header: 'Nombre', sortable: true },
      {
        key: 'regionId',
        header: 'Region',
        sortable: true,
        sortValue: (row) => (row.regionId ? regionMap[row.regionId] || row.regionId : 'Global'),
        render: (row) => (row.regionId ? regionMap[row.regionId] || row.regionId : 'Global')
      },
      { key: 'umbrales', header: 'Umbrales configurados', render: (row) => summarizeThresholds(row) },
      { key: 'activa', header: 'Activa', sortable: true, sortValue: (row) => (row.activa ? 1 : 0), render: (row) => (row.activa ? 'Si' : 'No') }
    ],
    [regionMap]
  );

  function onInputChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    setValidationErrors((prev) => ({ ...prev, [name]: '' }));
  }

  function setThreshold(name, value) {
    setForm((prev) => ({ ...prev, [name]: String(value) }));
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
      umbralFwi: rule.umbralFwi != null ? String(rule.umbralFwi) : '',
      umbralNdmi: rule.umbralNdmi != null ? String(rule.umbralNdmi) : '',
      umbralNdvi: rule.umbralNdvi != null ? String(rule.umbralNdvi) : '',
      umbralFirmsCount: rule.umbralFirmsCount != null ? String(rule.umbralFirmsCount) : '',
      umbralReportesCiudadanos: rule.umbralReportesCiudadanos != null ? String(rule.umbralReportesCiudadanos) : '',
      activa: String(Boolean(rule.activa))
    });
    setValidationErrors({});
    feedback.clear();
  }

  async function confirmDelete() {
    if (!deleteId) return;

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

  async function onSubmit(event) {
    event.preventDefault();
    feedback.clear();
    setValidationErrors({});

    const payload = {
      nombre: form.nombre.trim(),
      regionId: form.regionId || null,
      umbralFwi: asNumberOrNull(form.umbralFwi),
      umbralNdmi: asNumberOrNull(form.umbralNdmi),
      umbralNdvi: asNumberOrNull(form.umbralNdvi),
      umbralFirmsCount: asNumberOrNull(form.umbralFirmsCount),
      umbralReportesCiudadanos: asNumberOrNull(form.umbralReportesCiudadanos),
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

  return (
    <section className="page-container">
      <SectionTitle title="Reglas" subtitle="Configuracion de reglas de alerta" />

      {feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}

      <form ref={formRef} onSubmit={onSubmit} className={`rules-form${editingId ? ' rules-form--editing' : ''}`}>

        {editingId ? (
          <div className="rules-edit-banner">
            <div className="rules-edit-banner-label">
              <span className="rules-edit-banner-icon">✏️</span>
              Editando regla: <strong>{form.nombre}</strong>
            </div>
            <button type="button" className="btn btn-secondary btn-sm" onClick={resetForm}>
              Cancelar edicion
            </button>
          </div>
        ) : (
          <p className="rules-form-section-title">Nueva regla</p>
        )}

        <div className="form-grid rules-form-row-2col">
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
        </div>

        <p className="rules-form-section-title">Umbrales de activacion</p>

        <div className="form-grid rules-form-row-3col">
          <label>
            Umbral FWI <small>(FWI &ge; valor)</small>
            <input name="umbralFwi" type="number" step="0.01" value={form.umbralFwi} onChange={onInputChange} />
            <ThresholdHints fieldName="umbralFwi" onSelect={setThreshold} />
            {validationErrors.umbralFwi ? <small className="field-error">{validationErrors.umbralFwi}</small> : null}
          </label>

          <label>
            Umbral NDMI <small>(NDMI &le; valor, mas seco = mas riesgo)</small>
            <input name="umbralNdmi" type="number" step="0.01" value={form.umbralNdmi} onChange={onInputChange} />
            <ThresholdHints fieldName="umbralNdmi" onSelect={setThreshold} />
            {validationErrors.umbralNdmi ? <small className="field-error">{validationErrors.umbralNdmi}</small> : null}
          </label>

          <label>
            Umbral NDVI <small>(NDVI &le; valor, menos vegetacion = mas riesgo)</small>
            <input name="umbralNdvi" type="number" step="0.01" value={form.umbralNdvi} onChange={onInputChange} />
            <ThresholdHints fieldName="umbralNdvi" onSelect={setThreshold} />
            {validationErrors.umbralNdvi ? <small className="field-error">{validationErrors.umbralNdvi}</small> : null}
          </label>
        </div>

        <div className="form-grid rules-form-row-count">
          <label>
            Umbral FIRMS <small>(detecciones &ge; valor)</small>
            <input name="umbralFirmsCount" type="number" value={form.umbralFirmsCount} onChange={onInputChange} />
            <ThresholdHints fieldName="umbralFirmsCount" onSelect={setThreshold} />
            {validationErrors.umbralFirmsCount ? <small className="field-error">{validationErrors.umbralFirmsCount}</small> : null}
          </label>

          <label>
            Umbral reportes ciudadanos <small>(reportes &ge; valor)</small>
            <input name="umbralReportesCiudadanos" type="number" value={form.umbralReportesCiudadanos} onChange={onInputChange} />
            <ThresholdHints fieldName="umbralReportesCiudadanos" onSelect={setThreshold} />
            {validationErrors.umbralReportesCiudadanos ? (
              <small className="field-error">{validationErrors.umbralReportesCiudadanos}</small>
            ) : null}
          </label>

          <label>
            Activa
            <select name="activa" value={form.activa} onChange={onInputChange}>
              <option value="true">Si</option>
              <option value="false">No</option>
            </select>
            {validationErrors.activa ? <small className="field-error">{validationErrors.activa}</small> : null}
          </label>
        </div>

        <div className="form-actions">
          <button className="btn" type="submit">
            {editingId ? 'Actualizar regla' : 'Crear regla'}
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
          defaultSortKey="nombre"
          defaultSortDir="asc"
          actions={(row) => (
            <div className="row-actions">
              <button type="button" className="btn btn-secondary btn-sm" onClick={() => startEdit(row)}>
                Editar
              </button>
              <button type="button" className="btn btn-danger btn-sm" onClick={() => setDeleteId(row.id)}>
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
