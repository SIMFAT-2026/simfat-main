import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useFeedback } from '../../hooks/useFeedback';
import AuthCard from '../../components/auth/AuthCard';
import TurnstileWidget from '../../components/auth/TurnstileWidget';
import { TURNSTILE_ENABLED } from '../../components/auth/turnstile';

const REMEMBERED_EMAIL_KEY = 'simfat_auth_remembered_email';

function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { createDevUsers, login } = useAuth();
  const { message, type, showError, showSuccess } = useFeedback();
  const [form, setForm] = useState({ email: '', password: '' });
  const [rememberUser, setRememberUser] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [captchaToken, setCaptchaToken] = useState('');
  const [seededUsers, setSeededUsers] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [creatingSeedUsers, setCreatingSeedUsers] = useState(false);

  const fromPath = location.state?.from?.pathname || '/dashboard';
  const authDevToolsEnabled = import.meta.env.VITE_AUTH_DEV_TOOLS_ENABLED === 'true';

  useEffect(() => {
    const rememberedEmail = window.localStorage.getItem(REMEMBERED_EMAIL_KEY);
    if (rememberedEmail) {
      setForm((prev) => ({ ...prev, email: rememberedEmail }));
      setRememberUser(true);
    }
  }, []);

  function onChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function onSubmit(event) {
    event.preventDefault();

    if (TURNSTILE_ENABLED && !captchaToken) {
      showError('Completa la verificacion de Cloudflare para continuar.');
      return;
    }

    setSubmitting(true);
    try {
      await login({ ...form, captchaToken });

      if (rememberUser) {
        window.localStorage.setItem(REMEMBERED_EMAIL_KEY, form.email.trim().toLowerCase());
      } else {
        window.localStorage.removeItem(REMEMBERED_EMAIL_KEY);
      }

      navigate(fromPath, { replace: true });
    } catch (error) {
      showError(error.message || 'No fue posible iniciar sesion.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCreateDevUsers() {
    setCreatingSeedUsers(true);
    setSeededUsers([]);
    try {
      const result = await createDevUsers({ count: 3 });
      setSeededUsers(result.users);
      showSuccess(result.message);
    } catch (error) {
      showError(error.message || 'No se pudieron crear usuarios de prueba.');
    } finally {
      setCreatingSeedUsers(false);
    }
  }

  return (
    <AuthCard
      title="Iniciar sesion"
      subtitle="Accede al panel operativo de SIMFAT"
      footer={
        <>
          <Link to="/register">Crear cuenta</Link>
          <Link to="/forgot-password">Recuperar contraseña</Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="auth-form">
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
              autoComplete="current-password"
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

        <label className="auth-inline-option">
          <input
            type="checkbox"
            checked={rememberUser}
            onChange={(event) => setRememberUser(event.target.checked)}
          />
          <span>Recordar usuario en este equipo</span>
        </label>

        <TurnstileWidget onTokenChange={setCaptchaToken} />

        {message ? <p className={`feedback feedback-${type}`}>{message}</p> : null}

        <button type="submit" className="btn" disabled={submitting}>
          {submitting ? 'Ingresando...' : 'Entrar'}
        </button>
      </form>

      {authDevToolsEnabled ? (
        <div className="auth-dev-box">
          <p>Entorno dev: generar usuarios de prueba desde backend (sin hardcode).</p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={handleCreateDevUsers}
            disabled={creatingSeedUsers}
          >
            {creatingSeedUsers ? 'Creando usuarios...' : 'Crear usuarios de prueba'}
          </button>
          {seededUsers.length > 0 ? (
            <ul className="auth-dev-users">
              {seededUsers.map((item) => (
                <li key={item.email}>
                  {item.email} | {item.password}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </AuthCard>
  );
}

export default LoginPage;
