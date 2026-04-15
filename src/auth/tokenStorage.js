const SESSION_KEY = 'simfat_auth_session';

function readSession() {
  try {
    const raw = window.localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function writeSession(session) {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function saveAuthSession({ user, accessToken, refreshToken = null, expiresAt = null }) {
  writeSession({ user, accessToken, refreshToken, expiresAt });
}

export function clearAuthSession() {
  window.localStorage.removeItem(SESSION_KEY);
}

export function getAuthSession() {
  return readSession();
}

export function getCurrentUser() {
  return readSession()?.user ?? null;
}

export function getAccessToken() {
  return readSession()?.accessToken ?? '';
}

export function updateSessionUser(user) {
  const session = readSession();
  if (!session) return;
  writeSession({ ...session, user });
}

export function updateSessionTokens({ accessToken, refreshToken, expiresAt, user }) {
  const session = readSession();
  if (!session) return;

  writeSession({
    ...session,
    user: user || session.user,
    accessToken: accessToken || session.accessToken,
    refreshToken: refreshToken ?? session.refreshToken ?? null,
    expiresAt: expiresAt ?? session.expiresAt ?? null
  });
}
