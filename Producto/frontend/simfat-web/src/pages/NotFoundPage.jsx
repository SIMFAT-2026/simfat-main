import { Link } from 'react-router-dom';

function NotFoundPage() {
  return (
    <section className="page-container not-found">
      <h2>Error 404</h2>
      <p>La página solicitada no existe.</p>
      <Link to="/" className="back-home-link">
        Volver al inicio
      </Link>
    </section>
  );
}

export default NotFoundPage;
