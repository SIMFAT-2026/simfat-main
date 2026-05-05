import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import {
  createDevUsers,
  fetchCurrentUser,
  getCurrentUser,
  login as loginService,
  logout as logoutService,
  register as registerService,
  requestPasswordReset,
  resetPassword
} from '../services/authService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => getCurrentUser());
  const [isBootstrapping, setIsBootstrapping] = useState(true);

  useEffect(() => {
    let mounted = true;

    async function bootstrap() {
      if (!getCurrentUser()) {
        setIsBootstrapping(false);
        return;
      }

      try {
        const currentUser = await fetchCurrentUser();
        if (mounted) setUser(currentUser || null);
      } catch {
        if (mounted) setUser(null);
      } finally {
        if (mounted) setIsBootstrapping(false);
      }
    }

    bootstrap();

    return () => {
      mounted = false;
    };
  }, []);

  const value = useMemo(
    () => ({
      user,
      isBootstrapping,
      isAuthenticated: Boolean(user),
      login: async (credentials) => {
        const loggedUser = await loginService(credentials);
        setUser(loggedUser);
        return loggedUser;
      },
      register: async (payload) => {
        const registeredUser = await registerService(payload);
        setUser(registeredUser);
        return registeredUser;
      },
      logout: async () => {
        await logoutService();
        setUser(null);
      },
      requestPasswordReset: async (payload) => requestPasswordReset(payload),
      resetPassword: async (payload) => resetPassword(payload),
      createDevUsers: async (payload) => createDevUsers(payload)
    }),
    [isBootstrapping, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe usarse dentro de AuthProvider.');
  }
  return context;
}
