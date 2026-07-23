import LeadCta from '../components/LeadCta';
import { aboutContent } from '../content/pages';
import { TELEGRAM_CHANNEL_URL } from '../domain/siteConfig';
import useSeoMeta from '../utils/useSeoMeta';

function AboutPage() {
  useSeoMeta({
    title: 'О проекте GrowerHub — контроль полива и микроклимата',
    description: aboutContent.intro,
    path: '/about/',
  });

  return (
    <div className="section">
      <h1>{aboutContent.title}</h1>
      <p>{aboutContent.intro}</p>
      <div className="info-grid content-section">
        <div className="info-block">
          <h2>Задача проекта</h2>
          <p>{aboutContent.mission}</p>
        </div>
        <div className="info-block">
          <h2>Чем помогаем</h2>
          <p>{aboutContent.value}</p>
        </div>
      </div>
      <section className="content-section">
        <h2>Контакты</h2>
        <ul className="contact-list">
          <li><strong>Сайт:</strong> <a href={aboutContent.contacts.site}>{aboutContent.contacts.site}</a></li>
          <li><strong>Telegram:</strong> <a href={TELEGRAM_CHANNEL_URL} target="_blank" rel="noreferrer">канал GrowerHub</a></li>
        </ul>
      </section>
      <LeadCta placement="about_bottom" />
    </div>
  );
}

export default AboutPage;
