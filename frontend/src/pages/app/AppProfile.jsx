import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword, fetchAuthMethods, linkSsoMethod, setLocalLogin, unlinkAuthMethod } from '../../api/auth';
import { useAuth } from '../../features/auth/AuthContext';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppGrid from '../../components/layout/AppGrid';
import FormField from '../../components/ui/FormField';
import Button from '../../components/ui/Button';
import Surface from '../../components/ui/Surface';
import { Title, Text } from '../../components/ui/Typography';
import './AppProfile.css';

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
    navigate('/app/login', { replace: true });
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
      setMethodsError(error?.message || 'Не удалось загрузить способы входа');
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
        setMethodsError('Пароли не совпадают');
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
        setMethodsError(error?.message || 'Не удалось сохранить локальный вход');
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
        setMethodsError('Новый пароль и подтверждение не совпадают');
        return;
      }
      setChangingPassword(true);
      try {
        await changePassword(currentPassword, newPassword, token);
        setPasswordSuccess('Пароль успешно обновлён');
        setCurrentPassword('');
        setNewPassword('');
        setNewPasswordConfirm('');
        setShowChangePasswordForm(false);
      } catch (error) {
        setMethodsError(error?.message || 'Не удалось сменить пароль');
      } finally {
        setChangingPassword(false);
      }
    },
    [currentPassword, newPassword, newPasswordConfirm, token],
  );

  const handleUnlink = useCallback(
    async (provider) => {
      const confirmed = window.confirm('Удалить этот способ входа?');
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
        setMethodsError(error?.message || 'Не удалось удалить способ входа');
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
        const redirectPath = '/app/profile';
        const url = await linkSsoMethod(provider, redirectPath, token);
        window.location.href = url;
      } catch (error) {
        setMethodsError(error?.message || 'Не удалось начать привязку');
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
      return 'Не привязан';
    }
    const subject = providerData.provider_subject || '';
    const shortSubject = subject ? ` (${subject.slice(0, 10)}${subject.length > 10 ? '…' : ''})` : '';
    return `Привязан${shortSubject}`;
  }, []);

  const isLoadingLink = useMemo(() => Boolean(linkingProvider), [linkingProvider]);

  if (!user) {
    return <div className="profile-card">Нет данных профиля</div>;
  }

  return (
    <div className="app-profile">
      <AppPageHeader title="Профиль" />
      <AppGrid min={320}>
        <Surface variant="card" padding="md" className="profile-card">
          <div className="profile-row">
            <span className="profile-label">ID</span>
            <span>{user.id}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">Email</span>
            <span>{user.email || '-'}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">Имя пользователя</span>
            <span>{user.username || '-'}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">Роль</span>
            <span>{user.role || '-'}</span>
          </div>
          <div className="profile-row">
            <span className="profile-label">Статус</span>
            <span>{user.is_active ? 'Активен' : 'Заблокирован'}</span>
          </div>
          <div className="profile-actions">
            <Button type="button" variant="secondary" onClick={handleLogout}>
              Выйти
            </Button>
          </div>
        </Surface>

        <Surface variant="card" padding="md" className="profile-card profile-auth-card">
          <div className="profile-auth-header">
            <Title level={3}>Способы входа</Title>
            {methodsError ? <Text tone="danger" className="profile-auth-error">{methodsError}</Text> : null}
            {passwordSuccess ? <Text className="profile-auth-success">{passwordSuccess}</Text> : null}
          </div>

          {loadingMethods ? (
            <div className="profile-auth-status">Загрузка способов входа...</div>
          ) : authMethods ? (
            <>
              <div className="profile-auth-row">
                <div>
                  <div className="profile-auth-title">Локальный логин</div>
                  <Text tone="muted" className="profile-auth-status">
                    {localMethods?.active
                      ? `Локальный вход активен (${localMethods.email || '-'})`
                      : 'Локальный вход не настроен'}
                  </Text>
                </div>
                <div className="profile-auth-actions">
                  {!localMethods?.active ? (
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => setShowSetLocalForm((prev) => !prev)}
                      disabled={updatingLocal || isLoadingLink || Boolean(unlinkingProvider)}
                    >
                      Установить пароль
                    </Button>
                  ) : (
                    <>
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => setShowChangePasswordForm((prev) => !prev)}
                        disabled={changingPassword || isLoadingLink || Boolean(unlinkingProvider)}
                      >
                        Сменить пароль
                      </Button>
                      {localMethods?.can_delete ? (
                        <Button
                          type="button"
                          variant="danger"
                          onClick={() => handleUnlink('local')}
                          disabled={unlinkingProvider === 'local'}
                        >
                          {unlinkingProvider === 'local' ? 'Удаляем...' : 'Удалить локальный вход'}
                        </Button>
                      ) : null}
                    </>
                  )}
                </div>
              </div>

              {!localMethods?.active && showSetLocalForm ? (
                <form className="profile-auth-form" onSubmit={handleSetLocalLogin}>
                  <FormField label="Email для входа" htmlFor="local-email">
                    <input
                      id="local-email"
                      type="email"
                      value={localEmail}
                      onChange={(event) => setLocalEmail(event.target.value)}
                      required
                      disabled={updatingLocal}
                    />
                  </FormField>
                  <FormField label="Новый пароль" htmlFor="local-password">
                    <input
                      id="local-password"
                      type="password"
                      value={localPassword}
                      onChange={(event) => setLocalPassword(event.target.value)}
                      required
                      disabled={updatingLocal}
                    />
                  </FormField>
                  <FormField label="Повторите новый пароль" htmlFor="local-password-confirm">
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
                      {updatingLocal ? 'Сохраняем...' : 'Сохранить'}
                    </Button>
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => setShowSetLocalForm(false)}
                      disabled={updatingLocal}
                    >
                      Отмена
                    </Button>
                  </div>
                </form>
              ) : null}

              {localMethods?.active && showChangePasswordForm ? (
                <form className="profile-auth-form" onSubmit={handleChangePassword}>
                  <FormField label="Текущий пароль" htmlFor="current-password">
                    <input
                      id="current-password"
                      type="password"
                      value={currentPassword}
                      onChange={(event) => setCurrentPassword(event.target.value)}
                      required
                      disabled={changingPassword}
                    />
                  </FormField>
                  <FormField label="Новый пароль" htmlFor="new-password">
                    <input
                      id="new-password"
                      type="password"
                      value={newPassword}
                      onChange={(event) => setNewPassword(event.target.value)}
                      required
                      disabled={changingPassword}
                    />
                  </FormField>
                  <FormField label="Повторите новый пароль" htmlFor="new-password-confirm">
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
                      {changingPassword ? 'Обновляем...' : 'Сменить пароль'}
                    </Button>
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => setShowChangePasswordForm(false)}
                      disabled={changingPassword}
                    >
                      Отмена
                    </Button>
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
                          {unlinkingProvider === 'google' ? 'Отвязываем...' : 'Отвязать'}
                        </Button>
                      ) : null
                    ) : (
                      <Button
                        type="button"
                        variant="primary"
                        onClick={() => handleLink('google')}
                        disabled={isLoadingLink || loadingMethods}
                      >
                        {linkingProvider === 'google' ? 'Открываем...' : 'Привязать Google'}
                      </Button>
                    )}
                </div>
              </div>

              <div className="profile-auth-row">
                <div>
                  <div className="profile-auth-title">Яндекс</div>
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
                          {unlinkingProvider === 'yandex' ? 'Отвязываем...' : 'Отвязать'}
                        </Button>
                      ) : null
                    ) : (
                      <Button
                        type="button"
                        variant="primary"
                        onClick={() => handleLink('yandex')}
                        disabled={isLoadingLink || loadingMethods}
                      >
                        {linkingProvider === 'yandex' ? 'Открываем...' : 'Привязать Яндекс'}
                      </Button>
                    )}
                </div>
              </div>
            </>
          ) : (
            <Text tone="muted" className="profile-auth-status">Нет данных о способах входа</Text>
          )}
        </Surface>
      </AppGrid>
    </div>
  );
}

export default AppProfile;
