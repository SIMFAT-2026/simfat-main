const SCRIPT_ID = 'cf-turnstile-script';
const TURNSTILE_ENABLED = import.meta.env.VITE_AUTH_TURNSTILE_ENABLED === 'true';
const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || '';

function loadTurnstileScript() {
  return new Promise((resolve, reject) => {
    const existingScript = document.getElementById(SCRIPT_ID);
    if (existingScript) {
      if (window.turnstile) {
        resolve();
      } else {
        existingScript.addEventListener('load', resolve, { once: true });
        existingScript.addEventListener('error', reject, { once: true });
      }
      return;
    }

    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
    script.async = true;
    script.defer = true;
    script.addEventListener('load', resolve, { once: true });
    script.addEventListener('error', reject, { once: true });
    document.head.appendChild(script);
  });
}

export {
  TURNSTILE_ENABLED,
  TURNSTILE_SITE_KEY,
  loadTurnstileScript
};
