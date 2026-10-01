import { useMemo, useState } from 'react';
import Button from './ui/Button';
import { ZIGBEE_COORDINATOR_DOWNLOAD_URLS, ZIGBEE_CONNECTOR_DOWNLOAD_URL } from '../domain/siteConfig';
import { buildConnectorConfig, CONNECTION_MODES, SETUP_PLATFORMS } from '../domain/coordinatorSetup';
import { translateApp } from '../locales/i18n';
import './CoordinatorSetup.css';

const downloadTextFile = (name, content) => {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
};

export function CoordinatorConnectionMode({ value, onChange, children }) {
  return (
    <>
      <div className="onboarding-choice-grid" role="group" aria-label={translateApp("Способ подключения")}>
        {children}
        <button type="button" aria-pressed={value === CONNECTION_MODES.BRIDGE} className={value === CONNECTION_MODES.BRIDGE ? 'choice-card is-selected' : 'choice-card'} onClick={() => onChange(CONNECTION_MODES.BRIDGE)}>
          <strong>{translateApp("Уже работает Zigbee2MQTT")}</strong><span>{translateApp("Отдельно или внутри Home Assistant. Подключим существующие устройства через модуль связи.")}</span>
        </button>
        <button type="button" aria-pressed={value === CONNECTION_MODES.DIRECT} className={value === CONNECTION_MODES.DIRECT ? 'choice-card is-selected' : 'choice-card'} onClick={() => onChange(CONNECTION_MODES.DIRECT)}>
          <strong>{translateApp("Новая установка")}</strong><span>{translateApp("Есть USB-координатор и Zigbee-устройства. Настроим Zigbee2MQTT с подключением к GrowerHub.")}</span>
        </button>
      </div>
      {value === CONNECTION_MODES.BRIDGE ? (
        <>
          <p>{translateApp("Нужен компьютер с Docker в вашей сети. Существующая сеть Zigbee сохраняется. Если в Home Assistant используется ZHA, этот способ пока не подходит.")}</p>
          <p>{translateApp("После запуска модуля в ваш аккаунт GrowerHub поступят список и показания всех устройств выбранного Zigbee2MQTT. Модуль также передаёт команды из GrowerHub обратно устройствам; отдельного режима только чтения и выбора одного устройства пока нет.")}</p>
          <p>{translateApp("Сценарии включаются отдельно. Для каждого устройства используйте автоматизацию в одной системе — Home Assistant или GrowerHub, чтобы их команды не мешали друг другу.")}</p>
        </>
      ) : null}
    </>
  );
}

function CoordinatorSetup({ setup, connectionMode, onConnectionModeChange, onHide }) {
  const [platform, setPlatform] = useState(SETUP_PLATFORMS.WINDOWS);
  const [localMqtt, setLocalMqtt] = useState({ host: '', port: '1883', username: '', password: '', baseTopic: 'zigbee2mqtt' });
  const connectorConfig = useMemo(
    () => buildConnectorConfig({ setup, local: localMqtt }),
    [setup, localMqtt],
  );

  if (!setup) return null;

  return (
    <section className="onboarding-card onboarding-secret">
      <div>
        <div className="onboarding-kicker">{translateApp("Показываем один раз")}</div>
        <h2>{translateApp("Сохраните конфигурацию подключения")}</h2>
        <p>{translateApp("Пароль не хранится в GrowerHub и не появится снова после перезагрузки страницы. При утрате выпустите новые данные подключения.")}</p>
      </div>

      {connectionMode ? (
        <>
          <dl className="onboarding-credentials ym-hide-content">
            <div><dt>{translateApp("MQTT-сервер")}</dt><dd>{setup.server}</dd></div>
            <div><dt>{translateApp("Имя пользователя")}</dt><dd>{setup.username}</dd></div>
            <div><dt>{translateApp("Пароль")}</dt><dd>{setup.password}</dd></div>
            <div><dt>{translateApp("Базовый топик")}</dt><dd>{setup.base_topic}</dd></div>
          </dl>

          {connectionMode === CONNECTION_MODES.DIRECT ? <div className="onboarding-choice-grid" role="group" aria-label={translateApp("Платформа установки")}>
            {[
              [SETUP_PLATFORMS.WINDOWS, 'Windows', translateApp("Мастер с выбором COM-порта и Z-Stack/Ember.")],
              [SETUP_PLATFORMS.LINUX, 'Raspberry Pi / Linux', translateApp("Docker Compose, подключение USB и постоянное хранилище.")],
              [SETUP_PLATFORMS.MANUAL, translateApp("Вручную"), translateApp("Для уже установленного Zigbee2MQTT.")],
            ].map(([value, title, text]) => (
              <button
                type="button"
                aria-pressed={platform === value}
                className={platform === value ? 'choice-card is-selected' : 'choice-card'}
                key={value}
                onClick={() => setPlatform(value)}
              >
                <strong>{title}</strong><span>{text}</span>
              </button>
            ))}
          </div> : null}

          {connectionMode === CONNECTION_MODES.BRIDGE ? (
            <div className="onboarding-local-mqtt">
              <h3>{translateApp("Локальный MQTT")}</h3>
              <p>{translateApp("Укажите LAN-адрес MQTT-брокера и mqtt.base_topic из Zigbee2MQTT. Если брокер работает на том же компьютере с Docker Desktop, используйте host.docker.internal. Адрес 127.0.0.1 внутри модуля связи не ведёт к вашему брокеру.")}</p>
              <p>{translateApp("Эти значения используются только для создания файла в браузере и не отправляются GrowerHub.")}</p>
              <div className="onboarding-fields ym-hide-content">
                <label>{translateApp("Адрес")}<input value={localMqtt.host} onChange={(event) => setLocalMqtt((value) => ({ ...value, host: event.target.value }))} placeholder="192.168.1.10" /></label>
                <label>{translateApp("Порт")}<input value={localMqtt.port} onChange={(event) => setLocalMqtt((value) => ({ ...value, port: event.target.value }))} inputMode="numeric" /></label>
                <label>{translateApp("Базовая тема Zigbee2MQTT")}<input value={localMqtt.baseTopic ?? 'zigbee2mqtt'} onChange={(event) => setLocalMqtt((value) => ({ ...value, baseTopic: event.target.value }))} /></label>
                <label>{translateApp("Имя пользователя")}<input value={localMqtt.username} onChange={(event) => setLocalMqtt((value) => ({ ...value, username: event.target.value }))} autoComplete="off" /></label>
                <label>{translateApp("Пароль")}<input type="password" value={localMqtt.password} onChange={(event) => setLocalMqtt((value) => ({ ...value, password: event.target.value }))} autoComplete="new-password" /></label>
              </div>
            </div>
          ) : null}

          <div className="onboarding-actions">
            {connectionMode === CONNECTION_MODES.DIRECT ? (
              <>
                <Button variant="primary" onClick={() => downloadTextFile('configuration.yaml', setup.configuration_yaml)}>{translateApp("Скачать configuration.yaml")}</Button>
                <Button onClick={() => downloadTextFile('secret.yaml', setup.secret_yaml)}>{translateApp("Скачать secret.yaml")}</Button>
              </>
            ) : (
              <Button variant="primary" disabled={!connectorConfig} onClick={() => downloadTextFile('connector.json', connectorConfig)}>{translateApp("Скачать личный connector.json")}</Button>
            )}
            {connectionMode === CONNECTION_MODES.BRIDGE ? (
              <a className="gh-btn gh-btn--secondary gh-btn--md" href={ZIGBEE_CONNECTOR_DOWNLOAD_URL}>{translateApp("Скачать модуль связи")}</a>
            ) : platform !== SETUP_PLATFORMS.MANUAL ? (
              <a className="gh-btn gh-btn--secondary gh-btn--md" href={ZIGBEE_COORDINATOR_DOWNLOAD_URLS[platform]}>
                {platform === SETUP_PLATFORMS.WINDOWS ? translateApp("Скачать пакет для Windows") : translateApp("Скачать пакет для Raspberry Pi / Linux")}
              </a>
            ) : null}
          </div>
          {connectionMode === CONNECTION_MODES.DIRECT && platform !== SETUP_PLATFORMS.MANUAL ? (
            <div className="onboarding-install-steps">
              <h3>{translateApp("После скачивания")}</h3>
              {platform === SETUP_PLATFORMS.WINDOWS ? (
                <>
                  <p>{translateApp("Для запуска нужны Node.js и Corepack.")}{' '}<a href="https://www.zigbee2mqtt.io/guide/installation/05_windows.html" target="_blank" rel="noreferrer">{translateApp("Как подготовить Windows")}</a></p>
                  <ol>
                    <li>{translateApp("Распакуйте ZIP в постоянную папку на компьютере.")}</li>
                    <li>{translateApp("Положите скачанные configuration.yaml и secret.yaml в подпапку data.")}</li>
                    <li>{translateApp("Подключите USB-координатор и запустите setup-coordinator.bat: выберите порт и тип адаптера по модели устройства.")}</li>
                    <li>{translateApp("Запустите start-coordinator.bat и вернитесь сюда — статус подключения обновится автоматически.")}</li>
                  </ol>
                </>
              ) : (
                <>
                  <p>{translateApp("Нужен компьютер с Docker Compose и подключённым USB-координатором.")}</p>
                  <ol>
                    <li>{translateApp("Распакуйте ZIP и откройте README.md: в нём показано, как указать USB-порт и тип адаптера.")}</li>
                    <li>{translateApp("Создайте подпапку data и положите туда скачанные configuration.yaml и secret.yaml.")}</li>
                    <li>{translateApp("Откройте терминал в этой папке и выполните:")} <code>docker compose up -d</code>.</li>
                    <li>{translateApp("Вернитесь сюда — статус подключения обновится автоматически.")}</li>
                  </ol>
                </>
              )}
              <p>{translateApp("Компьютер должен оставаться включённым: во время сна новые показания не поступают.")}</p>
            </div>
          ) : null}
          {connectionMode === CONNECTION_MODES.BRIDGE ? (
            <div className="onboarding-install-steps">
              <h3>{translateApp("Запустите модуль связи")}</h3>
              <p>{translateApp("Нужен постоянно включённый компьютер с Docker Compose: Linux/Raspberry Pi или Windows с Docker Desktop в режиме Linux containers.")}</p>
              <ol>
                <li>{translateApp("Распакуйте скачанный модуль связи.")}</li>
                <li>{translateApp("Положите личный connector.json рядом с docker-compose.yml.")}</li>
                <li>{translateApp("Откройте терминал в этой папке и выполните:")} <code>docker compose up -d --build</code>.</li>
                <li>{translateApp("Дождитесь статуса «В сети» и появления уже сопряжённых устройств.")}</li>
              </ol>
              <p>{translateApp("После запуска дождитесь нового сообщения датчика. Сохранённое показание не добавляет точку истории и не подтверждает живую связь.")}</p>
              <p>{translateApp("Для Home Assistant OS запустите модуль связи на другом компьютере с Docker в той же сети. USB-координатор и настройки Zigbee2MQTT остаются на прежнем месте.")}</p>
            </div>
          ) : null}
          <p className="onboarding-note">{translateApp("Не публикуйте эти файлы и не отправляйте их в Telegram. GrowerHub никогда не просит прислать пароль MQTT.")}</p>
        </>
      ) : (
        <CoordinatorConnectionMode value={connectionMode} onChange={onConnectionModeChange} />
      )}
      {onHide ? <div className="onboarding-actions"><Button onClick={onHide}>{translateApp("Скрыть навсегда")}</Button></div> : null}
    </section>
  );
}

export default CoordinatorSetup;
