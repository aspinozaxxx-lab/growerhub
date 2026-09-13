import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { fetchDemoStatus, fetchDemoCatalog, addDemoDevice, changeDemoEnvironment } from '../../api/demo';
import { useAuth } from '../../features/auth/AuthContext';
import AppPageHeader from '../../components/layout/AppPageHeader';
import { translateApp as t } from '../../locales/i18n';
import './AppDemo.css';

export default function AppDemoTools() {
  const { demoActive } = useAuth();
  const [status, setStatus] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [profile, setProfile] = useState('controller');
  const [name, setName] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [temperature, setTemperature] = useState(25);
  const [moisture, setMoisture] = useState(60);
  const [leak, setLeak] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const sensors = (status?.devices || []).filter((device) => ['controller', 'air', 'soil', 'leak'].includes(device.profile));
  const selected = sensors.find((device) => device.id === selectedId) || sensors[0];

  useEffect(() => {
    if (!demoActive) return;
    let cancelled = false;
    Promise.all([fetchDemoStatus(), fetchDemoCatalog()]).then(([next, profiles]) => {
      if (!cancelled) { setStatus(next); setCatalog(profiles); }
    }).catch((failure) => { if (!cancelled && failure.name !== 'AbortError') setError(failure.message); });
    return () => { cancelled = true; };
  }, [demoActive]);

  useEffect(() => {
    setTemperature(Math.round((selected?.state?.temperature ?? 25) * 100) / 100);
    setMoisture(Math.round((selected?.state?.moisture ?? 60) * 100) / 100);
    setLeak(Boolean(selected?.state?.water_leak));
  }, [selected]);

  if (!demoActive) return <Navigate to="/app/demo/" replace />;
  const add = async (event) => {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      await addDemoDevice({ profile, name: name.trim() || null });
      setStatus(await fetchDemoStatus()); setName('');
      setNotice(t('Устройство добавлено. Назначьте его в слот теплицы в конструкторе фермы.'));
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  };
  const apply = async (event) => {
    event.preventDefault(); if (!selected) return;
    setBusy(true); setError(''); setNotice('');
    const body = { device_id: selected.id };
    if (['controller', 'air'].includes(selected.profile)) body.temperature = Number(temperature);
    if (['controller', 'soil'].includes(selected.profile)) body.moisture = Number(moisture);
    if (selected.profile === 'leak') body.leak = leak;
    try {
      setStatus(await changeDemoEnvironment(body));
      setNotice(t('Показания изменены. Включённые сценарии отреагируют на них автоматически.'));
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  };
  return (
    <div className="demo-tools">
      <AppPageHeader title={t('Устройства и условия среды')} subtitle={t('Проверьте, как ваша настройка реагирует на жару, сухую почву и протечку.')} />
      <div className="demo-actions">
        <Link className="demo-link" to="/app/farm/">{t('Конструктор фермы')}</Link>
        <Link className="demo-link" to="/app/settings/devices/">{t('Все устройства')}</Link>
        <Link className="demo-link" to="/app/automations/">{t('Сценарии')}</Link>
      </div>
      {error ? <p className="demo-message demo-message--error" role="alert">{error}</p> : null}
      {notice ? <p className="demo-message" role="status">{notice}</p> : null}
      <div className="demo-tools__grid">
        <section className="demo-tool-card">
          <h2>{t('Добавить виртуальное устройство')}</h2>
          <form onSubmit={add}>
            <label htmlFor="demo-profile">{t('Тип устройства')}</label>
            <select id="demo-profile" value={profile} onChange={(event) => setProfile(event.target.value)} disabled={busy || !catalog.length}>
              {catalog.map((item) => <option key={item.key} value={item.key}>{item.name}</option>)}
            </select>
            <p className="demo-hint">{catalog.find((item) => item.key === profile)?.description}</p>
            <label htmlFor="demo-name">{t('Название')}</label>
            <input id="demo-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={80} placeholder={t('Например, свет для рассады')} />
            <button className="gh-btn gh-btn--primary gh-btn--md" disabled={busy || !catalog.length}>{t('Добавить устройство')}</button>
          </form>
          <p>{t('Виртуальные розетки считают время работы и расход энергии. Насос меняет влажность почвы и записывает поливы в журнал растений.')}</p>
        </section>
        <section className="demo-tool-card">
          <h2>{t('Изменить условия')}</h2>
          <form onSubmit={apply}>
            <label htmlFor="demo-sensor">{t('Датчик или контроллер')}</label>
            <select id="demo-sensor" value={selected?.id || ''} onChange={(event) => setSelectedId(event.target.value)} disabled={busy || !sensors.length}>
              {sensors.map((device) => <option key={device.id} value={device.id}>{device.name}</option>)}
            </select>
            {selected && ['controller', 'air'].includes(selected.profile) ? <>
              <label htmlFor="demo-temperature">{t('Температура воздуха, °C')}</label>
              <input id="demo-temperature" type="number" min="-20" max="60" step="any" value={temperature} onChange={(event) => setTemperature(event.target.value)} required />
            </> : null}
            {selected && ['controller', 'soil'].includes(selected.profile) ? <>
              <label htmlFor="demo-moisture">{t('Влажность почвы, %')}</label>
              <input id="demo-moisture" type="number" min="0" max="100" step="any" value={moisture} onChange={(event) => setMoisture(event.target.value)} required />
            </> : null}
            {selected?.profile === 'leak' ? <label className="demo-checkbox"><input type="checkbox" checked={leak} onChange={(event) => setLeak(event.target.checked)} />{t('Обнаружена протечка')}</label> : null}
            <button className="gh-btn gh-btn--primary gh-btn--md" disabled={busy || !selected}>{t('Применить условия')}</button>
          </form>
          <p>{t('Попробуйте температуру 34 °C: вытяжка и общий климат сработают по текущим настройкам. Измените пороги в разделе сценариев и повторите опыт.')}</p>
        </section>
      </div>
    </div>
  );
}
