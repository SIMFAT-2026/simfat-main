import { Link } from 'react-router-dom';
import SectionTitle from '../components/SectionTitle';
import { homeQuickLinks } from '../router/navigationConfig';

function HomePage() {
  return (
    <section className="page-container">
      <SectionTitle title="NoFires" subtitle="Plataforma territorial para prevención y alerta temprana de incendios" />

      <p>
        Esta versión prioriza la monitorización territorial, la coordinación comunitaria, reportes ciudadanos y alertas
        operativas para Biobío y La Araucanía.
      </p>

      <div className="quick-links">
        {homeQuickLinks.map((link) => (
          <Link key={link.to} to={link.to} className="quick-link">
            {link.label}
          </Link>
        ))}
      </div>
    </section>
  );
}

export default HomePage;
