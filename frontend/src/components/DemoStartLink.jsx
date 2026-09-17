import { Link } from 'react-router-dom';
import { DEMO_PUBLIC_ENABLED } from '../domain/siteConfig';
import { getCurrentLocale, translatePublic } from '../locales/i18n';

export default function DemoStartLink({ placement, className = 'hero-cta', onClick, view, children }) {
  if (!DEMO_PUBLIC_ENABLED) return null;
  const params = new URLSearchParams({ lang: getCurrentLocale() });
  if (view) params.set('view', view);
  return <Link className={className} to={`/app/demo/?${params}`} state={{ demoPlacement: placement }} onClick={onClick}>{children || translatePublic('Попробовать демо без регистрации')}</Link>;
}
