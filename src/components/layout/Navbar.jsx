import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

const topLinks = [
  { label: 'Inicio', to: '/' },
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Regiones', to: '/regions' },
  { label: 'Perdida Forestal', to: '/forest-loss' },
  { label: 'Alertas', to: '/alerts' },
  { label: 'Reglas', to: '/rules' }
];

function Navbar() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="navbar">
      <div className="navbar-brand">
        <h1>SIMFAT</h1>
        <span>Sistema Integrado de Monitoreo y Alerta Temprana Forestal</span>
      </div>

      <div className="navbar-actions">
        <nav className="navbar-nav" aria-label="Navegacion principal">
          {topLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                isActive ? 'nav-link nav-link-active' : 'nav-link'
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="navbar-user">
          <span className="navbar-user-name">{user?.name || 'Usuario'}</span>
          <button
            type="button"
            className="btn btn-secondary navbar-logout-btn"
            onClick={handleLogout}
          >
            Cerrar sesion
          </button>
        </div>
      </div>
    </header>
  );
}

export default Navbar;
