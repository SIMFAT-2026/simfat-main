import { NavLink } from 'react-router-dom';
import { adminNavigationLinks, primaryNavigationLinks } from '../../router/navigationConfig';

const sidebarSections = [
  { title: 'Operacion', links: primaryNavigationLinks },
  { title: 'Administracion', links: adminNavigationLinks }
];

function Sidebar() {
  return (
    <aside className="sidebar" aria-label="Menu lateral">
      <h2>Modulos</h2>

      {sidebarSections.map((section) => (
        <div key={section.title} className="sidebar-section">
          <h3 className="sidebar-section-title">{section.title}</h3>
          <ul>
            {section.links.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  className={({ isActive }) => (isActive ? 'sidebar-link sidebar-link-active' : 'sidebar-link')}
                >
                  {item.label}
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
