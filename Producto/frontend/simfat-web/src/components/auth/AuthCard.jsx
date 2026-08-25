import { Link } from 'react-router-dom';

function AuthCard({ title, subtitle, children, footer }) {
  return (
    <div className="auth-page">
      <section className="auth-card" aria-labelledby="auth-title">
        <header className="auth-header">
          <h2 id="auth-title">{title}</h2>
          {subtitle ? <p>{subtitle}</p> : null}
        </header>
        {children}
        {footer ? <footer className="auth-footer">{footer}</footer> : null}
      </section>
      <p className="auth-brand-note">
        NoFires | Monitoreo y Alerta Temprana Forestal
      </p>
      <Link to="/" className="auth-back-link">
        Volver al inicio
      </Link>
    </div>
  );
}

export default AuthCard;
