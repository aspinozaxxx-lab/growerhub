import { translateApp } from '../../locales/i18n';
import './ClimateScenarioFields.css';

const CLIMATE_FIELD_DEFINITIONS = [
  ['max_c', 'Обдув включить выше, °C'],
  ['exhaust_off_below_c', 'Обдув выключить ниже, °C'],
  ['ac_request_above_c', 'Запрос охлаждения выше, °C'],
  ['ac_clear_below_c', 'Снять запрос ниже, °C'],
];

const AIR_CONDITIONER_FIELD_DEFINITIONS = [
  ['off_delay_minutes', 'Задержка выключения, мин'],
  ['min_toggle_minutes', 'Защита от частых переключений, мин'],
];

function NumberFields({ fields, config, onChange }) {
  return (
    <div className="climate-settings-fields">
      {fields.map(([field, label]) => (
        <label key={field}>
          <span>{translateApp(label)}</span>
          <input
            type="number"
            step={field.endsWith('_c') ? '0.1' : '1'}
            min={field.endsWith('_minutes') ? '0' : undefined}
            value={config?.[field] ?? ''}
            onChange={(event) => onChange(field, event.target.value === ''
              ? ''
              : Number(event.target.value))}
          />
        </label>
      ))}
    </div>
  );
}

export function ClimateScenarioFields({ config, onChange }) {
  return (
    <NumberFields
      fields={CLIMATE_FIELD_DEFINITIONS}
      config={config}
      onChange={onChange}
    />
  );
}

export function AirConditionerSettingsFields({ config, onChange }) {
  return (
    <NumberFields
      fields={AIR_CONDITIONER_FIELD_DEFINITIONS}
      config={config}
      onChange={onChange}
    />
  );
}
