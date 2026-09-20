import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import ProtectedRoute from '../auth/ProtectedRoute';
import PublicOnlyRoute from '../auth/PublicOnlyRoute';

const MainLayout = lazy(() => import('../layouts/MainLayout'));
const HomePage = lazy(() => import('../pages/HomePage'));
const DashboardPage = lazy(() => import('../pages/DashboardPage'));
const TerritoryPage = lazy(() => import('../pages/TerritoryPage'));
const CommunityPage = lazy(() => import('../pages/CommunityPage'));
const CitizenReportsPage = lazy(() => import('../pages/CitizenReportsPage'));
const RegionsPage = lazy(() => import('../pages/RegionsPage'));
const AlertsPage = lazy(() => import('../pages/AlertsPage'));
const RulesPage = lazy(() => import('../pages/RulesPage'));
const AccessControlPage = lazy(() => import('../pages/AccessControlPage'));
const AccountPage = lazy(() => import('../pages/AccountPage'));
const NotFoundPage = lazy(() => import('../pages/NotFoundPage'));
const LoginPage = lazy(() => import('../pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('../pages/auth/RegisterPage'));
const ForgotPasswordPage = lazy(() => import('../pages/auth/ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => import('../pages/auth/ResetPasswordPage'));

function RouteLoader() {
  return <div className="loading-state">Cargando vista...</div>;
}

function withSuspense(element) {
  return <Suspense fallback={<RouteLoader />}>{element}</Suspense>;
}

// Leaf-level guard: redirects anonymous users to /login keeping state.from.
function guarded(element) {
  return <ProtectedRoute>{withSuspense(element)}</ProtectedRoute>;
}

// Keeps the query string (e.g. ?regionId=nuble) so shared links still work.
function LegacyMonitoringRedirect() {
  const location = useLocation();
  return <Navigate to={`/territorio${location.search}${location.hash}`} replace />;
}

function AppRouter() {
  return (
    <Routes>
      <Route
        path="/login"
        element={<PublicOnlyRoute>{withSuspense(<LoginPage />)}</PublicOnlyRoute>}
      />
      <Route
        path="/register"
        element={<PublicOnlyRoute>{withSuspense(<RegisterPage />)}</PublicOnlyRoute>}
      />
      <Route
        path="/forgot-password"
        element={<PublicOnlyRoute>{withSuspense(<ForgotPasswordPage />)}</PublicOnlyRoute>}
      />
      <Route
        path="/reset-password"
        element={<PublicOnlyRoute>{withSuspense(<ResetPasswordPage />)}</PublicOnlyRoute>}
      />

      {/* Legacy public route: the public view now lives at /territorio. */}
      <Route path="/monitoreo" element={<LegacyMonitoringRedirect />} />

      {/* One unguarded shell: anonymous visitors get the same layout. Guards
          live on the leaf routes that need a session. */}
      <Route element={withSuspense(<MainLayout />)}>
        <Route path="/" element={<Navigate to="/territorio" replace />} />
        <Route path="/home" element={guarded(<HomePage />)} />

        {/* Renders for anonymous users too (public mode inside the page). */}
        <Route path="/territorio" element={withSuspense(<TerritoryPage />)} />
        <Route path="/comunidad" element={guarded(<CommunityPage />)} />
        <Route path="/reportes" element={guarded(<CitizenReportsPage />)} />
        {/* TODO(S3b): unguard once AlertsPage has an anonymous read-only view. */}
        <Route path="/alertas" element={guarded(<AlertsPage />)} />

        <Route path="/admin/regions" element={guarded(<RegionsPage />)} />
        <Route path="/admin/rules" element={guarded(<RulesPage />)} />
        <Route path="/admin/access-control" element={guarded(<AccessControlPage />)} />

        <Route path="/account" element={guarded(<AccountPage />)} />

        <Route path="/dashboard" element={guarded(<DashboardPage />)} />
        <Route path="/alerts" element={<Navigate to="/alertas" replace />} />
        <Route path="/regions" element={<Navigate to="/admin/regions" replace />} />
        <Route path="/rules" element={<Navigate to="/admin/rules" replace />} />
      </Route>

      <Route path="*" element={withSuspense(<NotFoundPage />)} />
    </Routes>
  );
}

export default AppRouter;
