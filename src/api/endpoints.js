export const API_BASE_URL =
  import.meta.env.NEXT_PUBLIC_API_BASE_URL || import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const API_ENDPOINTS = {
  regions: '/api/regions',
  forestLoss: '/api/forest-loss',
  alerts: '/api/alerts',
  rules: '/api/rules',
  dashboardSummary: '/api/dashboard/summary',
  dashboardCriticalRegions: '/api/dashboard/critical-regions',
  dashboardLossTrend: '/api/dashboard/loss-trend',
  dashboardAlertsSummary: '/api/dashboard/alerts-summary'
};
