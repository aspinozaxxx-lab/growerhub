import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { registerAuthHandlers } from '../../api/client';

const STORAGE_KEY = 'gh_access_token';

const AuthContext = createContext(undefined);

function AuthProvider({ children }) {
  const [status, setStatus] = useState('idle');
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [error, setError] = useState(null);
  const [redirectAfterLogin, setRedirectAfterLoginState] = useState(null);

  const clearError = useCallback(() => setError(null), []);

  const setRedirectAfterLogin = useCallback((path) => {
    if (!path || path === '/app/login') {
      return;
    }
    setRedirectAfterLoginState(path);
  }, []);

  const consumeRedirectAfterLogin = useCallback(() => {
    const target = redirectAfterLogin || '/app';
    setRedirectAfterLoginState(null);
    return target;
  }, [redirectAfterLogin]);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setUser(null);
    setToken(null);
    setStatus('unauthorized');
    setRedirectAfterLoginState(null);
    setError(null);
  }, []);

  useEffect(() => {
    // Translitem: registriruem logout/getToken dlya fetch-wrapper, chtoby on mog razloginivat' pri neudachnom refresh.
    registerAuthHandlers({ logout });
  }, [logout]);

  const loadCurrentUser = useCallback(async (providedToken) => {
    const effectiveToken = providedToken || localStorage.getItem(STORAGE_KEY);
    if (!effectiveToken) {
      setUser(null);
      setToken(null);
      setStatus('unauthorized');
      return { success: false };
    }

    setStatus((prev) => (prev === 'authorized' ? prev : 'loading'));

    try {
      const response = await fetch('/api/auth/me', {
        headers: {
          Authorization: `Bearer ${effectiveToken}`,
        },
      });

      if (!response.ok) {
        throw new Error('unauthorized');
      }

      const data = await response.json();
      setUser(data);
      setToken(effectiveToken);
      setStatus('authorized');
      setError(null);

      return { success: true, user: data };
    } catch (_error) {
      localStorage.removeItem(STORAGE_KEY);
      setUser(null);
      setToken(null);
      setStatus('unauthorized');
      return { success: false };
    }
  }, []);

  const loginWithPassword = useCallback(
    async (email, password) => {
      setStatus('loading');
      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });

        if (!response.ok) {
          throw new Error('unauthorized');
        }

        const payload = await response.json();
        const accessToken = payload?.access_token;
        if (!accessToken) {
          throw new Error('invalid');
        }

        localStorage.setItem(STORAGE_KEY, accessToken);
        setToken(accessToken);
        setError(null);

        const result = await loadCurrentUser(accessToken);
        return { success: result.success !== false };
      } catch (_error) {
        setError('Неверный email или пароль');
        setStatus('unauthorized');
        return { success: false };
      }
    },
    [loadCurrentUser],
  );

  useEffect(() => {
    const url = new URL(window.location.href);
    const urlToken = url.searchParams.get('access_token');
    let effectiveToken = localStorage.getItem(STORAGE_KEY);

    if (urlToken) {
      // Translitem: podhvatyvaem token iz URL posle SSO i chistim ego iz stroki brauzera
      effectiveToken = urlToken;
      localStorage.setItem(STORAGE_KEY, urlToken);
      setToken(urlToken);

      url.searchParams.delete('access_token');
      const cleanedSearch = url.searchParams.toString();
      const cleanedUrl = `${url.pathname}${cleanedSearch ? `?${cleanedSearch}` : ''}${url.hash}`;
      window.history.replaceState({}, document.title, cleanedUrl);
    }

    if (effectiveToken) {
      setToken(effectiveToken);
      loadCurrentUser(effectiveToken);
    } else {
      setStatus('unauthorized');
    }
  }, [loadCurrentUser]);

  const value = useMemo(
    () => ({
      status,
      user,
      token,
      error,
      redirectAfterLogin,
      loginWithPassword,
      loadCurrentUser,
      logout,
      setRedirectAfterLogin,
      consumeRedirectAfterLogin,
      clearError,
    }),
    [
      status,
      user,
      token,
      error,
      redirectAfterLogin,
      loginWithPassword,
      loadCurrentUser,
      logout,
      setRedirectAfterLogin,
      consumeRedirectAfterLogin,
      clearError,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export { AuthProvider, useAuth };
