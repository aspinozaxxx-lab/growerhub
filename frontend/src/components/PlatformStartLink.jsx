import { Link } from 'react-router-dom';
import { getPlatformStartPath, getPublicPath } from '../domain/localizedRoutes';
import { SELF_SERVICE_PUBLIC_ENABLED } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import { trackProductGoal } from '../utils/analytics';
import { useAuth } from '../features/auth/AuthContext';

function PlatformStartLink({ placement, className = 'hero-cta', children, onClick }) {
  const locale = getCurrentLocale();
  const { status, user } = useAuth();
  if (status === 'idle' || status === 'loading') {
    return null;
  }
  if (status === 'authorized' && user?.onboarding_completed) {
    return null;
  }
  const continueSetup = status === 'authorized' && !user?.onboarding_completed;
  const target = continueSetup
    ? '/app/onboarding/'
    : (SELF_SERVICE_PUBLIC_ENABLED
        ? getPlatformStartPath(locale)
        : getPublicPath('gettingStarted', locale));
  const label = continueSetup
    ? translatePublic('Продолжить настройку')
    : (children || translatePublic(SELF_SERVICE_PUBLIC_ENABLED ? 'Начать бесплатно' : 'Как начать'));

  const handleClick = (event) => {
    trackProductGoal('platform_start', {
      placement,
      step: SELF_SERVICE_PUBLIC_ENABLED ? 'login' : 'early_access_waitlist',
    });
    onClick?.(event);
  };

  return (
    <Link className={className} to={target} onClick={handleClick}>
      {label}
    </Link>
  );
}

export default PlatformStartLink;
