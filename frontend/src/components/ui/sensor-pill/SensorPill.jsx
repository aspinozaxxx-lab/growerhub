import React from 'react';
import { formatSensorValue } from '../../../utils/formatters';
import './SensorPill.css';
import iconTemperature from './assets/temperature.svg?raw';
import iconAirHumidity from './assets/air-humidity.svg?raw';
import iconSoilMoisture from './assets/soil-moisture.svg?raw';
import iconWatering from './assets/watering.svg?raw';
import iconPump from './assets/pump.svg?raw';
import iconUnknown from './assets/unknown.svg?raw';

const WARNING_ICON = `
<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M12 3.5L21 19H3L12 3.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
  <path d="M12 9V13.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
  <circle cx="12" cy="17" r="1.1" fill="currentColor"/>
</svg>
`;

const ICONS = {
  air_temperature: iconTemperature,
  air_humidity: iconAirHumidity,
  soil_moisture: iconSoilMoisture,
  watering: iconWatering,
  pump: iconPump,
};

const UNITS = {
  air_temperature: '°C',
  air_humidity: '%',
  soil_moisture: '%',
};

function buildStatusTooltip(kind, status) {
  if (!status || status === 'OK') {
    return '';
  }
  if (status === 'DISCONNECTED') {
    return 'datchik otklyuchen';
  }
  if (status === 'ERROR') {
    if (kind === 'soil_moisture') {
      return 'proverte datchik vlazhnosti pochvy';
    }
    return 'proverte datchik temperatury';
  }
  return '';
}

function SensorPill({
  kind,
  value,
  status = 'OK',
  onClick,
  disabled = false,
  highlight = false,
  action = null,
  className = '',
}) {
  const iconMarkup = ICONS[kind] || iconUnknown;
  const unit = UNITS[kind] || '';

  const isClickable = typeof onClick === 'function';

  const isWatering = kind === 'watering';
  const isPump = kind === 'pump';
  const statusTooltip = buildStatusTooltip(kind, status);
  const showWarning = !isWatering && !isPump && Boolean(statusTooltip);

  let displayValue = value;
  let displayUnit = unit;
  let computedHighlight = highlight;

  if (isWatering) {
    const wateringActive = typeof value === 'boolean' ? value : value === 'Выполняется';
    displayValue = wateringActive ? 'Выполняется' : 'Нет';
    computedHighlight = highlight || wateringActive;
    displayUnit = '';
  } else if (isPump) {
    if (value === null || value === undefined || value === '') {
      displayValue = 'не задано';
    } else {
      const formatted = formatSensorValue(value, 1);
      displayValue = `${formatted} л/ч`;
    }
    displayUnit = '';
  } else {
    displayValue = formatSensorValue(value, 1);
  }

  const showUnit = Boolean(displayUnit) && displayValue !== '—';

  const classNames = [
    'sensor-pill',
    computedHighlight ? 'is-highlight' : '',
    isClickable ? 'is-clickable' : '',
    disabled ? 'is-disabled' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const Element = isClickable ? 'button' : 'div';

  return (
    <Element
      type={isClickable ? 'button' : undefined}
      className={classNames}
      onClick={onClick}
      disabled={disabled && isClickable}
      aria-label={action === 'edit' ? 'Редактировать' : undefined}
    >
      <span
        className="sensor-pill__icon"
        aria-hidden="true"
        dangerouslySetInnerHTML={{ __html: iconMarkup }}
      />
      <span className="sensor-pill__value">
        {displayValue}
        {showUnit ? (
          <span className="sensor-pill__unit">
            {displayUnit}
          </span>
        ) : null}
      </span>
      {showWarning ? (
        <span
          className="sensor-pill__warning"
          title={statusTooltip}
          aria-label={statusTooltip}
          dangerouslySetInnerHTML={{ __html: WARNING_ICON }}
        />
      ) : null}
    </Element>
  );
}

export default SensorPill;
