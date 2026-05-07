import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useFeedback } from '../../hooks/useFeedback';
import AuthCard from '../../components/auth/AuthCard';
import TurnstileWidget from '../../components/auth/TurnstileWidget';
import { TURNSTILE_ENABLED } from '../../components/auth/turnstile';
import {
  getPasswordRequirementsStatus,
  getPasswordStrength,
  PASSWORD_MIN_LENGTH
} from '../../utils/passwordPolicy';

function RegisterPage() {
  const navigate = useNavigate();
  const { register } = useAuth();
  const { message, type, showError } = useFeedback();
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [captchaToken, setCaptchaToken] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const passwordChecks = useMemo(
    () => getPasswordRequirementsStatus(form.password),
    [form.password]
  );
  const passwordStrength = useMemo(
    () => getPasswordStrength(form.password),
    [form.password]
  );

  function onChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();

    if (form.password !== form.confirmPassword) {
      showError('Las contraseñas no coinciden.');
      return;
    }

    if (form.password.length < PASSWORD_MIN_LENGTH) {
      showError(`La contraseña debe tener al menos ${PASSWORD_MIN_LENGTH} caracteres.`);
      return;
    }

    const hasAllRules = passwordChecks.every((item) => item.passed);
    if (!hasAllRules) {
      showError('Debes cumplir todos los requisitos de seguridad de contraseña.');
      return;
    }

    if (TURNSTILE_ENABLED && !captchaToken) {
      showError('Completa la verificacion de Cloudflare para continuar.');
      return;
    }

    setSubmitting(true);
    try {
      await register({
        name: form.name,
        email: form.email,
        password: form.password,
        captchaToken
      });
      navigate('/territorio', { replace: true });
    } catch (error) {
      showError(error.message || 'No fue posible crear la cuenta.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="Crear cuenta"
      subtitle="Registro minimo para entorno de desarrollo SIMFAT"
      footer={
        <>
          <Link to="/login">Ya tengo cuenta</Link>
          <Link to="/forgot-password">Olvide mi contraseña</Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="auth-form">
        <label>
          Nombre completo
          <input
            name="name"
            type="text"
            required
            autoComplete="name"
            value={form.name}
            onChange={onChange}
          />
        </label>

        <label>
          Correo
          <input
            name="email"
            type="email"
            required
            autoComplete="email"
            value={form.email}
            onChange={onChange}
          />
        </label>

        <label>
          Contraseña
          <div className="auth-password-row">
            <input
              name="password"
              type={showPassword ? 'text' : 'password'}
              required
              minLength={PASSWORD_MIN_LENGTH}
              autoComplete="new-password"
              value={form.password}
              onChange={onChange}
            />
            <button
              type="button"
              className="btn btn-secondary auth-password-toggle"
              onClick={() => setShowPassword((prev) => !prev)}
            >
              {showPassword ? 'Ocultar' : 'Mostrar'}
            </button>
          </div>
        </label>

        <div className="auth-password-guidance">
          <p className="auth-password-title">Requisitos de password:</p>
          <ul className="auth-password-list">
            {passwordChecks.map((item) => (
              <li key={item.id} className={item.passed ? 'password-rule-ok' : 'password-rule-pending'}>
                {item.passed ? 'Cumple' : 'Pendiente'} | {item.label}
              </li>
            ))}
          </ul>
          <div className="auth-strength-wrapper" aria-live="polite">
            <div className="auth-strength-bar-bg">
              <div
                className={`auth-strength-bar auth-strength-bar-${passwordStrength.tone}`}
                style={{ width: `${passwordStrength.percent}%` }}
              />
            </div>
            <span className={`auth-strength-label auth-strength-label-${passwordStrength.tone}`}>
              Seguridad: {passwordStrength.label} ({passwordStrength.score}/{passwordStrength.max})
            </span>
          </div>
        </div>

        <label>
          Confirmar contraseña
          <div className="auth-password-row">
            <input
              name="confirmPassword"
              type={showConfirmPassword ? 'text' : 'password'}
              required
              minLength={PASSWORD_MIN_LENGTH}
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={onChange}
            />
            <button
              type="button"
              className="btn btn-secondary auth-password-toggle"
              onClick={() => setShowConfirmPassword((prev) => !prev)}
            >
              {showConfirmPassword ? 'Ocultar' : 'Mostrar'}
            </button>
          </div>
        </label>

        <TurnstileWidget onTokenChange={setCaptchaToken} />

        {message ? <p className={`feedback feedback-${type}`}>{message}</p> : null}

        <button type="submit" className="btn" disabled={submitting}>
          {submitting ? 'Creando cuenta...' : 'Registrarme'}
        </button>
      </form>
    </AuthCard>
  );
}

export default RegisterPage;
