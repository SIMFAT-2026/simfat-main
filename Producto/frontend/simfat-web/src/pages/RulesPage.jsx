import { useEffect, useMemo, useRef, useState } from 'react';
import ConfirmModal from '../components/ConfirmModal';
import DataTable from '../components/DataTable';
import EmptyState from '../components/EmptyState';
import ErrorMessage from '../components/ErrorMessage';
import LoadingSpinner from '../components/LoadingSpinner';
import SectionTitle from '../components/SectionTitle';
import { useCloseDetailsOnOutsideClick, useFeedback } from '../hooks';
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
    presets: [
      { label: 'Moderado', value: 11 },
      { label: 'Alto', value: 21 },
      { label: 'Muy alto', value: 38 },
      { label: 'Extremo', value: 50 }
    ]
  },
  umbralNdmi: {
    presets: [
      { label: 'Estres leve', value: 0.1 },
      { label: 'Estres moderado', value: -0.1 },
      { label: 'Estres severo', value: -0.3 }
    ]
  },
  umbralNdvi: {
    presets: [
      { label: 'Vegetacion moderada', value: 0.5 },
      { label: 'Vegetacion escasa', value: 0.2 },
      { label: 'Sin vegetacion', value: 0.0 }
    ]
  },
  umbralFirmsCount: {
    presets: [
      { label: 'Bajo', value: 1 },
      { label: 'Moderado', value: 5 },
      { label: 'Alto', value: 15 },
      { label: 'Critico', value: 30 }
    ]
  },
  umbralReportesCiudadanos: {
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
  );
}

function FieldLabel({ children, tooltip }) {
  return (
    <span className="rule-field-label">
      {children}
      <details className="access-help metric-help">
        <summary>&#9432;</summary>
        <p>{tooltip}</p>
      </details>
    </span>
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
  useCloseDetailsOnOutsideClick();
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
      {
        key: 'activa',
        header: 'Activa',
        sortable: true,
        sortValue: (row) => (row.activa ? 1 : 0),
        render: (row) => (row.activa ? 'Si' : 'No')
      }
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

      <article ref={formRef} className={`dashboard-card rules-form-card${editingId ? ' rules-form-card--editing' : ''}`}>
        <div className="card-title-row">
          <h3>{editingId ? `Editando: ${form.nombre}` : 'Nueva regla'}</h3>
          <details className="access-help">
            <summary>&#9432; Como funcionan</summary>
            <ul>
              <li><strong>Que son:</strong> definen los umbrales que determinan cuando una region escala su nivel de alerta.</li>
              <li><strong>Logica OR:</strong> si cualquiera de los umbrales configurados se supera, la regla se activa.</li>
              <li><strong>Alcance:</strong> una regla puede aplicar a una region especifica o a todas (Global).</li>
              <li><strong>Activa / Inactiva:</strong> las reglas inactivas no generan alertas aunque se cumplan los umbrales.</li>
              <li><strong>Umbrales opcionales:</strong> deja en blanco los que no quieras usar. Solo se evaluan los que tienen valor.</li>
            </ul>
          </details>
        </div>

        {editingId ? (
          <div className="rules-edit-banner">
            <div className="rules-edit-banner-label">
              Editando regla: <strong>{form.nombre}</strong>
            </div>
            <button type="button" className="btn btn-secondary btn-sm" onClick={resetForm}>
              Cancelar edicion
            </button>
          </div>
        ) : null}

        <form onSubmit={onSubmit} className="rules-form">
          <p className="rules-form-section-title">Identificacion</p>

          <div className="form-grid rules-form-row-2col">
            <label>
              Nombre
              <input name="nombre" value={form.nombre} onChange={onInputChange} required />
              {validationErrors.nombre ? <small className="field-error">{validationErrors.nombre}</small> : null}
            </label>

            <label>
              <FieldLabel tooltip="Global aplica la regla a todas las regiones monitoreadas. Si eliges una region especifica, solo se evalua para ella.">
                Region (opcional)
              </FieldLabel>
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
          <p className="card-subtitle">Configura uno o mas umbrales. Cualquiera que se supere activa la regla. Deja en blanco los que no uses.</p>

          <div className="form-grid rules-form-row-3col">
            <label>
              <FieldLabel tooltip="Fire Weather Index (FWI). Indice meteorologico que combina temperatura, humedad, viento y precipitacion. Escala 0-100+. La regla dispara cuando el FWI regional supera este valor. FWI > 20 ya indica condicion de alerta; > 38 es muy alto; > 50 extremo.">
                Umbral FWI <small>(FWI &ge; valor)</small>
              </FieldLabel>
              <input name="umbralFwi" type="number" step="0.01" value={form.umbralFwi} onChange={onInputChange} />
              <ThresholdHints fieldName="umbralFwi" onSelect={setThreshold} />
              {validationErrors.umbralFwi ? <small className="field-error">{validationErrors.umbralFwi}</small> : null}
            </label>

            <label>
              <FieldLabel tooltip="Normalized Difference Moisture Index (NDMI). Mide la humedad en la vegetacion usando satelite. Escala -1 a +1: valores negativos indican vegetacion seca y mayor riesgo. La regla dispara cuando el NDMI cae POR DEBAJO de este valor.">
                Umbral NDMI <small>(NDMI &le; valor)</small>
              </FieldLabel>
              <input name="umbralNdmi" type="number" step="0.01" value={form.umbralNdmi} onChange={onInputChange} />
              <ThresholdHints fieldName="umbralNdmi" onSelect={setThreshold} />
              {validationErrors.umbralNdmi ? <small className="field-error">{validationErrors.umbralNdmi}</small> : null}
            </label>

            <label>
              <FieldLabel tooltip="Normalized Difference Vegetation Index (NDVI). Mide la densidad y salud de la cobertura vegetal. Escala 0 a +1: valores bajos indican poca vegetacion y mayor riesgo de propagacion. La regla dispara cuando el NDVI cae POR DEBAJO de este valor.">
                Umbral NDVI <small>(NDVI &le; valor)</small>
              </FieldLabel>
              <input name="umbralNdvi" type="number" step="0.01" value={form.umbralNdvi} onChange={onInputChange} />
              <ThresholdHints fieldName="umbralNdvi" onSelect={setThreshold} />
              {validationErrors.umbralNdvi ? <small className="field-error">{validationErrors.umbralNdvi}</small> : null}
            </label>
          </div>

          <div className="form-grid rules-form-row-count">
            <label>
              <FieldLabel tooltip="Focos de calor detectados por satelites NASA (VIIRS / MODIS) en las ultimas 48 horas. La regla dispara cuando el conteo de detecciones activas en la region supera este numero.">
                Umbral FIRMS <small>(detecciones &ge; valor)</small>
              </FieldLabel>
              <input name="umbralFirmsCount" type="number" value={form.umbralFirmsCount} onChange={onInputChange} />
              <ThresholdHints fieldName="umbralFirmsCount" onSelect={setThreshold} />
              {validationErrors.umbralFirmsCount ? <small className="field-error">{validationErrors.umbralFirmsCount}</small> : null}
            </label>

            <label>
              <FieldLabel tooltip="Numero de reportes ciudadanos activos recibidos en la region. Complementa las fuentes satelitales con informacion local. La regla dispara cuando los reportes superan este umbral.">
                Umbral reportes ciudadanos <small>(reportes &ge; valor)</small>
              </FieldLabel>
              <input name="umbralReportesCiudadanos" type="number" value={form.umbralReportesCiudadanos} onChange={onInputChange} />
              <ThresholdHints fieldName="umbralReportesCiudadanos" onSelect={setThreshold} />
              {validationErrors.umbralReportesCiudadanos ? (
                <small className="field-error">{validationErrors.umbralReportesCiudadanos}</small>
              ) : null}
            </label>

            <label>
              <FieldLabel tooltip="Si esta Inactiva, la regla no se evalua en los ciclos de scoring y no genera alertas aunque se superen los umbrales. Util para desactivar temporalmente sin eliminar la configuracion.">
                Activa
              </FieldLabel>
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
      </article>

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
