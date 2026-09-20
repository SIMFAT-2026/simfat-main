import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import ConfirmModal from '../components/ConfirmModal';
import AlertsReadView from '../features/alerts/components/AlertsReadView';
import { useAlertsData } from '../features/alerts/hooks/useAlertsData';
import { RISK_LEVELS } from '../features/alerts/utils/alertsLogic';
import { useFeedback } from '../hooks';
import { createAlert, deleteAlert, updateAlert } from '../services';
import { asNumberOrNull } from '../utils/data';
import { mapValidationErrors } from '../utils/errors';

const initialForm = {
  regionId: '',
  fechaEvento: '',
  nivelRiesgo: 'BAJO',
  latitud: '',
  longitud: '',
  fuente: '',
  descripcion: ''
};

function toCsvRow(cells) {
  return cells.map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(',');
}

function exportAlertsCsv(alerts, regionMap) {
  const headers = ['ID', 'Region', 'Fecha evento', 'Nivel riesgo', 'Fuente', 'Descripcion', 'Latitud', 'Longitud'];
  const rows = alerts.map((a) => [
    a.id,
    regionMap[a.regionId] || a.regionId,
    a.fechaEvento,
    a.nivelRiesgo,
    a.fuente,
    a.descripcion,
    a.latitud,
    a.longitud
  ]);
  const csv = [toCsvRow(headers), ...rows.map(toCsvRow)].join('\n');
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `alertas_${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

// Waits for the session bootstrap so the data mode (public vs authenticated) is
// decided once with the final session state; keyed on the mode so a login/logout
// while mounted remounts with clean state. Anonymous visitors never reach the
// CRUD component nor any authenticated request.
function AlertsPage() {
  const { isAuthenticated, isBootstrapping } = useAuth();

  if (isBootstrapping) {
    return <div className="loading-state">Validando sesión...</div>;
  }

  return isAuthenticated ? <AuthenticatedAlerts key="auth" /> : <PublicAlerts key="public" />;
}

function PublicAlerts() {
  const data = useAlertsData({ publicMode: true });
  return <AlertsReadView data={data} />;
}

function AuthenticatedAlerts() {
  const data = useAlertsData({ publicMode: false });
  const { regions, regionMap, alerts, loadAlerts } = data;
  const [form, setForm] = useState(initialForm);
  const [editingId, setEditingId] = useState('');
  const [validationErrors, setValidationErrors] = useState({});
  const [deleteId, setDeleteId] = useState('');
  const feedback = useFeedback();

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

  function startEdit(alert) {
    setEditingId(alert.id);
    setForm({
      regionId: alert.regionId || '',
      fechaEvento: alert.fechaEvento ? String(alert.fechaEvento).slice(0, 16) : '',
      nivelRiesgo: alert.nivelRiesgo || 'BAJO',
      latitud: String(alert.latitud || ''),
      longitud: String(alert.longitud || ''),
      fuente: alert.fuente || '',
      descripcion: alert.descripcion || ''
    });
    setValidationErrors({});
    feedback.clear();
  }

  async function onSubmit(event) {
    event.preventDefault();
    feedback.clear();
    setValidationErrors({});

    const payload = {
      regionId: form.regionId,
      fechaEvento: form.fechaEvento ? new Date(form.fechaEvento).toISOString() : null,
      nivelRiesgo: form.nivelRiesgo,
      latitud: asNumberOrNull(form.latitud),
      longitud: asNumberOrNull(form.longitud),
      fuente: form.fuente.trim(),
      descripcion: form.descripcion.trim()
    };

    try {
      if (editingId) {
        await updateAlert(editingId, payload);
        feedback.showSuccess('Alerta actualizada correctamente.');
      } else {
        await createAlert(payload);
        feedback.showSuccess('Alerta creada correctamente.');
      }

      resetForm();
      await loadAlerts();
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
      await deleteAlert(deleteId);
      feedback.showSuccess('Alerta eliminada correctamente.');
      setDeleteId('');
      await loadAlerts();
    } catch (err) {
      feedback.showError(err.message);
      setDeleteId('');
    }
  }

  return (
    <AlertsReadView
      data={data}
      feedback={feedback.message ? <p className={`feedback feedback-${feedback.type}`}>{feedback.message}</p> : null}
      onExportCsv={() => exportAlertsCsv(alerts, regionMap)}
      rowActions={(row) => (
        <div className="row-actions">
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => startEdit(row)}>
            Editar
          </button>
          <button type="button" className="btn btn-danger btn-sm" onClick={() => setDeleteId(row.id)}>
            Eliminar
          </button>
        </div>
      )}
    >
      {editingId ? (
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
            Fecha evento
            <input type="datetime-local" name="fechaEvento" value={form.fechaEvento} onChange={onInputChange} required />
            {validationErrors.fechaEvento ? <small className="field-error">{validationErrors.fechaEvento}</small> : null}
          </label>

          <label>
            Nivel riesgo
            <select name="nivelRiesgo" value={form.nivelRiesgo} onChange={onInputChange} required>
              {RISK_LEVELS.map((level) => (
                <option key={level} value={level}>
                  {level}
                </option>
              ))}
            </select>
            {validationErrors.nivelRiesgo ? <small className="field-error">{validationErrors.nivelRiesgo}</small> : null}
          </label>

          <label>
            Latitud
            <input name="latitud" type="number" step="0.000001" value={form.latitud} onChange={onInputChange} required />
            {validationErrors.latitud ? <small className="field-error">{validationErrors.latitud}</small> : null}
          </label>

          <label>
            Longitud
            <input name="longitud" type="number" step="0.000001" value={form.longitud} onChange={onInputChange} required />
            {validationErrors.longitud ? <small className="field-error">{validationErrors.longitud}</small> : null}
          </label>

          <label>
            Fuente
            <input name="fuente" value={form.fuente} onChange={onInputChange} required />
            {validationErrors.fuente ? <small className="field-error">{validationErrors.fuente}</small> : null}
          </label>

          <label className="full-width">
            Descripcion
            <textarea name="descripcion" value={form.descripcion} onChange={onInputChange} rows={3} />
            {validationErrors.descripcion ? <small className="field-error">{validationErrors.descripcion}</small> : null}
          </label>

          <div className="form-actions">
            <button className="btn" type="submit">Actualizar</button>
            <button type="button" className="btn btn-secondary" onClick={resetForm}>
              Cancelar edicion
            </button>
          </div>
        </form>
      ) : null}

      <ConfirmModal
        isOpen={Boolean(deleteId)}
        title="Eliminar alerta"
        message="Confirma la eliminacion del evento de alerta."
        confirmLabel="Eliminar"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteId('')}
      />
    </AlertsReadView>
  );
}

export default AlertsPage;
