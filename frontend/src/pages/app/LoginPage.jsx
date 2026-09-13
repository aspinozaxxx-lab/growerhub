import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import FormField from '../../components/ui/FormField';
import Button from '../../components/ui/Button';
import './LoginPage.css';
import { getCurrentLocale, translateApp } from '../../locales/i18n';
import { getPublicPath } from '../../domain/localizedRoutes';
import { DEMO_PUBLIC_ENABLED } from '../../domain/siteConfig';
import { trackProductGoal, trackProductGoalOnce } from '../../utils/analytics';
import { buildSsoLoginUrl } from '../../features/auth/sso';

function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    accountStatus: status,
    error,
    loginWithPassword,
    clearError,
    consumeRedirectAfterLogin,
    setRedirectAfterLogin,
  } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const redirected = useRef(false);
  const from = location.state?.from;
  const requested = new URLSearchParams(location.search).get('redirect')
    || (from?.pathname ? from.pathname + (from.search || '') : null);
  const destination = requested?.startsWith('/app/') && !requested.includes('\\')
    && !requested.startsWith('/app/login/') ? requested : null;

  useEffect(() => {
    clearError();
    if (destination) setRedirectAfterLogin(destination);
  }, [destination, setRedirectAfterLogin, clearError]);

  useEffect(() => {
    if (status !== 'authorized' || redirected.current) return;
    redirected.current = true;
    const stored = consumeRedirectAfterLogin();
    navigate(destination || stored, { replace: true });
  }, [status, destination, consumeRedirectAfterLogin, navigate]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    clearError();
    await loginWithPassword(email.trim(), password);
  };

  useEffect(() => { trackProductGoalOnce('login_view', { placement: 'login' }); }, []);

  const handleSSO = (provider) => {
    trackProductGoal('sso_start', { provider, mode: 'account' });
    const target = destination || consumeRedirectAfterLogin();
    window.location.href = buildSsoLoginUrl(
      provider,
      target,
      getCurrentLocale(),
      window.location.origin,
    );
  };

  const isLoading = status === 'loading';

  return (
    <div className="login-page">
      <div className="login-card">
        <h1>{translateApp("Начать работу с GrowerHub")}</h1>
        <p className="login-intro">{translateApp("GrowerHub доступен бесплатно и без карты. Войдите, чтобы подключить свои устройства или сохранить демоферму.")}</p>
        {DEMO_PUBLIC_ENABLED ? <a className="demo-link" href="/app/demo/">{translateApp("Сначала попробовать демо без регистрации")}</a> : null}
        <div className="login-sso">
          <button type="button" className="login-sso__btn login-sso__btn--primary" onClick={() => handleSSO('yandex')}>{translateApp("Продолжить с Яндексом")}</button>
          <button type="button" className="login-sso__btn" onClick={() => handleSSO('google')}>{translateApp("Продолжить с Google")}</button>
        </div>

        <details className="login-local">
          <summary>{translateApp("Вход по паролю для существующих аккаунтов")}</summary>
          <form className="login-form" onSubmit={handleSubmit}>
            <FormField label={translateApp("Электронная почта")} htmlFor="login-email">
              <input
                id="login-email"
                type="email"
                name="email"
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
              />
            </FormField>

            <FormField label={translateApp("Пароль")} htmlFor="login-password">
              <input
                id="login-password"
                type="password"
                name="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </FormField>

            {error ? <div className="login-error">{error}</div> : null}

            <Button type="submit" variant="primary" disabled={isLoading}>
              {isLoading ? translateApp("Входим...") : translateApp("Войти")}
            </Button>
          </form>
        </details>

        <p className="login-legal">
          {translateApp("Продолжая, вы принимаете")}{' '}
          <a href={getPublicPath('terms', getCurrentLocale())}>{translateApp("условия использования")}</a>
          {' '}{translateApp("и знакомитесь с")}{' '}
          <a href={getPublicPath('privacy', getCurrentLocale())}>{translateApp("политикой конфиденциальности")}</a>.
        </p>
      </div>
    </div>
  );
}

export default LoginPage;
