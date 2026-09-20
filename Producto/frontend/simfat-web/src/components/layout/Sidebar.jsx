import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import {
  LOCKED_NAV_TITLE,
  adminNavigationLinks,
  isNavItemLocked,
  primaryNavigationLinks
} from '../../router/navigationConfig';

const sidebarSections = [
  { title: 'Operacion', links: primaryNavigationLinks },
  { title: 'Administracion', links: adminNavigationLinks }
];

function Sidebar({ collapsed = false, onToggle }) {
  const { isAuthenticated, isBootstrapping } = useAuth();

  return (
    <aside className="sidebar" aria-label="Menu lateral" data-collapsed={collapsed}>
      <div className="sidebar-header">
        {!collapsed && <h2>Modulos</h2>}
        <button
          type="button"
          className="sidebar-toggle"
          onClick={onToggle}
          aria-label={collapsed ? 'Expandir menu lateral' : 'Contraer menu lateral'}
          aria-expanded={!collapsed}
          title={collapsed ? 'Expandir menu' : 'Contraer menu'}
        >
          <span aria-hidden="true">{collapsed ? '>' : '<'}</span>
        </button>
      </div>

      {/* While the session bootstraps, render a fixed-size neutral placeholder
          (no empty headings/lists, no flash, no layout jump). */}
      {isBootstrapping && <div className="sidebar-placeholder" aria-hidden="true" />}
      {!isBootstrapping && sidebarSections.map((section) => (
        <div key={section.title} className="sidebar-section">
          {!collapsed && <h3 className="sidebar-section-title">{section.title}</h3>}
          <ul>
            {section.links.map((item) =>
              isNavItemLocked(item, isAuthenticated) ? (
                <li key={item.to}>
                  <Link
                    to="/login"
                    state={{ from: { pathname: item.to } }}
                    title={collapsed ? item.label : undefined}
                    className="sidebar-link sidebar-link-locked"
                  >
                    <span className="sidebar-link-marker" aria-hidden="true">
                      {item.label.slice(0, 1)}
                      <span className="sidebar-link-marker-lock">{'\u{1F512}'}</span>
                    </span>
                    <span className="sidebar-link-text">
                      <span aria-hidden="true">{'\u{1F512}'} </span>
                      {item.label}
                      <span className="sr-only"> ({LOCKED_NAV_TITLE.toLowerCase()})</span>
                    </span>
                  </Link>
                </li>
              ) : (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    title={collapsed ? item.label : undefined}
                    aria-label={collapsed ? item.label : undefined}
                    className={({ isActive }) => (isActive ? 'sidebar-link sidebar-link-active' : 'sidebar-link')}
                  >
                    <span className="sidebar-link-marker" aria-hidden="true">
                      {item.label.slice(0, 1)}
                    </span>
                    <span className="sidebar-link-text">{item.label}</span>
                  </NavLink>
                </li>
              )
            )}
          </ul>
        </div>
      ))}
    </aside>
  );
}

export default Sidebar;
