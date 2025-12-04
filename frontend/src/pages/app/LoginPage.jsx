import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
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
  } = useAuth();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

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
        <h1>Вход в GrowerHub</h1>
        <form className="login-form" onSubmit={handleSubmit}>
          <label className="login-label" htmlFor="login-email">Email</label>
          <input
            id="login-email"
            className="login-input"
            type="email"
            name="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />

          <label className="login-label" htmlFor="login-password">Пароль</label>
          <input
            id="login-password"
            className="login-input"
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />

          {error ? <div className="login-error">{error}</div> : null}

          <button type="submit" className="login-submit" disabled={isLoading}>
            {isLoading ? 'Входим...' : 'Войти'}
          </button>
        </form>

        <div className="login-divider">или продолжить через SSO</div>
        <div className="login-sso">
          <button type="button" className="login-sso__btn" onClick={() => handleSSO('google')}>
            Google
          </button>
          <button type="button" className="login-sso__btn" onClick={() => handleSSO('yandex')}>
            Yandex
          </button>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
