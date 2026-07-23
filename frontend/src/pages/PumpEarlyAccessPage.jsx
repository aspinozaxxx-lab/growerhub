import LeadCta from '../components/LeadCta';
import TelegramContactLink from '../components/TelegramContactLink';
import { equipmentContent } from '../content/pages';
import { getPublicPath } from '../domain/localizedRoutes';
import { getCurrentLocale, translatePublic } from '../locales/i18n';
import useSeoMeta from '../utils/useSeoMeta';

function PumpEarlyAccessPage() {
  const locale = getCurrentLocale();
  const pump = equipmentContent.pump;
  useSeoMeta({
    title: `${pump.title} — GrowerHub`,
    description: pump.description,
    path: getPublicPath('equipmentPump', locale),
    locale,
  });

  return (
    <div className="section equipment-page">
      <div className="badge">{pump.status}</div>
      <h1>{pump.title}</h1>
      <p className="article-lead">{pump.summary}</p>

      <section className="content-section split-section">
        <div className="info-block"><h2>{translatePublic('Два варианта связи')}</h2><p>{translatePublic('Проектируем два режима: Zigbee для общей сети устройств и Wi‑Fi с прямым MQTT GrowerHub. Оба варианта работают с единым кабинетом платформы.')}</p></div>
        <div className="info-block"><h2>{translatePublic('Текущий этап')}</h2><p>{translatePublic('Прототип проходит испытания. Если хотите присоединиться к первым пользователям, напишите нам — обсудим оборудование и подходящий сценарий.')}</p></div>
      </section>

      <section className="content-section">
        <h2>{translatePublic('Что мы проверяем')}</h2>
        <ul className="check-list limitations-list">{pump.limitations.map((item) => <li key={item}>{item}</li>)}</ul>
      </section>

      <section className="lead-cta">
        <div><h2>{translatePublic('Стать одним из первых пользователей')}</h2><p>{translatePublic('Расскажите, где планируете использовать насос. Мы ответим на вопросы и подскажем, как подготовиться к первым испытаниям.')}</p></div>
        <TelegramContactLink placement="pump_early_access" className="hero-cta">{translatePublic('Написать в Telegram')}</TelegramContactLink>
      </section>
      <LeadCta placement="pump_platform_bottom" title={translatePublic('Начать с готового оборудования')} text={translatePublic('Для мониторинга GrowerHub насос не нужен: достаточно координатора и одного Zigbee-датчика.')} />
    </div>
  );
}

export default PumpEarlyAccessPage;
