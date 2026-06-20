import { useState } from 'react';
import { useAuth } from '../../../auth/AuthContext';
import { triggerWeatherSync } from '../services/dashboardApiService';

const WEATHER_SYNC_ROLES = new Set(['ROLE_ADMIN', 'ROLE_SUPER_ADMIN']);

interface WeatherSyncButtonProps {
  regionId: string | null;
}

// ADMIN-only trigger for the FIRMS + Open-Meteo (clima/viento) sync. Backend
// endpoint already existed (POST /api/territory/sync) but had no UI caller —
// it could only be run via curl with a bearer token.
function WeatherSyncButton({ regionId }: WeatherSyncButtonProps) {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // user.roles es el enum legacy (solo "ADMIN"/"USER", sin SUPER_ADMIN) — no
  // sirve para gatear esto. roleCodes es el RBAC real (mismo que usa el
  // backend en @PreAuthorize), ahora expuesto via /api/auth/me.
  const roleCodes: string[] = user?.roleCodes || [];
  const canSync = roleCodes.some((role) => WEATHER_SYNC_ROLES.has(role));

  if (!canSync) {
    return null;
  }

  const handleClick = async () => {
    if (!regionId) {
      setFeedback({ type: 'error', message: 'Selecciona una region antes de sincronizar.' });
      return;
    }

    try {
      setLoading(true);
      setFeedback(null);
      const result = await triggerWeatherSync(regionId);
      setFeedback({
        type: 'success',
        message: result.message || 'Sincronizacion de clima y viento iniciada.'
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'No fue posible sincronizar el clima.';
      setFeedback({ type: 'error', message });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="sync-button-block">
      <button type="button" className="btn btn-secondary" onClick={handleClick} disabled={loading || !regionId}>
        {loading ? 'Sincronizando clima...' : 'Sincronizar clima y viento'}
      </button>
      {feedback ? <p className={`sync-feedback sync-feedback-${feedback.type}`}>{feedback.message}</p> : null}
    </div>
  );
}

export default WeatherSyncButton;
