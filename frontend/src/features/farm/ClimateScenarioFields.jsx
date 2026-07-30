import { translateApp } from '../../locales/i18n';
import './ClimateScenarioFields.css';

const CLIMATE_FIELD_DEFINITIONS = [
  ['max_c', 'Обдув включить выше, °C'],
  ['exhaust_off_below_c', 'Обдув выключить ниже, °C'],
  ['ac_request_above_c', 'Запрос охлаждения выше, °C'],
  ['ac_clear_below_c', 'Снять запрос ниже, °C'],
];

export function ClimateScenarioFields({ config, onChange }) {
  return (
    <div className="climate-settings-fields">
      {CLIMATE_FIELD_DEFINITIONS.map(([field, label]) => (
        <label key={field}>
          <span>{translateApp(label)}</span>
          <input
            type="number"
            step="0.1"
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
