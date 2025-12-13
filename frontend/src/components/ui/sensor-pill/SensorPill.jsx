import React from 'react';
import { formatSensorValue } from '../../../utils/formatters';
import './SensorPill.css';
import iconTemperature from './assets/temperature.svg';
import iconAirHumidity from './assets/air-humidity.svg';
import iconSoilMoisture from './assets/soil-moisture.svg';
import iconWatering from './assets/watering.svg';
import iconPump from './assets/pump.svg';
import iconUnknown from './assets/unknown.svg';

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

function SensorPill({
  kind,
  value,
  onClick,
  disabled = false,
  highlight = false,
  action = null,
}) {
  const icon = ICONS[kind] || iconUnknown;
  const unit = UNITS[kind] || '';

  const isClickable = typeof onClick === 'function';

  const isWatering = kind === 'watering';
  const isPump = kind === 'pump';

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
      <span className="sensor-pill__icon" aria-hidden="true">
        <img src={icon} alt="" />
      </span>
      <span className="sensor-pill__value">
        {displayValue}
        {showUnit ? (
          <span className="sensor-pill__unit">
            {displayUnit}
          </span>
        ) : null}
      </span>
    </Element>
  );
}

export default SensorPill;
