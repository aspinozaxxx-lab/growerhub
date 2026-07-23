import { TELEGRAM_DIRECT_URL } from '../domain/siteConfig';
import { trackTelegramContact } from '../utils/analytics';

function TelegramContactLink({ placement, className = '', children, onClick }) {
  const handleClick = (event) => {
    trackTelegramContact(placement);
    onClick?.(event);
  };

  return (
    <a
      className={className}
      href={TELEGRAM_DIRECT_URL}
      target="_blank"
      rel="noreferrer"
      onClick={handleClick}
    >
      {children}
    </a>
  );
}

export default TelegramContactLink;
