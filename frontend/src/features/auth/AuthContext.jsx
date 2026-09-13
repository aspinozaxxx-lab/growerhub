import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
/* eslint-disable react-refresh/only-export-components */
import { registerAuthHandlers, resetApiSession } from '../../api/client';
import { requestAccountRefresh, requestCurrentUser, requestPasswordLogin } from '../../api/auth';
import { startDemoSession, saveDemoSession, resetDemoSession, refreshDemoSession } from '../../api/demo';
import { getCurrentLocale, translateApp } from '../../locales/i18n';
import { DEFAULT_UI_TIME_ZONE, setUiTimeZone } from '../../utils/formatters';
import { trackProductGoal } from '../../utils/analytics';

const AuthContext = createContext(undefined);
const DEMO_MODE_KEY = 'gh_demo_active';
const readDemoMode = () => {
  try { return sessionStorage.getItem(DEMO_MODE_KEY) === '1'; }
  catch { return false; }
};

function AuthProvider({ children }) {
  const [accountStatus, setAccountStatus] = useState('idle');
  const [accountUser, setAccountUser] = useState(null);
  const [accountToken, setAccountToken] = useState(null);
  const [demoActive, setDemoActive] = useState(readDemoMode);
  const [demoSession, setDemoSession] = useState(null);
  const [demoStatus, setDemoStatus] = useState('idle');
  const [sessionKey, setSessionKey] = useState(0);
  const [error, setError] = useState(null);
  const [redirectAfterLogin, setRedirectAfterLoginState] = useState(null);
  const accountTokenRef = useRef(null);
  const demoTokenRef = useRef(null);
  const modeRef = useRef(demoActive ? 'demo' : 'account');
  const demoOperation = useRef(0);
  const accountOperation = useRef(0);
  const accountUserRef = useRef(null);
  const refreshAccountPromise = useRef(null);

  const clearError = useCallback(() => setError(null), []);
  const setRedirectAfterLogin = useCallback((target) => {
    if (target && target.startsWith('/app/') && !target.includes('\\') && !target.startsWith('/app/login/')) {
      setRedirectAfterLoginState(target);
    }
  }, []);
  const consumeRedirectAfterLogin = useCallback(() => {
    const target = redirectAfterLogin || '/app/';
    setRedirectAfterLoginState(null);
    return target;
  }, [redirectAfterLogin]);

  const storeAccountToken = useCallback((token) => {
    accountTokenRef.current = token; setAccountToken(token);
  }, []);
  const clearAccount = useCallback(() => {
    accountOperation.current += 1;
    storeAccountToken(null); accountUserRef.current = null; setAccountUser(null); setAccountStatus('unauthorized');
  }, [storeAccountToken]);
  const setMode = useCallback((active) => {
    resetApiSession(); demoOperation.current += 1;
    modeRef.current = active ? 'demo' : 'account'; setDemoActive(active); setSessionKey((value) => value + 1);
    try {
      if (active) sessionStorage.setItem(DEMO_MODE_KEY, '1'); else sessionStorage.removeItem(DEMO_MODE_KEY);
    } catch { /* Translitem: rezhim ostajotsja v pamjati vkladki. */ }
  }, []);
  const leaveDemo = useCallback(() => {
    setMode(false); demoTokenRef.current = null; setDemoSession(null); setDemoStatus('idle');
    setUiTimeZone(accountUserRef.current?.timezone || DEFAULT_UI_TIME_ZONE);
  }, [setMode]);
  const logout = useCallback(() => {
    leaveDemo(); clearAccount(); setError(null); setRedirectAfterLoginState(null);
  }, [leaveDemo, clearAccount]);

  const refreshAccount = useCallback(async () => {
    if (!refreshAccountPromise.current) {
      const operation = accountOperation.current;
      refreshAccountPromise.current = (async () => {
        const response = await requestAccountRefresh();
        if (!response.ok) return null;
        const payload = await response.json();
        if (operation !== accountOperation.current) return null;
        storeAccountToken(payload?.access_token || null);
        return payload?.access_token || null;
      })().finally(() => { refreshAccountPromise.current = null; });
    }
    return refreshAccountPromise.current;
  }, [storeAccountToken]);

  const refreshDemo = useCallback(async () => {
    const operation = demoOperation.current;
    const send = () => refreshDemoSession(accountTokenRef.current);
    let response = await send();
    if (response.status === 401 && accountTokenRef.current && await refreshAccount()) response = await send();
    if (!response.ok || operation !== demoOperation.current) return null;
    const payload = await response.json();
    if (!payload?.access_token || operation !== demoOperation.current) return null;
    demoTokenRef.current = payload.access_token; setDemoSession(payload.space); setDemoStatus('authorized');
    if (modeRef.current === 'demo') setUiTimeZone(payload.space?.timezone);
    return payload.access_token;
  }, [refreshAccount]);

  useEffect(() => {
    registerAuthHandlers({
      getMode: () => modeRef.current,
      getToken: (scope) => scope === 'demo' ? demoTokenRef.current : accountTokenRef.current,
      refresh: (scope) => scope === 'demo' ? refreshDemo() : refreshAccount(),
      expire: (scope) => {
        if (scope === 'demo') {
          resetApiSession(); demoOperation.current += 1; demoTokenRef.current = null; setDemoStatus('unauthorized');
        } else clearAccount();
      },
      onDemoAction: (url, method) => trackProductGoal('demo_action', { action: url.split('/')[2], step: method || 'POST' }),
    });
    return () => registerAuthHandlers({});
  }, [refreshDemo, refreshAccount, clearAccount]);

  const loadCurrentUser = useCallback(async (providedToken) => {
    const operation = accountOperation.current;
    if (providedToken) storeAccountToken(providedToken);
    setAccountStatus((previous) => previous === 'authorized' ? previous : 'loading');
    try {
      const response = await requestCurrentUser();
      if (!response.ok) throw new Error('unauthorized');
      const data = await response.json();
      if (operation !== accountOperation.current) return { success: false };
      accountUserRef.current = data; setAccountUser(data); setAccountStatus('authorized'); setError(null);
      if (modeRef.current === 'account') setUiTimeZone(data?.timezone);
      return { success: true, user: data };
    } catch (failure) {
      if (failure.name !== 'AbortError') clearAccount();
      return { success: false };
    }
  }, [storeAccountToken, clearAccount]);

  const loginWithPassword = useCallback(async (email, password) => {
    setAccountStatus('loading');
    const operation = accountOperation.current;
    try {
      const response = await requestPasswordLogin(email, password);
      if (!response.ok) throw new Error('unauthorized');
      const payload = await response.json();
      if (operation !== accountOperation.current) return { success: false };
      if (!payload?.access_token) throw new Error('invalid');
      return await loadCurrentUser(payload.access_token);
    } catch {
      setError(translateApp('Неверный email или пароль')); setAccountStatus('unauthorized'); return { success: false };
    }
  }, [loadCurrentUser]);

  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.has('access_token')) {
      url.searchParams.delete('access_token');
      window.history.replaceState({}, document.title, url.pathname + url.search + url.hash);
    }
    try { localStorage.removeItem('gh_access_token'); } catch { /* Translitem: legacy token bolshe ne hranitsja na diske. */ }
    let cancelled = false;
    (async () => {
      await loadCurrentUser();
      if (!cancelled && modeRef.current === 'demo') {
        setDemoStatus('loading');
        if (!await refreshDemo() && !cancelled) setDemoStatus('unauthorized');
      }
    })();
    return () => { cancelled = true; };
  }, [loadCurrentUser, refreshDemo]);

  const acceptDemo = useCallback((payload) => {
    setMode(true); demoTokenRef.current = payload.access_token;
    setDemoSession(payload.space); setDemoStatus('authorized'); setUiTimeZone(payload.space?.timezone);
  }, [setMode]);
  const startDemo = useCallback(async () => {
    const response = await startDemoSession(getCurrentLocale(), Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
    if (!response.ok) return { success: false, status: response.status };
    acceptDemo(await response.json()); trackProductGoal('demo_ready', { placement: 'demo_entry' });
    return { success: true };
  }, [acceptDemo]);
  const saveDemo = useCallback(async (replace = false) => {
    const response = await saveDemoSession(replace);
    if (!response.ok) return { success: false, status: response.status };
    acceptDemo(await response.json()); trackProductGoal('demo_save'); return { success: true };
  }, [acceptDemo]);
  const resetDemo = useCallback(async () => {
    const response = await resetDemoSession();
    if (!response.ok) return { success: false, status: response.status };
    acceptDemo(await response.json()); trackProductGoal('demo_reset'); return { success: true };
  }, [acceptDemo]);

  const demoUser = useMemo(() => demoSession ? {
    id: `demo:${demoSession.id}`, role: 'demo', username: translateApp('Демоферма'), active: true,
    timezone: demoSession.timezone, onboarding_completed: true,
  } : null, [demoSession]);
  const value = {
    status: demoActive ? demoStatus : accountStatus, user: demoActive ? demoUser : accountUser,
    token: demoActive ? demoTokenRef.current : accountToken, error, redirectAfterLogin,
    accountStatus, accountUser, demoActive, demoSession, sessionKey,
    loginWithPassword, loadCurrentUser, logout, setRedirectAfterLogin, consumeRedirectAfterLogin, clearError,
    startDemo, saveDemo, resetDemo, leaveDemo,
    setCurrentUser: (nextUser) => {
      accountUserRef.current = nextUser; setAccountUser(nextUser);
      if (!demoActive) setUiTimeZone(nextUser?.timezone);
    },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
export { AuthProvider, useAuth };
