import { Link } from 'react-router-dom';
import { PLATFORM_START_PATH, SELF_SERVICE_PUBLIC_ENABLED } from '../domain/siteConfig';
import { trackProductGoal } from '../utils/metrika';

function PlatformStartLink({ placement, className = 'hero-cta', children, onClick }) {
  const target = SELF_SERVICE_PUBLIC_ENABLED ? PLATFORM_START_PATH : '/kak-nachat/';
  const label = children || (SELF_SERVICE_PUBLIC_ENABLED ? 'Начать бесплатно' : 'Как начать');

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
