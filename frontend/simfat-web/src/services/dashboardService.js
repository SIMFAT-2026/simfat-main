import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData } from '../api/responseAdapter';

export async function getDashboardSummary() {
  const response = await axiosClient.get(API_ENDPOINTS.dashboardSummary);
  return extractData(response.data);
}

export async function getDashboardCriticalRegions() {
  const response = await axiosClient.get(API_ENDPOINTS.dashboardCriticalRegions);
  return extractData(response.data);
}

export async function getDashboardLossTrend() {
  const response = await axiosClient.get(API_ENDPOINTS.dashboardLossTrend);
  return extractData(response.data);
}

export async function getDashboardAlertsSummary() {
  const response = await axiosClient.get(API_ENDPOINTS.dashboardAlertsSummary);
  return extractData(response.data);
}
