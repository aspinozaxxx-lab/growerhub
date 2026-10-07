import { Sprout, Wifi } from 'lucide-react';
import { Link } from 'react-router-dom';
import { NurseryProductDetail } from '../components/nursery/NurseryComponents';
import { getPageContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { useShop } from '../features/shop';
import { getCurrentLocale } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function PumpEarlyAccessPage() {
  const locale = getCurrentLocale();
  const pump = getPageContent(locale).equipmentContent.pump;
  const { catalog, openConsultation } = useShop();
  const offers = ['pump', 'pump-drip-kit'].map((id) => catalog.offers.find((offer) => offer.id === id)).filter(Boolean);
  useSeoMeta({
    title: `${pump.title} — GrowerHub`, description: pump.description,
    path: getPublicPath('equipmentPump', locale), image: '/content/nursery/pump-drip-kit.webp', locale,
  });
  return <div className="nursery-page nursery-pump">
    <header className="nursery-page-heading"><p className="nursery-eyebrow"><Sprout aria-hidden="true" size={19} />{pump.status}</p><h1>{pump.title}</h1><p>{pump.summary}</p></header>
    {offers.length > 0 && <NurseryProductDetail offers={offers} locale={locale} currency={catalog.currency} />}
    <div className="nursery-info-grid">{pump.connections.map((connection) => <section className="nursery-info-panel" key={connection.title}><Wifi aria-hidden="true" size={28} /><h2>{connection.title}</h2><p>{connection.text}</p></section>)}</div>
    <section className="nursery-info-panel"><h2>{pump.setup_title}</h2><ul>{pump.setup.map((step) => <li key={step}>{step}</li>)}</ul></section>
    <div className="nursery-help-row"><div><h2>{locale === 'en' ? 'Let us choose together' : 'Давайте подберём вместе'}</h2><p>{locale === 'en' ? 'Tell us about your plants and we will help you choose a watering setup.' : 'Расскажите о своих растениях — поможем подобрать полив и способ подключения.'}</p></div><button type="button" className="nursery-button" onClick={openConsultation}>{locale === 'en' ? 'Help me choose' : 'Помогите выбрать'}</button></div>
    <Link className="nursery-text-link" to={getPublicPath('equipment', locale)}>{locale === 'en' ? 'All kits and devices' : 'Все комплекты и устройства'}</Link>
  </div>;
}

export default PumpEarlyAccessPage;
