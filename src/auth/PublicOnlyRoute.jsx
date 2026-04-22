import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

function PublicOnlyRoute({ children }) {
  const { isAuthenticated, isBootstrapping } = useAuth();

  if (isBootstrapping) {
    return <div className="loading-state">Validando sesion...</div>;
  }

  if (isAuthenticated) {
    return <Navigate to="/territorio" replace />;
  }

  return children;
}

export default PublicOnlyRoute;
