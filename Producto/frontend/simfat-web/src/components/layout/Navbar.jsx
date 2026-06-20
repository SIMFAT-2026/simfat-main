import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { primaryNavigationLinks } from '../../router/navigationConfig';
import NotificationBell from './NotificationBell';

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
          {primaryNavigationLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) => (isActive ? 'nav-link nav-link-active' : 'nav-link')}
            >
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="navbar-user">
          <NotificationBell />
          <Link to="/account" className="navbar-user-name navbar-account-link">
            {user?.name || user?.fullName || 'Usuario'}
          </Link>
          <button type="button" className="btn btn-secondary navbar-logout-btn" onClick={handleLogout}>
            Cerrar sesion
          </button>
        </div>
      </div>
    </header>
  );
}

export default Navbar;
