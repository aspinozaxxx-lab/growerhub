import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword, fetchAuthMethods, linkSsoMethod, setLocalLogin, unlinkAuthMethod } from '../../api/auth';
import { isSessionExpiredError } from '../../api/client';
import { useAuth } from '../../features/auth/AuthContext';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppGrid from '../../components/layout/AppGrid';
import FormField from '../../components/ui/FormField';
import Button from '../../components/ui/Button';
import Surface from '../../components/ui/Surface';
import { Title, Text } from '../../components/ui/Typography';
import './AppProfile.css';
import { translateApp } from '../../locales/i18n';

function AppProfile() {
  const { user, token, status, logout } = useAuth();
  const navigate = useNavigate();

  const [authMethods, setAuthMethods] = useState(null);
  const [loadingMethods, setLoadingMethods] = useState(false);
  const [methodsError, setMethodsError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');
  const [updatingLocal, setUpdatingLocal] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const [linkingProvider, setLinkingProvider] = useState(null);
  const [unlinkingProvider, setUnlinkingProvider] = useState(null);
  const [showSetLocalForm, setShowSetLocalForm] = useState(false);
  const [showChangePasswordForm, setShowChangePasswordForm] = useState(false);

  const [localEmail, setLocalEmail] = useState('');
  const [localPassword, setLocalPassword] = useState('');
  const [localPasswordConfirm, setLocalPasswordConfirm] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');

  useEffect(() => {
    setLocalEmail(user?.email || '');
  }, [user]);

  const handleLogout = useCallback(() => {
    logout();
    navigate('/app/login/', { replace: true });
  }, [logout, navigate]);

  /**
   * Translitem: zagruzka sostoyaniya sposobov vhoda.
   */
  const loadAuthMethods = useCallback(async () => {
    if (!token || status !== 'authorized') {
      return;
    }
    setLoadingMethods(true);
    setMethodsError('');
    try {
      const data = await fetchAuthMethods(token);
      setAuthMethods(data);
    } catch (error) {
      if (isSessionExpiredError(error)) return;
      setMethodsError(error?.message || translateApp("Не удалось загрузить способы входа"));
    } finally {
      setLoadingMethods(false);
    }
  }, [status, token]);

  useEffect(() => {
    loadAuthMethods();
  }, [loadAuthMethods]);

  const handleSetLocalLogin = useCallback(
    async (event) => {
      event?.preventDefault?.();
      setPasswordSuccess('');
      if (localPassword !== localPasswordConfirm) {
        setMethodsError(translateApp("Пароли не совпадают"));
        return;
      }
      setMethodsError('');
      setUpdatingLocal(true);
      try {
        const data = await setLocalLogin(localEmail.trim(), localPassword, token);
        setAuthMethods(data);
        setShowSetLocalForm(false);
        setLocalPassword('');
        setLocalPasswordConfirm('');
      } catch (error) {
        if (isSessionExpiredError(error)) return;
        setMethodsError(error?.message || translateApp("Не удалось сохранить локальный вход"));
      } finally {
        setUpdatingLocal(false);
      }
    },
    [localEmail, localPassword, localPasswordConfirm, token],
  );

  const handleChangePassword = useCallback(
    async (event) => {
      event?.preventDefault?.();
      setMethodsError('');
      setPasswordSuccess('');
      if (newPassword !== newPasswordConfirm) {
        setMethodsError(translateApp("Новый пароль и подтверждение не совпадают"));
        return;
      }
      setChangingPassword(true);
      try {
        await changePassword(currentPassword, newPassword, token);
        setPasswordSuccess(translateApp("Пароль успешно обновлён"));
        setCurrentPassword('');
        setNewPassword('');
        setNewPasswordConfirm('');
        setShowChangePasswordForm(false);
      } catch (error) {
        if (isSessionExpiredError(error)) return;
        setMethodsError(error?.message || translateApp("Не удалось сменить пароль"));
      } finally {
        setChangingPassword(false);
      }
    },
    [currentPassword, newPassword, newPasswordConfirm, token],
  );

  const handleUnlink = useCallback(
    async (provider) => {
      const confirmed = window.confirm(translateApp("Удалить этот способ входа?"));
      if (!confirmed) {
        return;
      }
      setMethodsError('');
      setPasswordSuccess('');
      setUnlinkingProvider(provider);
      try {
        const data = await unlinkAuthMethod(provider, token);
        setAuthMethods(data);
      } catch (error) {
        if (isSessionExpiredError(error)) return;
        setMethodsError(error?.message || translateApp("Не удалось удалить способ входа"));
      } finally {
        setUnlinkingProvider(null);
      }
    },
    [token],
  );

  const handleLink = useCallback(
    async (provider) => {
      setMethodsError('');
      setPasswordSuccess('');
      setLinkingProvider(provider);
      try {
        const redirectPath = '/app/profile/';
        const url = await linkSsoMethod(provider, redirectPath, token);
        window.location.href = url;
      } catch (error) {
        if (isSessionExpiredError(error)) return;
        setMethodsError(error?.message || translateApp("Не удалось начать привязку"));
      } finally {
        setLinkingProvider(null);
      }
    },
    [token],
  );

  const localMethods = authMethods?.local;
  const googleMethods = authMethods?.google;
  const yandexMethods = authMethods?.yandex;

  const formatProviderStatus = useCallback((providerData) => {
    if (!providerData?.linked) {
      return translateApp("Не привязан");
    }
    const subject = providerData.provider_subject || '';
    const shortSubject = subject ? ` (${subject.slice(0, 10)}${subject.length > 10 ? '…' : ''})` : '';
    return translateApp("Привязан{{value1}}", { value1: shortSubject });
  }, []);

  const isLoadingLink = useMemo(() => Boolean(linkingProvider), [linkingProvider]);

  if (!user) {
    return <div className="profile-card">{translateApp("Нет данных профиля")}</div>;
  }

  return (
    <div className="app-profile">
      <AppPageHeader
        title={translateApp("Профиль")}
        right={user && user.role === 'admin' ? (
          <Button
            type="button"
            variant="secondary"
            onClick={() => navigate('/app/admin/devices/')}
          >{translateApp("Администрирование")}</Button>
        ) : null}
      />
      <AppGrid min={320}>
        <Surface variant="card" padding="md" className="profile-card">
          <div className="profile-row">
            <span className="profile-label">ID</span>
            <span>{user.id}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">{translateApp("Электронная почта")}</span>
            <span>{user.email || '-'}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">{translateApp("Имя пользователя")}</span>
            <span>{user.username || '-'}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">{translateApp("Роль")}</span>
            <span>{user.role || '-'}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">{translateApp("Статус")}</span>
            <span>{user.is_active ? translateApp("Активен") : translateApp("Заблокирован")}</span>
          </div>
          <div className="profile-actions">
            <Button type="button" variant="secondary" onClick={handleLogout}>{translateApp("Выйти")}</Button>
          </div>
        </Surface>

        <Surface variant="card" padding="md" className="profile-card profile-auth-card">
          <div className="profile-auth-header">
            <Title level={3}>{translateApp("Способы входа")}</Title>
            {methodsError ? <Text tone="danger" className="profile-auth-error">{methodsError}</Text> : null}
            {passwordSuccess ? <Text className="profile-auth-success">{passwordSuccess}</Text> : null}
          </div>

          {loadingMethods ? (
            <div className="profile-auth-status">{translateApp("Загрузка способов входа...")}</div>
          ) : authMethods ? (
            <>
              <div className="profile-auth-row">
                <div>
                  <div className="profile-auth-title">{translateApp("Локальный логин")}</div>
                  <Text tone="muted" className="profile-auth-status">
                    {localMethods?.active
                      ? translateApp("Локальный вход активен ({{value1}})", { value1: localMethods.email || '-' })
                      : translateApp("Локальный вход не настроен")}
                  </Text>
                </div>
                <div className="profile-auth-actions">
                  {!localMethods?.active ? (
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => setShowSetLocalForm((prev) => !prev)}
                      disabled={updatingLocal || isLoadingLink || Boolean(unlinkingProvider)}
                    >{translateApp("Установить пароль")}</Button>
                  ) : (
                    <>
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => setShowChangePasswordForm((prev) => !prev)}
                        disabled={changingPassword || isLoadingLink || Boolean(unlinkingProvider)}
                      >{translateApp("Сменить пароль")}</Button>
                      {localMethods?.can_delete ? (
                        <Button
                          type="button"
                          variant="danger"
                          onClick={() => handleUnlink('local')}
                          disabled={unlinkingProvider === 'local'}
                        >
                          {unlinkingProvider === 'local' ? translateApp("Удаляем...") : translateApp("Удалить локальный вход")}
                        </Button>
                      ) : null}
                    </>
                  )}
                </div>
              </div>

              {!localMethods?.active && showSetLocalForm ? (
                <form className="profile-auth-form" onSubmit={handleSetLocalLogin}>
                  <FormField label={translateApp("Электронная почта для входа")} htmlFor="local-email">
                    <input
                      id="local-email"
                      type="email"
                      value={localEmail}
                      onChange={(event) => setLocalEmail(event.target.value)}
                      required
                      disabled={updatingLocal}
                    />
                  </FormField>
                  <FormField label={translateApp("Новый пароль")} htmlFor="local-password">
                    <input
                      id="local-password"
                      type="password"
                      value={localPassword}
                      onChange={(event) => setLocalPassword(event.target.value)}
                      required
                      disabled={updatingLocal}
                    />
                  </FormField>
                  <FormField label={translateApp("Повторите новый пароль")} htmlFor="local-password-confirm">
                    <input
                      id="local-password-confirm"
                      type="password"
                      value={localPasswordConfirm}
                      onChange={(event) => setLocalPasswordConfirm(event.target.value)}
                      required
                      disabled={updatingLocal}
                    />
                  </FormField>
                  <div className="profile-auth-actions">
                    <Button type="submit" variant="primary" disabled={updatingLocal}>
                      {updatingLocal ? translateApp("Сохраняем...") : translateApp("Сохранить")}
                    </Button>
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => setShowSetLocalForm(false)}
                      disabled={updatingLocal}
                    >{translateApp("Отмена")}</Button>
                  </div>
                </form>
              ) : null}

              {localMethods?.active && showChangePasswordForm ? (
                <form className="profile-auth-form" onSubmit={handleChangePassword}>
                  <FormField label={translateApp("Текущий пароль")} htmlFor="current-password">
                    <input
                      id="current-password"
                      type="password"
                      value={currentPassword}
                      onChange={(event) => setCurrentPassword(event.target.value)}
                      required
                      disabled={changingPassword}
                    />
                  </FormField>
                  <FormField label={translateApp("Новый пароль")} htmlFor="new-password">
                    <input
                      id="new-password"
                      type="password"
                      value={newPassword}
                      onChange={(event) => setNewPassword(event.target.value)}
                      required
                      disabled={changingPassword}
                    />
                  </FormField>
                  <FormField label={translateApp("Повторите новый пароль")} htmlFor="new-password-confirm">
                    <input
                      id="new-password-confirm"
                      type="password"
                      value={newPasswordConfirm}
                      onChange={(event) => setNewPasswordConfirm(event.target.value)}
                      required
                      disabled={changingPassword}
                    />
                  </FormField>
                  <div className="profile-auth-actions">
                    <Button type="submit" variant="primary" disabled={changingPassword}>
                      {changingPassword ? translateApp("Обновляем...") : translateApp("Сменить пароль")}
                    </Button>
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => setShowChangePasswordForm(false)}
                      disabled={changingPassword}
                    >{translateApp("Отмена")}</Button>
                  </div>
                </form>
              ) : null}

              <div className="profile-auth-row">
                <div>
                  <div className="profile-auth-title">Google</div>
                  <Text tone="muted" className="profile-auth-status">{formatProviderStatus(googleMethods)}</Text>
                </div>
                <div className="profile-auth-actions">
                  {googleMethods?.linked ? (
                    googleMethods?.can_delete ? (
                      <Button
                        type="button"
                        variant="danger"
                        onClick={() => handleUnlink('google')}
                        disabled={unlinkingProvider === 'google'}
                        >
                          {unlinkingProvider === 'google' ? translateApp("Отвязываем...") : translateApp("Отвязать")}
                        </Button>
                      ) : null
                    ) : (
                      <Button
                        type="button"
                        variant="primary"
                        onClick={() => handleLink('google')}
                        disabled={isLoadingLink || loadingMethods}
                      >
                        {linkingProvider === 'google' ? translateApp("Открываем...") : translateApp("Привязать Google")}
                      </Button>
                    )}
                </div>
              </div>

              <div className="profile-auth-row">
                <div>
                  <div className="profile-auth-title">{translateApp("Яндекс")}</div>
                  <Text tone="muted" className="profile-auth-status">{formatProviderStatus(yandexMethods)}</Text>
                </div>
                <div className="profile-auth-actions">
                  {yandexMethods?.linked ? (
                    yandexMethods?.can_delete ? (
                      <Button
                        type="button"
                        variant="danger"
                        onClick={() => handleUnlink('yandex')}
                        disabled={unlinkingProvider === 'yandex'}
                        >
                          {unlinkingProvider === 'yandex' ? translateApp("Отвязываем...") : translateApp("Отвязать")}
                        </Button>
                      ) : null
                    ) : (
                      <Button
                        type="button"
                        variant="primary"
                        onClick={() => handleLink('yandex')}
                        disabled={isLoadingLink || loadingMethods}
                      >
                        {linkingProvider === 'yandex' ? translateApp("Открываем...") : translateApp("Привязать Яндекс")}
                      </Button>
                    )}
                </div>
              </div>
            </>
          ) : (
            <Text tone="muted" className="profile-auth-status">{translateApp("Нет данных о способах входа")}</Text>
          )}
        </Surface>
      </AppGrid>
    </div>
  );
}

export default AppProfile;
