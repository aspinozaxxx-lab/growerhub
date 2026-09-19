import PlatformStartLink from './PlatformStartLink';
import DemoStartLink from './DemoStartLink';
import TelegramContactLink from './TelegramContactLink';
import { translatePublic } from '../locales/i18n';
import { DEMO_PUBLIC_ENABLED, TELEGRAM_CHANNEL_URL } from '../domain/siteConfig';
import { trackProductGoal } from '../utils/analytics';

function LeadCta({
  placement,
  title,
  text,
  compact = false,
  demoView,
  showChannel = false,
}) {
  const localizedTitle = title || translatePublic('Начните с первого устройства');
  const localizedText = text || translatePublic('Войдите, подключите Zigbee2MQTT и соберите первую зону самостоятельно. GrowerHub доступен бесплатно и без карты.');

  return (
    <section className={compact ? 'lead-cta lead-cta--compact' : 'lead-cta'}>
      <div>
        <h2>{localizedTitle}</h2>
        <p>{localizedText}</p>
        {showChannel && (
          <p>
            {translatePublic('В Telegram — короткие видео демофермы, заметки и печатный журнал полива.')} {' '}
            <a
              href={TELEGRAM_CHANNEL_URL}
              target="_blank"
              rel="noreferrer"
              onClick={() => trackProductGoal('telegram_channel_open', { placement: `${placement}_channel` })}
            >
              {translatePublic('Читать канал GrowerHub')}
            </a>
          </p>
        )}
      </div>
      <div className="cta-row">
        <DemoStartLink placement={placement + '_demo'} view={demoView} />
        <PlatformStartLink placement={placement} className={DEMO_PUBLIC_ENABLED ? 'secondary-link' : 'hero-cta'} />
        <TelegramContactLink placement={`${placement}_help`} className="secondary-link">
          {translatePublic('Поможем подключить первый датчик')}
        </TelegramContactLink>
      </div>
    </section>
  );
}

export default LeadCta;
