import React from 'react';
import { Link } from 'react-router-dom';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import { SLOT_ROLE_LABELS } from '../../features/farm/farmModel';
import { resolveDeviceAsset } from './assets';
import SensorPill from '../ui/sensor-pill/SensorPill';
import Button from '../ui/Button';
import Surface from '../ui/Surface';
import { Title, Text } from '../ui/Typography';
import WateringInProgressBanner from '../watering/WateringInProgressBanner';
import usePumpWateringStatus from '../../features/watering/usePumpWateringStatus';
import './DeviceCard.css';
import { translateApp } from '../../locales/i18n';

const SENSOR_KIND_MAP = {
  SOIL_MOISTURE: 'soil_moisture',
  AIR_TEMPERATURE: 'air_temperature',
  AIR_HUMIDITY: 'air_humidity',
};

const SENSOR_TITLE_MAP = {
  SOIL_MOISTURE: translateApp("Влажность почвы"),
  AIR_TEMPERATURE: translateApp("Температура воздуха"),
  AIR_HUMIDITY: translateApp("Влажность воздуха"),
};

// Translitem: DeviceCard - komponent ustrojstva s telemetriej i rolyami v teplicah.
function StatusBadge({ isOnline }) {
  return (
    <div className="device-card__status">
      <span className={`status-dot ${isOnline ? 'is-online' : 'is-offline'}`} aria-hidden="true" />
      {isOnline ? translateApp("Онлайн") : translateApp("Оффлайн")}
    </div>
  );
}

function buildSensorTitle(sensor) {
  const base = sensor?.label || SENSOR_TITLE_MAP[sensor?.type] || sensor?.type || translateApp("Датчик");
  if (sensor?.channel === null || sensor?.channel === undefined) {
    return base;
  }
  return translateApp("{{value1}} · канал {{value2}}", { value1: base, value2: sensor.channel });
}

function buildPumpTitle(pump) {
  const base = pump?.label || translateApp("Насос");
  if (pump?.channel === null || pump?.channel === undefined) {
    return base;
  }
  return translateApp("{{value1}} · канал {{value2}}", { value1: base, value2: pump.channel });
}

function DevicePumpRow({ pump, isOnline }) {
  const isWateringFallback = pump?.is_running === true;
  const { remainingSeconds, isRunning, stop } = usePumpWateringStatus(pump?.id, {
    enabled: Boolean(pump?.id && isWateringFallback),
  });
  const hasStatus = isRunning !== null && isRunning !== undefined;
  const isWatering = hasStatus ? isRunning : isWateringFallback;
  const statusLabel = hasStatus
    ? (isRunning ? translateApp("Выполняется") : translateApp("Остановлен"))
    : (pump.is_running === null || pump.is_running === undefined
      ? translateApp("Нет данных")
      : pump.is_running
        ? translateApp("Выполняется")
        : translateApp("Остановлен"));
  return (
    <div className="device-card__item">
      <div className="device-card__item-main">
        <div className="device-card__item-title">{buildPumpTitle(pump)}</div>
        <div className="device-card__item-status">
          {!isOnline ? (
            <SensorPill
              kind="watering"
              value={isWatering}
              isOffline
            />
          ) : isWatering ? (
            <WateringInProgressBanner
              isWatering={isWatering}
              remainingSeconds={remainingSeconds}
              onStop={stop}
            />
          ) : (
            statusLabel
          )}
        </div>
      </div>
    </div>
  );
}

const FIRMWARE_PROGRESS_LABELS = {
  QUEUED: 'Команда обновления отправлена',
  DOWNLOADING: 'Загрузка новой прошивки…',
  RESTARTING: 'Прошивка установлена, устройство перезапускается…',
};

const FIRMWARE_ERROR_LABELS = {
  hardware_profile_unknown: 'Аппаратная версия устройства не определена; требуется первичное обновление по USB',
  pump_running: 'Обновление нельзя выполнить, пока работает насос',
  invalid_ota_request: 'Устройство отклонило параметры обновления',
  tls_or_network_failed: 'Не удалось установить защищённое соединение для загрузки',
  firmware_http_failed: 'Сервер не отдал файл прошивки',
  ota_begin_failed: 'Недостаточно места для установки прошивки',
  firmware_download_failed: 'Загрузка прошивки прервалась',
  firmware_write_failed: 'Не удалось записать прошивку',
  firmware_sha256_mismatch: 'Контрольная сумма прошивки не совпала',
  ota_finalize_failed: 'Не удалось завершить установку прошивки',
  ota_unavailable: 'OTA недоступно на устройстве',
  device_update_timeout: 'Устройство не подтвердило обновление вовремя',
};

function resolveFirmwareError(status) {
  const error = status?.action_error || status?.error || status?.load_error;
  if (!error) return null;
  return translateApp(FIRMWARE_ERROR_LABELS[error] || error);
}

function FirmwarePanel({ device, firmwareStatus, isUpdating, onUpdate }) {
  const currentVersion = firmwareStatus?.current_version
    || device.firmware_version
    || device.current_version
    || translateApp('Не определена');
  const latestVersion = firmwareStatus?.latest_version || null;
  const updateState = firmwareStatus?.status || 'IDLE';
  const isActive = ['QUEUED', 'DOWNLOADING', 'RESTARTING'].includes(updateState);
  const updateAvailable = firmwareStatus?.update_available === true;
  const isUpToDate = Boolean(latestVersion && currentVersion === latestVersion && !updateAvailable);
  const error = resolveFirmwareError(firmwareStatus);
  const showUpdateButton = updateAvailable && !isActive;

  return (
    <div className="device-card__firmware" aria-live="polite">
      <div className="device-card__firmware-version">
        <span>{translateApp('Прошивка')}</span>
        <strong>{currentVersion}</strong>
      </div>
      {updateAvailable ? (
        <div className="device-card__firmware-update">
          {translateApp('Доступна новая версия: {{value1}}', { value1: latestVersion })}
        </div>
      ) : null}
      {isUpToDate ? (
        <div className="device-card__firmware-success">
          {translateApp('Установлена последняя версия')}
        </div>
      ) : null}
      {isActive ? (
        <div className="device-card__firmware-progress" role="status">
          {translateApp(FIRMWARE_PROGRESS_LABELS[updateState])}
        </div>
      ) : null}
      {error ? <div className="device-card__firmware-error" role="alert">{error}</div> : null}
      {showUpdateButton ? (
        <Button
          type="button"
          variant="primary"
          size="sm"
          onClick={onUpdate}
          isLoading={isUpdating}
          disabled={!device.is_online}
          title={!device.is_online ? translateApp('Подключите устройство к сети для обновления') : undefined}
        >
          {updateState === 'ERROR' || firmwareStatus?.action_error
            ? translateApp('Повторить обновление')
            : translateApp('Обновить прошивку')}
        </Button>
      ) : null}
    </div>
  );
}

function DeviceCard({
  device,
  assignments = [],
  firmwareStatus = null,
  isFirmwareUpdating = false,
  onFirmwareUpdate,
}) {
  const { openSensorStats } = useSensorStatsContext();
  const avatarKey = 'grovika_mini';
  const avatarSrc = resolveDeviceAsset(avatarKey);
  const displayName = device.name || translateApp("Устройство");

  const sensors = Array.isArray(device.sensors) ? device.sensors : [];
  const pumps = Array.isArray(device.pumps) ? device.pumps : [];

  const handleSensorStats = (sensor) => {
    const kind = SENSOR_KIND_MAP[sensor.type] || 'soil_moisture';
    openSensorStats({
      mode: 'sensor',
      sensorId: sensor.id,
      metric: kind,
      title: buildSensorTitle(sensor),
      subtitle: displayName,
    });
  };

  return (
    <Surface variant="card" padding="md" className="device-card">
      <div className="device-card__header">
        <div>
          <Title level={3} className="device-card__title">{displayName}</Title>
          <Text tone="muted" className="device-card__subtitle">{device.device_id}</Text>
          <StatusBadge isOnline={device.is_online} />
        </div>
      </div>

      <div className="device-card__body">
        <div className="device-card__avatar" aria-hidden="true">
          <img src={avatarSrc} alt="device avatar" />
        </div>
        <div className="device-card__info">
          <FirmwarePanel
            device={device}
            firmwareStatus={firmwareStatus}
            isUpdating={isFirmwareUpdating}
            onUpdate={onFirmwareUpdate}
          />
        </div>
      </div>

      <div className="device-card__roles">
        <span>{translateApp("Роли в ферме")}</span>
        {assignments.length > 0 ? (
          <div>
            {assignments.map((assignment) => (
              <Link
                key={`${assignment.zoneId}:${assignment.role}`}
                to="/app/farm/"
                title={translateApp("Изменить назначение в Конструкторе фермы")}
              >
                {assignment.zoneName} · {translateApp(SLOT_ROLE_LABELS[assignment.role] || assignment.role)}
              </Link>
            ))}
          </div>
        ) : (
          <Link to="/app/farm/">{translateApp("Не назначено — открыть конструктор")}</Link>
        )}
      </div>

      <div className="device-card__section">
        <div className="device-card__section-title">{translateApp("Датчики")}</div>
        {sensors.length === 0 && <div className="device-card__empty">{translateApp("Нет датчиков")}</div>}
        {sensors.map((sensor) => {
          const kind = SENSOR_KIND_MAP[sensor.type] || 'soil_moisture';
          return (
            <div key={sensor.id} className="device-card__item">
              <div className="device-card__item-main">
                <div className="device-card__item-title">{buildSensorTitle(sensor)}</div>
                <SensorPill
                  kind={kind}
                  value={sensor.last_value}
                  status={sensor.status}
                  isOffline={!device.is_online}
                  onClick={() => handleSensorStats(sensor)}
                />
              </div>
            </div>
          );
        })}
      </div>

      <div className="device-card__section">
        <div className="device-card__section-title">{translateApp("Насосы")}</div>
        {pumps.length === 0 && <div className="device-card__empty">{translateApp("Нет насосов")}</div>}
        {pumps.map((pump) => (
          <DevicePumpRow key={pump.id} pump={pump} isOnline={Boolean(device.is_online)} />
        ))}
      </div>
    </Surface>
  );
}

export default DeviceCard;
