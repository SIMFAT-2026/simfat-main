import { Outlet } from 'react-router-dom';
import Navbar from '../components/layout/Navbar';
import Sidebar from '../components/layout/Sidebar';
import Footer from '../components/layout/Footer';
import '../styles/layout.css';

function MainLayout() {
  return (
    <div className="app-shell">
      <Navbar />
      <div className="app-content-wrapper">
        <Sidebar />
        <main className="app-main" aria-live="polite">
          <Outlet />
        </main>
      </div>
      <Footer />
    </div>
  );
}

export default MainLayout;
