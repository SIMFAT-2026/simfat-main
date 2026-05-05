import axiosClient from '../api/axiosClient';
import { API_ENDPOINTS } from '../api/endpoints';
import { extractData, extractMessage } from '../api/responseAdapter';
import {
  clearAuthSession,
  getCurrentUser as getStoredUser,
  saveAuthSession,
  updateSessionUser
} from '../auth/tokenStorage';

function normalizeAuthPayload(payload) {
  const data = extractData(payload) || {};
  const user = data.user || null;
  const accessToken = data.accessToken || '';
  const refreshToken = data.refreshToken || null;
  const expiresAt = data.expiresAt || null;

  if (!user || !accessToken) {
    throw new Error('Respuesta de autenticacion incompleta desde backend.');
  }

  return { user, accessToken, refreshToken, expiresAt };
}

function persistAuth(payload) {
  const session = normalizeAuthPayload(payload);
  saveAuthSession(session);
  return session.user;
}

export function getCurrentUser() {
  return getStoredUser();
}

export async function login({ email, password, captchaToken = '', turnstileToken = '' }) {
  const response = await axiosClient.post(API_ENDPOINTS.authLogin, {
    email,
    password,
    turnstileToken: turnstileToken || captchaToken
  });

  return persistAuth(response.data);
}

export async function register({ name, fullName, email, password, captchaToken = '', turnstileToken = '' }) {
  const response = await axiosClient.post(API_ENDPOINTS.authRegister, {
    fullName: fullName || name,
    email,
    password,
    turnstileToken: turnstileToken || captchaToken
  });

  return persistAuth(response.data);
}

export async function requestPasswordReset({ email, captchaToken = '', turnstileToken = '' }) {
  const response = await axiosClient.post(API_ENDPOINTS.authForgotPassword, {
    email,
    turnstileToken: turnstileToken || captchaToken
  });

  const data = extractData(response.data) || {};
  return {
    ok: true,
    message:
      extractMessage(response.data) ||
      'Si el correo existe, te enviaremos instrucciones para recuperar tu acceso.',
    devResetToken: data.devResetToken || null,
    devResetUrl: data.devResetUrl || null
  };
}

export async function resetPassword({ email, token, newPassword, captchaToken = '', turnstileToken = '' }) {
  await axiosClient.post(API_ENDPOINTS.authResetPassword, {
    email,
    token,
    newPassword,
    turnstileToken: turnstileToken || captchaToken
  });
  return true;
}

export async function fetchCurrentUser() {
  const response = await axiosClient.get(API_ENDPOINTS.authMe);
  const user = extractData(response.data);
  if (user) {
    updateSessionUser(user);
  }
  return user;
}

export async function createDevUsers({ count = 3 } = {}) {
  const response = await axiosClient.post(API_ENDPOINTS.authDevSeedUsers, { count });
  const data = extractData(response.data) || {};
  return {
    message: extractMessage(response.data) || 'Usuarios de prueba creados.',
    users: Array.isArray(data.users) ? data.users : []
  };
}

export async function logout() {
  try {
    await axiosClient.post(API_ENDPOINTS.authLogout, {});
  } catch {
    // Ignoramos errores de logout remoto para no bloquear cierre local.
  } finally {
    clearAuthSession();
  }
}
