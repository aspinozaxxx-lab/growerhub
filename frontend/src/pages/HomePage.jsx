import { Sprout } from 'lucide-react';
import { Link } from 'react-router-dom';
import DemoStartLink from '../components/DemoStartLink';
import { NurseryDemoBanner, NurseryFootnote, NurseryHelpCard, NurseryKitCard, NurseryNote, NurseryServiceStrip } from '../components/nursery/NurseryComponents';
import { getNurseryCopy } from '../components/nursery/copy';
import { getPageContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { DEMO_PUBLIC_ENABLED, GITHUB_REPOSITORY_URL, ORGANIZATION_ID, SITE_NAME, SITE_URL, TELEGRAM_CHANNEL_URL } from '../domain/siteConfig';
import { useShop } from '../features/shop';
import { getCurrentLocale } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function HomePage() {
  const locale = getCurrentLocale();
  const { homeContent } = getPageContent(locale);
  const { catalog } = useShop();
  const { hero, kits, help, demo, faq } = homeContent;
  const path = getPublicPath('home', locale);
  const featured = ['light-mini', 'light-three'].map((id) => catalog.offers.find((offer) => offer.id === id)).filter(Boolean);
  const additions = ['soil-sensor', 'pump'].map((id) => catalog.offers.find((offer) => offer.id === id)).filter(Boolean);
  const additionsCopy = getNurseryCopy(locale).catalogue;

  useSeoMeta({
    title: homeContent.title,
    description: homeContent.description,
    path,
    image: hero.preview_image,
    locale,
    jsonLd: [{
      '@context': 'https://schema.org', '@type': 'Organization', '@id': ORGANIZATION_ID,
      name: SITE_NAME, url: SITE_URL, foundingDate: '2025-10-06',
      sameAs: [GITHUB_REPOSITORY_URL, TELEGRAM_CHANNEL_URL],
    }, {
      '@context': 'https://schema.org', '@type': 'FAQPage',
      mainEntity: faq.items.map((item) => ({ '@type': 'Question', name: item.question, acceptedAnswer: { '@type': 'Answer', text: item.answer } })),
    }],
  });

  return (
    <div className="nursery-page nursery-home">
      <section className="nursery-hero" aria-labelledby="nursery-hero-title">
        <div className="nursery-hero__copy">
          <p className="nursery-eyebrow"><Sprout aria-hidden="true" size={18} />{hero.badge}</p>
          <h1 id="nursery-hero-title">{hero.title}</h1>
          <p>{hero.subtitle}</p>
        </div>
        <div className="nursery-hero__photo"><img src={hero.preview_image} alt={hero.preview_alt} width="1600" height="914" fetchPriority="high" /></div>
        <div className="nursery-hero__actions">
          <div className="nursery-actions">
            <Link className="nursery-button" to={getPublicPath('equipment', locale)}>{hero.cta}</Link>
            <DemoStartLink className="nursery-button nursery-button--outline" placement="home_hero_demo">{hero.demo_cta}</DemoStartLink>
          </div>
          {DEMO_PUBLIC_ENABLED && <p className="nursery-small">{hero.reassurance}</p>}
        </div>
        <NurseryNote className="nursery-hero__note">{hero.note}</NurseryNote>
      </section>
      <NurseryServiceStrip items={homeContent.service} />

      <section aria-labelledby="nursery-kits-title">
        <div className="nursery-section-heading"><h2 id="nursery-kits-title">{kits.title}</h2><NurseryNote>{kits.note}</NurseryNote></div>
        <div className="nursery-home-kits">
          {featured.map((offer) => <NurseryKitCard key={offer.id} offer={offer} locale={locale} />)}
          <NurseryHelpCard content={help} />
        </div>
        <NurseryFootnote>{kits.lamps_note}</NurseryFootnote>
        <div className="nursery-kits-more"><Link className="nursery-text-link" to={getPublicPath('equipment', locale)}>{kits.all}</Link></div>
      </section>

      {additions.length > 0 && <section className="nursery-section" aria-labelledby="nursery-additions-title">
        <h2 id="nursery-additions-title">{additionsCopy.additionsTitle}</h2><p>{additionsCopy.additionsIntro}</p>
        <div className="nursery-home-additions">{additions.map((offer) => <NurseryKitCard key={offer.id} offer={offer} locale={locale} />)}</div>
      </section>}
      <NurseryDemoBanner content={demo} placement="home_example" />
      <NurseryFootnote connection>{homeContent.connection_note}</NurseryFootnote>

      <section className="nursery-faq" aria-labelledby="nursery-faq-title">
        <h2 id="nursery-faq-title">{faq.title}</h2>
        {faq.items.map((item) => <details key={item.question}><summary>{item.question}</summary><p>{item.answer}</p></details>)}
      </section>
    </div>
  );
}

export default HomePage;
