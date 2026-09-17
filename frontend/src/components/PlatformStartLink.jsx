import { Link } from 'react-router-dom';
import { getPlatformStartPath, getPublicPath } from '../domain/localizedRoutes';
import { SELF_SERVICE_PUBLIC_ENABLED } from '../domain/siteConfig';
import { getCurrentLocale, rememberLocale, translatePublic } from '../locales/i18n';
import { trackProductGoal } from '../utils/analytics';
import { useAuth } from '../features/auth/AuthContext';

function PlatformStartLink({ placement, className = 'hero-cta', children, onClick }) {
  const locale = getCurrentLocale();
  const { accountStatus: status, accountUser: user, demoActive, leaveDemo } = useAuth();
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
    if (SELF_SERVICE_PUBLIC_ENABLED || continueSetup) {
      rememberLocale(locale);
      if (demoActive) {
        trackProductGoal('demo_real_setup_start', { placement });
        leaveDemo();
      }
    }
    trackProductGoal('platform_start', {
      placement,
      step: SELF_SERVICE_PUBLIC_ENABLED ? 'login' : 'early_access_waitlist',
    });
    onClick?.(event);
  };

  return (
    <Link className={className} to={target} onClick={handleClick} data-platform-placement={placement}>
      {label}
    </Link>
  );
}

export default PlatformStartLink;
