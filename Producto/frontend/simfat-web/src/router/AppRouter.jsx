import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from '../auth/ProtectedRoute';
import PublicOnlyRoute from '../auth/PublicOnlyRoute';

const MainLayout = lazy(() => import('../layouts/MainLayout'));
const HomePage = lazy(() => import('../pages/HomePage'));
const DashboardPage = lazy(() => import('../pages/DashboardPage'));
const TerritoryPage = lazy(() => import('../pages/TerritoryPage'));
const PublicMonitoringPage = lazy(() => import('../pages/PublicMonitoringPage'));
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

      {/* Publica, sin login (spec: Fase 1B portafolio) — visible con o sin sesion,
          por eso va fuera tanto de ProtectedRoute como de PublicOnlyRoute. */}
      <Route path="/monitoreo" element={withSuspense(<PublicMonitoringPage />)} />

      <Route
        element={<ProtectedRoute>{withSuspense(<MainLayout />)}</ProtectedRoute>}
      >
        <Route path="/" element={<Navigate to="/territorio" replace />} />
        <Route path="/home" element={withSuspense(<HomePage />)} />

        <Route path="/territorio" element={withSuspense(<TerritoryPage />)} />
        <Route path="/comunidad" element={withSuspense(<CommunityPage />)} />
        <Route path="/reportes" element={withSuspense(<CitizenReportsPage />)} />
        <Route path="/alertas" element={withSuspense(<AlertsPage />)} />

        <Route path="/admin/regions" element={withSuspense(<RegionsPage />)} />
        <Route path="/admin/rules" element={withSuspense(<RulesPage />)} />
        <Route path="/admin/access-control" element={withSuspense(<AccessControlPage />)} />

        <Route path="/account" element={withSuspense(<AccountPage />)} />

        <Route path="/dashboard" element={withSuspense(<DashboardPage />)} />
        <Route path="/alerts" element={<Navigate to="/alertas" replace />} />
        <Route path="/regions" element={<Navigate to="/admin/regions" replace />} />
        <Route path="/rules" element={<Navigate to="/admin/rules" replace />} />
      </Route>

      <Route path="*" element={withSuspense(<NotFoundPage />)} />
    </Routes>
  );
}

export default AppRouter;
