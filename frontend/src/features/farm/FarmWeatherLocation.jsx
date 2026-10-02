import { useState } from 'react';
import Button from '../../components/ui/Button';
import { translateApp as t } from '../../locales/i18n';

export default function FarmWeatherLocation({ farm, onSave, disabled }) {
  const saved = farm.weather_location;
  const [latitude, setLatitude] = useState(saved?.latitude ?? '');
  const [longitude, setLongitude] = useState(saved?.longitude ?? '');
  const [label, setLabel] = useState(saved?.label ?? '');
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState('');
  const locate = () => {
    if (!navigator.geolocation) { setError(t('Определение места недоступно. Введите координаты вручную.')); return; }
    setLocating(true); setError('');
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      setLatitude(Math.round(coords.latitude * 100) / 100);
      setLongitude(Math.round(coords.longitude * 100) / 100);
      setLocating(false);
    }, () => { setError(t('Не удалось определить место. Разрешите доступ или введите координаты вручную.')); setLocating(false); },
    { enableHighAccuracy: false, timeout: 10000, maximumAge: 0 });
  };
  return <details className="farm-zones-settings__editor farm-weather-location">
    <summary><strong>{t('Место для прогноза')}</strong><span>{saved?.label || (saved ? `${saved.latitude}, ${saved.longitude}` : t('Не указано'))}</span></summary>
    <form className="farm-zones-settings__editor-fields" onSubmit={(event) => {
      event.preventDefault(); onSave({ latitude: Math.round(Number(latitude) * 100) / 100,
        longitude: Math.round(Number(longitude) * 100) / 100, label: label.trim() || null });
    }}>
      <p>{t('Определяйте место, находясь рядом с фермой. При учёте дождя округлённые координаты передаются MET Norway. Точный адрес не нужен; настройка сама не включает полив.')}</p>
      <Button type="button" size="sm" onClick={locate} disabled={disabled || locating}>{t(locating ? 'Определяем…' : 'Определить здесь')}</Button>
      <label>{t('Название места')}<input value={label} onChange={(event) => setLabel(event.target.value)} maxLength={120} disabled={disabled} /></label>
      <div className="farm-weather-location__coordinates">
        <label>{t('Широта')}<input type="number" step="any" min={-90} max={90} required value={latitude} onChange={(event) => setLatitude(event.target.value)} disabled={disabled} /></label>
        <label>{t('Долгота')}<input type="number" step="any" min={-180} max={180} required value={longitude} onChange={(event) => setLongitude(event.target.value)} disabled={disabled} /></label>
      </div>
      {error ? <p role="alert">{error}</p> : null}
      <div className="farm-zones-settings__actions">
        <Button type="submit" size="sm" disabled={disabled || latitude === '' || longitude === ''}>{t('Сохранить место')}</Button>
        {saved ? <Button type="button" size="sm" variant="secondary" disabled={disabled} onClick={() => onSave({ latitude: null, longitude: null })}>{t('Удалить место')}</Button> : null}
      </div>
    </form>
  </details>;
}
