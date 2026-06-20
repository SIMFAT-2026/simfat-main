import axios from 'axios';
import { API_BASE_URL, API_ENDPOINTS } from './endpoints';
import { toApiError } from './apiError';
import {
  clearAuthSession,
  getAccessToken,
  getAuthSession,
  updateSessionTokens
} from '../auth/tokenStorage';

// Capas territoriales (NDVI/NDMI) dependen del sync con Copernicus/CDSE, que puede
// tardar 130-220s en AOIs grandes (ver informe de sprint clima/viento). 15s causaba
// timeout y caida silenciosa a datos mock (createMockLayers) en useTerritoryLayers.
const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 240000,
  headers: {
    'Content-Type': 'application/json'
  },
  withCredentials: true
});

const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  },
  withCredentials: true
});

let isRefreshing = false;
let refreshSubscribers = [];

function subscribeTokenRefresh(resolve, reject) {
  refreshSubscribers.push({ resolve, reject });
}

function notifyTokenRefreshed(accessToken) {
  refreshSubscribers.forEach((subscriber) => subscriber.resolve(accessToken));
  refreshSubscribers = [];
}

function notifyRefreshFailed(error) {
  refreshSubscribers.forEach((subscriber) => subscriber.reject(error));
  refreshSubscribers = [];
}

async function refreshAccessToken() {
  const session = getAuthSession();
  const response = await refreshClient.post(API_ENDPOINTS.authRefresh, {
    refreshToken: session?.refreshToken || ''
  });
  const payload = response?.data?.data || response?.data || {};

  if (!payload.accessToken) {
    throw new Error('Respuesta inválida al refrescar sesión.');
  }

  updateSessionTokens({
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken,
    expiresAt: payload.expiresAt,
    user: payload.user
  });

  return payload.accessToken;
}

axiosClient.interceptors.request.use((config) => {
  const accessToken = getAccessToken();

  if (accessToken) {
    const nextHeaders = config.headers || {};
    if (!nextHeaders.Authorization) {
      nextHeaders.Authorization = `Bearer ${accessToken}`;
    }
    config.headers = nextHeaders;
  }

  return config;
});

axiosClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error?.config;
    const status = error?.response?.status;
    const requestUrl = originalRequest?.url || '';
    const isRefreshRequest = requestUrl.includes(API_ENDPOINTS.authRefresh);

    if (status !== 401 || !originalRequest || isRefreshRequest || originalRequest._retry) {
      if (status === 401 && isRefreshRequest) {
        clearAuthSession();
      }
      return Promise.reject(toApiError(error));
    }

    const session = getAuthSession();
    if (!session?.accessToken) {
      clearAuthSession();
      return Promise.reject(toApiError(error));
    }

    originalRequest._retry = true;

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        subscribeTokenRefresh(
          (newToken) => {
            originalRequest.headers = originalRequest.headers || {};
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            resolve(axiosClient(originalRequest));
          },
          (refreshError) => {
            reject(toApiError(refreshError));
          }
        );
      });
    }

    isRefreshing = true;

    try {
      const newToken = await refreshAccessToken();
      notifyTokenRefreshed(newToken);
      originalRequest.headers = originalRequest.headers || {};
      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return axiosClient(originalRequest);
    } catch (refreshError) {
      notifyRefreshFailed(refreshError);
      clearAuthSession();
      return Promise.reject(toApiError(refreshError));
    } finally {
      isRefreshing = false;
    }
  }
);

export default axiosClient;
