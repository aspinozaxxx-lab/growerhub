import { aboutContent } from '../content/pages';

function AboutPage() {
  return (
    <div className="section">
      <h1>{aboutContent.title}</h1>
      <p>{aboutContent.intro}</p>
      <div className="info-grid" style={{ marginTop: 18 }}>
        <div className="info-block">
          <h3>Миссия</h3>
          <p>{aboutContent.mission}</p>
        </div>
        <div className="info-block">
          <h3>Чем помогаем</h3>
          <p>{aboutContent.value}</p>
        </div>
      </div>
      <div className="section" style={{ marginTop: 18 }}>
        <h3>Контакты</h3>
        <ul className="contact-list">
          <li><strong>Email:</strong> <a href={`mailto:${aboutContent.contacts.email}`}>{aboutContent.contacts.email}</a></li>
          <li><strong>Сайт:</strong> <a href={aboutContent.contacts.site}>{aboutContent.contacts.site}</a></li>
          <li><strong>Телеграм:</strong> <a href={aboutContent.contacts.telegram}>{aboutContent.contacts.telegram}</a></li>
        </ul>
      </div>
    </div>
  );
}

export default AboutPage;
