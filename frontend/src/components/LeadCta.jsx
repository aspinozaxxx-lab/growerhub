import PlatformStartLink from './PlatformStartLink';
import TelegramContactLink from './TelegramContactLink';

function LeadCta({
  placement,
  title = 'Начните с первого устройства',
  text = 'Войдите, подключите Zigbee2MQTT и соберите первую зону самостоятельно. Бесплатно в открытой бете, без карты.',
  compact = false,
}) {
  return (
    <section className={compact ? 'lead-cta lead-cta--compact' : 'lead-cta'}>
      <div>
        <h2>{title}</h2>
        <p>{text}</p>
      </div>
      <div className="cta-row">
        <PlatformStartLink placement={placement} />
        <TelegramContactLink placement={`${placement}_help`} className="secondary-link">
          Помощь в Telegram
        </TelegramContactLink>
      </div>
    </section>
  );
}

export default LeadCta;
