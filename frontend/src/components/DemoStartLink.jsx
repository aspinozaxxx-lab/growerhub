import { Link } from 'react-router-dom';
import { DEMO_PUBLIC_ENABLED } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';

export default function DemoStartLink({ placement, className = 'hero-cta', onClick }) {
  if (!DEMO_PUBLIC_ENABLED) return null;
  return <Link className={className} to={`/app/demo/?lang=${getCurrentLocale()}`} state={{ demoPlacement: placement }} onClick={onClick}>{translatePublic('Попробовать демо без регистрации')}</Link>;
}
