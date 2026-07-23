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
        <div className="info-block"><h2>Два варианта связи</h2><p>Проектируем два режима: Zigbee для общей сети устройств и Wi‑Fi с прямым MQTT GrowerHub. Оба варианта работают с единым кабинетом платформы.</p></div>
        <div className="info-block"><h2>Текущий этап</h2><p>Прототип проходит испытания. Если хотите присоединиться к первым пользователям, напишите нам — обсудим оборудование и подходящий сценарий.</p></div>
      </section>

      <section className="content-section">
        <h2>Что мы проверяем</h2>
        <ul className="check-list limitations-list">{pump.limitations.map((item) => <li key={item}>{item}</li>)}</ul>
      </section>

      <section className="lead-cta">
        <div><h2>Стать одним из первых пользователей</h2><p>Расскажите, где планируете использовать насос. Мы ответим на вопросы и подскажем, как подготовиться к первым испытаниям.</p></div>
        <TelegramContactLink placement="pump_early_access" className="hero-cta">Написать в Telegram</TelegramContactLink>
      </section>
      <LeadCta placement="pump_platform_bottom" title="Начать с готового оборудования" text="Для мониторинга GrowerHub насос не нужен: достаточно координатора и одного Zigbee-датчика." />
    </div>
  );
}

export default PumpEarlyAccessPage;
