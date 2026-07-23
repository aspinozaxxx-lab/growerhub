import LeadCta from '../components/LeadCta';
import TelegramContactLink from '../components/TelegramContactLink';
import { equipmentContent } from '../content/pages';
import useSeoMeta from '../utils/useSeoMeta';

function PumpEarlyAccessPage() {
  const pump = equipmentContent.pump;
  useSeoMeta({
    title: `${pump.title} — GrowerHub`,
    description: pump.description,
    path: '/oborudovanie/nasos-dlya-poliva/',
  });

  return (
    <div className="section equipment-page">
      <div className="badge">{pump.status}</div>
      <h1>{pump.title}</h1>
      <p className="article-lead">{pump.summary}</p>

      <section className="content-section split-section">
        <div className="info-block"><h2>Два варианта связи</h2><p>Zigbee для общей сети устройств или Wi‑Fi с прямым MQTT GrowerHub. Публичная доступность обоих режимов зависит от завершения TLS и испытаний безопасности.</p></div>
        <div className="info-block"><h2>Что это значит сейчас</h2><p>Нет обещания продажи, цены или даты готовности. Мы приглашаем только к раннему тестированию и отдельно согласуем безопасную схему.</p></div>
      </section>

      <section className="content-section">
        <h2>Обязательные ограничения</h2>
        <ul className="check-list limitations-list">{pump.limitations.map((item) => <li key={item}>{item}</li>)}</ul>
      </section>

      <section className="lead-cta">
        <div><h2>Стать тестировщиком</h2><p>Напишите только если готовы тестировать прототип и соблюдать ограничения по воде, питанию и аварийному отключению.</p></div>
        <TelegramContactLink placement="pump_early_access" className="hero-cta">Написать в Telegram</TelegramContactLink>
      </section>
      <LeadCta placement="pump_platform_bottom" title="Начать с готового оборудования" text="Для мониторинга GrowerHub насос не нужен: достаточно координатора и одного Zigbee-датчика." />
    </div>
  );
}

export default PumpEarlyAccessPage;
