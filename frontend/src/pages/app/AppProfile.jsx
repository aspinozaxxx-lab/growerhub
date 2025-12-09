import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword, fetchAuthMethods, linkSsoMethod, setLocalLogin, unlinkAuthMethod } from '../../api/auth';
import { useAuth } from '../../features/auth/AuthContext';
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
      setMethodsError(error?.message || 'РќРµ СѓРґР°Р»РѕСЃСЊ Р·Р°РіСЂСѓР·РёС‚СЊ СЃРїРѕСЃРѕР±С‹ РІС…РѕРґР°');
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
        setMethodsError('РџР°СЂРѕР»Рё РЅРµ СЃРѕРІРїР°РґР°СЋС‚');
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
        setMethodsError(error?.message || 'РќРµ СѓРґР°Р»РѕСЃСЊ СЃРѕС…СЂР°РЅРёС‚СЊ Р»РѕРєР°Р»СЊРЅС‹Р№ РІС…РѕРґ');
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
        setMethodsError('РќРѕРІС‹Р№ РїР°СЂРѕР»СЊ Рё РїРѕРґС‚РІРµСЂР¶РґРµРЅРёРµ РЅРµ СЃРѕРІРїР°РґР°СЋС‚');
        return;
      }
      setChangingPassword(true);
      try {
        await changePassword(currentPassword, newPassword, token);
        setPasswordSuccess('РџР°СЂРѕР»СЊ СѓСЃРїРµС€РЅРѕ РѕР±РЅРѕРІР»С‘РЅ');
        setCurrentPassword('');
        setNewPassword('');
        setNewPasswordConfirm('');
        setShowChangePasswordForm(false);
      } catch (error) {
        setMethodsError(error?.message || 'РќРµ СѓРґР°Р»РѕСЃСЊ СЃРјРµРЅРёС‚СЊ РїР°СЂРѕР»СЊ');
      } finally {
        setChangingPassword(false);
      }
    },
    [currentPassword, newPassword, newPasswordConfirm, token],
  );

  const handleUnlink = useCallback(
    async (provider) => {
      const confirmed = window.confirm('РЈРґР°Р»РёС‚СЊ СЌС‚РѕС‚ СЃРїРѕСЃРѕР± РІС…РѕРґР°?');
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
        setMethodsError(error?.message || 'РќРµ СѓРґР°Р»РѕСЃСЊ СѓРґР°Р»РёС‚СЊ СЃРїРѕСЃРѕР± РІС…РѕРґР°');
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
        setMethodsError(error?.message || 'РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°С‡Р°С‚СЊ РїСЂРёРІСЏР·РєСѓ');
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
      return 'РќРµ РїСЂРёРІСЏР·Р°РЅ';
    }
    const subject = providerData.provider_subject || '';
    const shortSubject = subject ? ` (${subject.slice(0, 10)}${subject.length > 10 ? 'вЂ¦' : ''})` : '';
    return `РџСЂРёРІСЏР·Р°РЅ${shortSubject}`;
  }, []);

  const isLoadingLink = useMemo(() => Boolean(linkingProvider), [linkingProvider]);

  if (!user) {
    return <div className="profile-card">РќРµС‚ РґР°РЅРЅС‹С… РїСЂРѕС„РёР»СЏ</div>;
  }

  return (
    <div className="app-profile">
      <h2>РџСЂРѕС„РёР»СЊ</h2>
      <div className="profile-card">
        <div className="profile-row">
          <span className="profile-label">ID</span>
          <span>{user.id}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">Email</span>
          <span>{user.email || 'вЂ”'}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">РРјСЏ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ</span>
          <span>{user.username || 'вЂ”'}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">Р РѕР»СЊ</span>
          <span>{user.role || 'вЂ”'}</span>
        </div>
        <div className="profile-row">
          <span className="profile-label">РЎС‚Р°С‚СѓСЃ</span>
          <span>{user.is_active ? 'РђРєС‚РёРІРµРЅ' : 'Р—Р°Р±Р»РѕРєРёСЂРѕРІР°РЅ'}</span>
        </div>
        <div className="profile-actions">
          <button type="button" className="logout-button" onClick={handleLogout}>
            Р’С‹Р№С‚Рё
          </button>
        </div>
      </div>

      <div className="profile-card profile-auth-card">
        <div className="profile-auth-header">
          <h3>РЎРїРѕСЃРѕР±С‹ РІС…РѕРґР°</h3>
          {methodsError ? <div className="profile-auth-error">{methodsError}</div> : null}
          {passwordSuccess ? <div className="profile-auth-success">{passwordSuccess}</div> : null}
        </div>

        {loadingMethods ? (
          <div className="profile-auth-status">Р—Р°РіСЂСѓР·РєР° СЃРїРѕСЃРѕР±РѕРІ РІС…РѕРґР°...</div>
        ) : authMethods ? (
          <>
            <div className="profile-auth-row">
              <div>
                <div className="profile-auth-title">Р›РѕРєР°Р»СЊРЅС‹Р№ Р»РѕРіРёРЅ</div>
                <div className="profile-auth-status">
                  {localMethods?.active
                    ? `Р›РѕРєР°Р»СЊРЅС‹Р№ РІС…РѕРґ Р°РєС‚РёРІРµРЅ (${localMethods.email || 'вЂ”'})`
                    : 'Р›РѕРєР°Р»СЊРЅС‹Р№ РІС…РѕРґ РЅРµ РЅР°СЃС‚СЂРѕРµРЅ'}
                </div>
              </div>
              <div className="profile-auth-actions">
                {!localMethods?.active ? (
                  <button
                    type="button"
                    className="profile-auth-button"
                    onClick={() => setShowSetLocalForm((prev) => !prev)}
                    disabled={updatingLocal || isLoadingLink || Boolean(unlinkingProvider)}
                  >
                    РЈСЃС‚Р°РЅРѕРІРёС‚СЊ РїР°СЂРѕР»СЊ
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      className="profile-auth-button"
                      onClick={() => setShowChangePasswordForm((prev) => !prev)}
                      disabled={changingPassword || isLoadingLink || Boolean(unlinkingProvider)}
                    >
                      РЎРјРµРЅРёС‚СЊ РїР°СЂРѕР»СЊ
                    </button>
                    {localMethods?.can_delete ? (
                      <button
                        type="button"
                        className="profile-auth-button danger"
                        onClick={() => handleUnlink('local')}
                        disabled={unlinkingProvider === 'local'}
                      >
                        {unlinkingProvider === 'local' ? 'РЈРґР°Р»СЏРµРј...' : 'РЈРґР°Р»РёС‚СЊ Р»РѕРєР°Р»СЊРЅС‹Р№ РІС…РѕРґ'}
                      </button>
                    ) : null}
                  </>
                )}
              </div>
            </div>

            {!localMethods?.active && showSetLocalForm ? (
              <form className="profile-auth-form" onSubmit={handleSetLocalLogin}>
                <label htmlFor="local-email">Email РґР»СЏ РІС…РѕРґР°</label>
                <input
                  id="local-email"
                  type="email"
                  value={localEmail}
                  onChange={(event) => setLocalEmail(event.target.value)}
                  required
                  disabled={updatingLocal}
                />
                <label htmlFor="local-password">РќРѕРІС‹Р№ РїР°СЂРѕР»СЊ</label>
                <input
                  id="local-password"
                  type="password"
                  value={localPassword}
                  onChange={(event) => setLocalPassword(event.target.value)}
                  required
                  disabled={updatingLocal}
                />
                <label htmlFor="local-password-confirm">РџРѕРІС‚РѕСЂРёС‚Рµ РЅРѕРІС‹Р№ РїР°СЂРѕР»СЊ</label>
                <input
                  id="local-password-confirm"
                  type="password"
                  value={localPasswordConfirm}
                  onChange={(event) => setLocalPasswordConfirm(event.target.value)}
                  required
                  disabled={updatingLocal}
                />
                <div className="profile-auth-actions">
                  <button type="submit" className="profile-auth-button" disabled={updatingLocal}>
                    {updatingLocal ? 'РЎРѕС…СЂР°РЅСЏРµРј...' : 'РЎРѕС…СЂР°РЅРёС‚СЊ'}
                  </button>
                  <button
                    type="button"
                    className="profile-auth-button secondary"
                    onClick={() => setShowSetLocalForm(false)}
                    disabled={updatingLocal}
                  >
                    РћС‚РјРµРЅР°
                  </button>
                </div>
              </form>
            ) : null}

            {localMethods?.active && showChangePasswordForm ? (
              <form className="profile-auth-form" onSubmit={handleChangePassword}>
                <label htmlFor="current-password">РўРµРєСѓС‰РёР№ РїР°СЂРѕР»СЊ</label>
                <input
                  id="current-password"
                  type="password"
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  required
                  disabled={changingPassword}
                />
                <label htmlFor="new-password">РќРѕРІС‹Р№ РїР°СЂРѕР»СЊ</label>
                <input
                  id="new-password"
                  type="password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  required
                  disabled={changingPassword}
                />
                <label htmlFor="new-password-confirm">РџРѕРІС‚РѕСЂРёС‚Рµ РЅРѕРІС‹Р№ РїР°СЂРѕР»СЊ</label>
                <input
                  id="new-password-confirm"
                  type="password"
                  value={newPasswordConfirm}
                  onChange={(event) => setNewPasswordConfirm(event.target.value)}
                  required
                  disabled={changingPassword}
                />
                <div className="profile-auth-actions">
                  <button type="submit" className="profile-auth-button" disabled={changingPassword}>
                    {changingPassword ? 'РћР±РЅРѕРІР»СЏРµРј...' : 'РЎРјРµРЅРёС‚СЊ РїР°СЂРѕР»СЊ'}
                  </button>
                  <button
                    type="button"
                    className="profile-auth-button secondary"
                    onClick={() => setShowChangePasswordForm(false)}
                    disabled={changingPassword}
                  >
                    РћС‚РјРµРЅР°
                  </button>
                </div>
              </form>
            ) : null}

            <div className="profile-auth-row">
              <div>
                <div className="profile-auth-title">Google</div>
                <div className="profile-auth-status">{formatProviderStatus(googleMethods)}</div>
              </div>
              <div className="profile-auth-actions">
                {googleMethods?.linked ? (
                  googleMethods?.can_delete ? (
                    <button
                      type="button"
                      className="profile-auth-button danger"
                      onClick={() => handleUnlink('google')}
                      disabled={unlinkingProvider === 'google'}
                    >
                      {unlinkingProvider === 'google' ? 'РћС‚РІСЏР·С‹РІР°РµРј...' : 'РћС‚РІСЏР·Р°С‚СЊ'}
                    </button>
                  ) : null
                ) : (
                  <button
                    type="button"
                    className="profile-auth-button"
                    onClick={() => handleLink('google')}
                    disabled={isLoadingLink || loadingMethods}
                  >
                    {linkingProvider === 'google' ? 'РћС‚РєСЂС‹РІР°РµРј...' : 'РџСЂРёРІСЏР·Р°С‚СЊ Google'}
                  </button>
                )}
              </div>
            </div>

            <div className="profile-auth-row">
              <div>
                <div className="profile-auth-title">РЇРЅРґРµРєСЃ</div>
                <div className="profile-auth-status">{formatProviderStatus(yandexMethods)}</div>
              </div>
              <div className="profile-auth-actions">
                {yandexMethods?.linked ? (
                  yandexMethods?.can_delete ? (
                    <button
                      type="button"
                      className="profile-auth-button danger"
                      onClick={() => handleUnlink('yandex')}
                      disabled={unlinkingProvider === 'yandex'}
                    >
                      {unlinkingProvider === 'yandex' ? 'РћС‚РІСЏР·С‹РІР°РµРј...' : 'РћС‚РІСЏР·Р°С‚СЊ'}
                    </button>
                  ) : null
                ) : (
                  <button
                    type="button"
                    className="profile-auth-button"
                    onClick={() => handleLink('yandex')}
                    disabled={isLoadingLink || loadingMethods}
                  >
                    {linkingProvider === 'yandex' ? 'РћС‚РєСЂС‹РІР°РµРј...' : 'РџСЂРёРІСЏР·Р°С‚СЊ РЇРЅРґРµРєСЃ'}
                  </button>
                )}
              </div>
            </div>
          </>
        ) : (
          <div className="profile-auth-status">РќРµС‚ РґР°РЅРЅС‹С… Рѕ СЃРїРѕСЃРѕР±Р°С… РІС…РѕРґР°</div>
        )}
      </div>
    </div>
  );
}

export default AppProfile;

