import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useFeedback } from '../../hooks/useFeedback';
import AuthCard from '../../components/auth/AuthCard';
import TurnstileWidget from '../../components/auth/TurnstileWidget';
import { TURNSTILE_ENABLED } from '../../components/auth/turnstile';

function ForgotPasswordPage() {
  const { requestPasswordReset } = useAuth();
  const { message, type, showSuccess, showError } = useFeedback();
  const authDevToolsEnabled = import.meta.env.VITE_AUTH_DEV_TOOLS_ENABLED === 'true';
  const [email, setEmail] = useState('');
  const [captchaToken, setCaptchaToken] = useState('');
  const [devResetUrl, setDevResetUrl] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event) {
    event.preventDefault();

    if (TURNSTILE_ENABLED && !captchaToken) {
      showError('Completa la verificacion de Cloudflare para continuar.');
      return;
    }

    setSubmitting(true);
    try {
      const result = await requestPasswordReset({ email, captchaToken });
      showSuccess(result.message);
      setDevResetUrl(result.devResetUrl || '');
    } catch (error) {
      showError(error.message || 'No fue posible procesar la solicitud.');
      setDevResetUrl('');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="Recuperar contraseña"
      subtitle="Te ayudamos a restablecer tu acceso"
      footer={
        <>
          <Link to="/login">Volver a iniciar sesión</Link>
          <Link to="/register">Crear cuenta nueva</Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="auth-form">
        <label>
          Correo registrado
          <input
            name="email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>

        <TurnstileWidget onTokenChange={setCaptchaToken} />

        {message ? <p className={`feedback feedback-${type}`}>{message}</p> : null}

        <button type="submit" className="btn" disabled={submitting}>
          {submitting ? 'Procesando...' : 'Enviar instrucciones'}
        </button>
      </form>

      {authDevToolsEnabled && devResetUrl ? (
        <div className="auth-dev-box">
          <p>Modo desarrollo: usa este enlace directo para resetear:</p>
          <Link to={devResetUrl}>Ir a restablecer contraseña</Link>
        </div>
      ) : null}
    </AuthCard>
  );
}

export default ForgotPasswordPage;
