import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import FormField from '../../components/ui/FormField';
import Button from '../../components/ui/Button';
import './LoginPage.css';

function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    status,
    error,
    loginWithPassword,
    clearError,
    consumeRedirectAfterLogin,
    setRedirectAfterLogin,
    redirectAfterLogin,
  } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const queryRedirect = params.get('redirect');
    const stateRedirect = location.state?.from?.pathname;
    if (status === 'authorized' && location.pathname === '/app/login/' && !redirectAfterLogin && !queryRedirect && !stateRedirect) {
      // Translitem: perebrosyvaem uzhe avtorizovannogo s login na glavnyj /app
      navigate('/app/', { replace: true });
    }
  }, [status, location.pathname, location.search, location.state, redirectAfterLogin, navigate]);

  useEffect(() => {
    clearError();
    // pick redirect from query (?redirect=...) or location.state.from.pathname
    const params = new URLSearchParams(location.search);
    const queryRedirect = params.get('redirect');
    const stateRedirect = location.state?.from?.pathname;
    const candidate = queryRedirect || stateRedirect;
    if (candidate && candidate.startsWith('/app')) {
      setRedirectAfterLogin(candidate);
    }

    if (status === 'authorized') {
      const target = consumeRedirectAfterLogin();
      navigate(target, { replace: true });
    }
  }, [status, consumeRedirectAfterLogin, navigate, location.search, location.state, setRedirectAfterLogin, clearError]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    clearError();
    const result = await loginWithPassword(email.trim(), password);
    if (result?.success) {
      const target = consumeRedirectAfterLogin();
      navigate(target, { replace: true });
    }
  };

  const handleSSO = (provider) => {
    const target = consumeRedirectAfterLogin();
    window.location.href = `/api/auth/sso/${provider}/login?redirect_path=${encodeURIComponent(target)}`;
  };

  const isLoading = status === 'loading';

  return (
    <div className="login-page">
      <div className="login-card">
        <h1>Начать работу с GrowerHub</h1>
        <p className="login-intro">GrowerHub доступен бесплатно и без карты. После входа сразу перейдём к подключению Zigbee2MQTT.</p>
        <div className="login-sso">
          <button type="button" className="login-sso__btn login-sso__btn--primary" onClick={() => handleSSO('yandex')}>
            Продолжить с Яндексом
          </button>
          <button type="button" className="login-sso__btn" onClick={() => handleSSO('google')}>
            Продолжить с Google
          </button>
        </div>

        <details className="login-local">
          <summary>Вход по паролю для существующих аккаунтов</summary>
          <form className="login-form" onSubmit={handleSubmit}>
            <FormField label="Электронная почта" htmlFor="login-email">
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

            <FormField label="Пароль" htmlFor="login-password">
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
              {isLoading ? 'Входим...' : 'Войти'}
            </Button>
          </form>
        </details>

        <p className="login-legal">Продолжая, вы принимаете <a href="/terms/">условия использования</a> и знакомитесь с <a href="/privacy/">политикой конфиденциальности</a>.</p>
      </div>
    </div>
  );
}

export default LoginPage;
