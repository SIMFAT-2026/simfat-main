import { useState } from 'react';
import type { SyncRunResultDto } from '../types';

interface SyncNowButtonProps {
  onSync: () => Promise<SyncRunResultDto>;
  onSyncSuccess: () => Promise<void> | void;
}

function SyncNowButton({ onSync, onSyncSuccess }: SyncNowButtonProps) {
  const [loading, setLoading] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const handleClick = async () => {
    try {
      setLoading(true);
      setFeedback(null);
      const result = await onSync();
      await onSyncSuccess();
      setFeedback({
        type: 'success',
        message: result.message || 'Sincronizacion ejecutada correctamente.'
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'No fue posible sincronizar ahora.';
      setFeedback({ type: 'error', message });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="sync-button-block">
      <button type="button" className="btn" onClick={handleClick} disabled={loading}>
        {loading ? 'Sincronizando...' : 'Sincronizar ahora'}
      </button>
      {feedback ? <p className={`sync-feedback sync-feedback-${feedback.type}`}>{feedback.message}</p> : null}
    </div>
  );
}

export default SyncNowButton;
