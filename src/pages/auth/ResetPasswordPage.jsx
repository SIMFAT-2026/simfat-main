import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useFeedback } from '../../hooks/useFeedback';
import AuthCard from '../../components/auth/AuthCard';
import TurnstileWidget from '../../components/auth/TurnstileWidget';
import { TURNSTILE_ENABLED } from '../../components/auth/turnstile';

function ResetPasswordPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { resetPassword } = useAuth();
  const { message, type, showError, showSuccess } = useFeedback();

  const [form, setForm] = useState({
    email: params.get('email') || '',
    token: params.get('token') || '',
    newPassword: '',
    confirmPassword: ''
  });
  const [captchaToken, setCaptchaToken] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function onChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();

    if (form.newPassword !== form.confirmPassword) {
      showError('Las contraseñas no coinciden.');
      return;
    }

    if (form.newPassword.length < 8) {
      showError('La nueva contraseña debe tener al menos 8 caracteres.');
      return;
    }

    if (TURNSTILE_ENABLED && !captchaToken) {
      showError('Completa la verificacion de Cloudflare para continuar.');
      return;
    }

    setSubmitting(true);
    try {
      await resetPassword({
        email: form.email,
        token: form.token,
        newPassword: form.newPassword,
        captchaToken
      });
      showSuccess('Contraseña actualizada. Te redirigiremos al login.');
      window.setTimeout(() => navigate('/login', { replace: true }), 1200);
    } catch (error) {
      showError(error.message || 'No fue posible actualizar la contraseña.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="Restablecer contraseña"
      subtitle="Ingresa tu token de recuperación y una nueva contraseña"
      footer={<Link to="/login">Volver al login</Link>}
    >
      <form onSubmit={onSubmit} className="auth-form">
        <label>
          Correo
          <input
            name="email"
            type="email"
            required
            value={form.email}
            onChange={onChange}
          />
        </label>

        <label>
          Token de recuperación
          <input
            name="token"
            type="text"
            required
            value={form.token}
            onChange={onChange}
          />
        </label>

        <label>
          Nueva contraseña
          <input
            name="newPassword"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={form.newPassword}
            onChange={onChange}
          />
        </label>

        <label>
          Confirmar nueva contraseña
          <input
            name="confirmPassword"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={onChange}
          />
        </label>

        <TurnstileWidget onTokenChange={setCaptchaToken} />

        {message ? <p className={`feedback feedback-${type}`}>{message}</p> : null}

        <button type="submit" className="btn" disabled={submitting}>
          {submitting ? 'Actualizando...' : 'Actualizar contraseña'}
        </button>
      </form>
    </AuthCard>
  );
}

export default ResetPasswordPage;
