import { useEffect, useRef, useState } from 'react';
import { TURNSTILE_ENABLED, TURNSTILE_SITE_KEY, loadTurnstileScript } from './turnstile';

function TurnstileWidget({ onTokenChange }) {
  const containerRef = useRef(null);
  const widgetIdRef = useRef(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!TURNSTILE_ENABLED) {
      onTokenChange('dev-bypass');
      return;
    }

    if (!TURNSTILE_SITE_KEY) {
      setError('Falta configurar VITE_TURNSTILE_SITE_KEY.');
      onTokenChange('');
      return;
    }

    let mounted = true;

    async function renderWidget() {
      try {
        await loadTurnstileScript();
        if (!mounted || !containerRef.current || !window.turnstile) return;

        widgetIdRef.current = window.turnstile.render(containerRef.current, {
          sitekey: TURNSTILE_SITE_KEY,
          callback: (token) => onTokenChange(token),
          'expired-callback': () => onTokenChange(''),
          'error-callback': () => {
            setError('No se pudo validar el captcha. Intenta nuevamente.');
            onTokenChange('');
          }
        });
      } catch {
        if (mounted) {
          setError('No se pudo cargar Cloudflare Turnstile.');
          onTokenChange('');
        }
      }
    }

    renderWidget();

    return () => {
      mounted = false;
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
      }
    };
  }, [onTokenChange]);

  if (!TURNSTILE_ENABLED) return null;

  return (
    <div className="turnstile-wrapper">
      <div ref={containerRef} />
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

export default TurnstileWidget;
