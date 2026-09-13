import PlatformStartLink from './PlatformStartLink';
import DemoStartLink from './DemoStartLink';
import TelegramContactLink from './TelegramContactLink';
import { translatePublic } from '../locales/i18n';

function LeadCta({
  placement,
  title,
  text,
  compact = false,
}) {
  const localizedTitle = title || translatePublic('Начните с первого устройства');
  const localizedText = text || translatePublic('Войдите, подключите Zigbee2MQTT и соберите первую зону самостоятельно. GrowerHub доступен бесплатно и без карты.');

  return (
    <section className={compact ? 'lead-cta lead-cta--compact' : 'lead-cta'}>
      <div>
        <h2>{localizedTitle}</h2>
        <p>{localizedText}</p>
      </div>
      <div className="cta-row">
        <DemoStartLink placement={placement + '_demo'} />
        <PlatformStartLink placement={placement} />
        <TelegramContactLink placement={`${placement}_help`} className="secondary-link">
          {translatePublic('Поможем подключить первый датчик')}
        </TelegramContactLink>
      </div>
    </section>
  );
}

export default LeadCta;
