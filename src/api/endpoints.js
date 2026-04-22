export const API_BASE_URL =
  import.meta.env.NEXT_PUBLIC_API_BASE_URL || import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const API_ENDPOINTS = {
  authLogin: '/api/auth/login',
  authRefresh: '/api/auth/refresh',
  authRegister: '/api/auth/register',
  authForgotPassword: '/api/auth/forgot-password',
  authResetPassword: '/api/auth/reset-password',
  authMe: '/api/auth/me',
  authLogout: '/api/auth/logout',
  authDevSeedUsers: '/api/auth/dev/seed-users',
  regions: '/api/regions',
  forestLoss: '/api/forest-loss',
  alerts: '/api/alerts',
  alertsMap: '/api/alerts/map',
  rules: '/api/rules',
  citizenReports: '/api/citizen-reports',
  communityBoard: '/api/community/board',
  communityResources: '/api/community/resources',
  communityContacts: '/api/community/contacts',
  dashboardSummary: '/api/dashboard/summary',
  dashboardCriticalRegions: '/api/dashboard/critical-regions',
  dashboardLossTrend: '/api/dashboard/loss-trend',
  dashboardAlertsSummary: '/api/dashboard/alerts-summary'
};
