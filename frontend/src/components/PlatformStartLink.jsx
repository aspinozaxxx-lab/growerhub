import { Link } from 'react-router-dom';
import { getPlatformStartPath, getPublicPath } from '../domain/localizedRoutes';
import { SELF_SERVICE_PUBLIC_ENABLED } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import { trackProductGoal } from '../utils/analytics';

function PlatformStartLink({ placement, className = 'hero-cta', children, onClick }) {
  const locale = getCurrentLocale();
  const target = SELF_SERVICE_PUBLIC_ENABLED
    ? getPlatformStartPath(locale)
    : getPublicPath('gettingStarted', locale);
  const label = children || translatePublic(SELF_SERVICE_PUBLIC_ENABLED ? 'Начать бесплатно' : 'Как начать');

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
