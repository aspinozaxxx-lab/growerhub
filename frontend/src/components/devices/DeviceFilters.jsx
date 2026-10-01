import { EMPTY_DEVICE_FILTERS, listOrEmpty, overviewFarms } from '../../features/farm/farmModel';
import { translateApp as t } from '../../locales/i18n';

export default function DeviceFilters({ overview, nativeDevices = [], filters, onChange, shown, total }) {
  const coordinators = [...new Map(listOrEmpty(overview?.resource_catalog?.zigbee_devices)
    .map((device) => [String(device.coordinator_id), device.coordinator_name || t('Координатор')])).entries()];
  const change = (field, value) => onChange({ ...filters, [field]: value });
  const isFiltered = Object.entries(EMPTY_DEVICE_FILTERS).some(([key, value]) => filters[key] !== value);

  return (
    <div className="device-filters">
      <div className="farm-device-filters">
        <label>
          <span>{t('Поиск')}</span>
          <input type="search" value={filters.query} onChange={(event) => change('query', event.target.value)}
            placeholder={t('Название, модель или ID')} />
        </label>
        <label>
          <span>{t('Размещение')}</span>
          <select value={filters.placement} onChange={(event) => change('placement', event.target.value)}>
            <option value="all">{t('Все зоны')}</option>
            <option value="unassigned">{t('Без назначения')}</option>
            {overviewFarms(overview).map((farm) => (
              <optgroup key={farm.id} label={farm.name}>
                <option value={`farm:${farm.id}`}>{t('Вся ферма: {{value1}}', { value1: farm.name })}</option>
                {listOrEmpty(farm.greenhouses).map((greenhouse) => (
                  <option key={greenhouse.id} value={`greenhouse:${greenhouse.id}`}>{greenhouse.name}</option>
                ))}
              </optgroup>
            ))}
          </select>
        </label>
        <label>
          <span>{t('Состояние')}</span>
          <select value={filters.availability} onChange={(event) => change('availability', event.target.value)}>
            <option value="all">{t('Любое состояние')}</option>
            <option value="online">{t('В сети')}</option>
            <option value="offline">{t('Не в сети')}</option>
            <option value="unknown">{t('Статус неизвестен')}</option>
          </select>
        </label>
        <label>
          <span>{t('Подключение')}</span>
          <select value={filters.source} onChange={(event) => change('source', event.target.value)}>
            <option value="all">{t('Все подключения')}</option>
            {nativeDevices.length > 0 ? <option value="native">Grovika</option> : null}
            {coordinators.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
          </select>
        </label>
      </div>
      <div className="device-filters__summary">
        <span aria-live="polite">{t('Показано {{value1}} из {{value2}}', { value1: shown, value2: total })}</span>
        {isFiltered ? <button type="button" onClick={() => onChange({ ...EMPTY_DEVICE_FILTERS })}>{t('Сбросить фильтры')}</button> : null}
      </div>
    </div>
  );
}
