import { NavLink } from 'react-router-dom';
import { adminNavigationLinks, primaryNavigationLinks } from '../../router/navigationConfig';

const sidebarSections = [
  { title: 'Operacion', links: primaryNavigationLinks },
  { title: 'Administracion', links: adminNavigationLinks }
];

function Sidebar({ collapsed = false, onToggle }) {
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

      {sidebarSections.map((section) => (
        <div key={section.title} className="sidebar-section">
          {!collapsed && <h3 className="sidebar-section-title">{section.title}</h3>}
          <ul>
            {section.links.map((item) => (
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
            ))}
          </ul>
        </div>
      ))}
    </aside>
  );
}

export default Sidebar;
