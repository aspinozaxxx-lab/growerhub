import { useEffect, useState } from 'react';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import { createZone, createZoneSection, deleteZone, fetchAutomationOverview, updateZone } from '../../api/selfService';
import { trackProductGoal } from '../../utils/metrika';
import './SelfServicePages.css';
import { translateApp } from '../../locales/i18n';

function AppZones() {
  const [overview, setOverview] = useState(null);
  const [name, setName] = useState(translateApp("Новая зона"));
  const [busy, setBusy] = useState('loading');
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setOverview(await fetchAutomationOverview());
      setError('');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = async (event) => {
    event.preventDefault();
    setBusy('create');
    try {
      const afterZone = await createZone(name.trim());
      const zone = afterZone.rooms?.findLast?.((item) => item.name === name.trim()) || afterZone.rooms?.at(-1);
      const updated = zone ? await createZoneSection(zone.id) : afterZone;
      setOverview(updated);
      setName(translateApp("Новая зона"));
      trackProductGoal('zone_created', { placement: 'zones', step: 'zone_only' });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  const toggleZone = async (zone) => {
    setBusy(zone.id);
    try {
      setOverview(await updateZone(zone.id, { name: zone.name, enabled: !zone.enabled }));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy('');
    }
  };

  const removeZone = async (zone) => {
    if (!window.confirm(translateApp("Удалить зону «{{value1}}» и её настройки автоматизации?", { value1: zone.name }))) return;
    setBusy(zone.id);
    try {
      await deleteZone(zone.id);
      await load();
    } catch (requestError) {
      setError(requestError.message);
      setBusy('');
    }
  };

  if (busy === 'loading') return <AppPageState kind="loading" title={translateApp("Загружаем зоны…")} />;

  return (
    <div className="self-service-page">
      <AppPageHeader title={translateApp("Зоны")} />
      {error ? <AppPageState kind="error" title={error} /> : null}
      <div className="zone-grid">
        {(overview?.rooms || []).map((zone) => (
          <article className="zone-card" key={zone.id}>
            <div className="zone-card__header"><h2>{zone.name}</h2><span className={zone.enabled ? 'status-chip is-online' : 'status-chip'}>{zone.enabled ? translateApp("Активна") : translateApp("Выключена")}</span></div>
            <p>
              {translateApp('section_count', { count: zone.boxes.length })}
              {' · '}
              {translateApp('resource_count', {
                count: zone.boxes.reduce((sum, section) => sum + section.resources.length, 0),
              })}
            </p>
            <div className="zone-sections">{zone.boxes.map((section) => <div key={section.id}><strong>{section.name}</strong><span>{section.resources.length ? section.resources.map((item) => item.label || item.role).join(', ') : translateApp("устройства не назначены")}</span></div>)}</div>
            <div className="inline-actions"><Button onClick={() => toggleZone(zone)} isLoading={busy === zone.id}>{zone.enabled ? translateApp("Выключить") : translateApp("Включить")}</Button><Button variant="danger" onClick={() => removeZone(zone)} disabled={busy === zone.id}>{translateApp("Удалить")}</Button></div>
          </article>
        ))}
      </div>
      <section className="self-service-section">
        <h2>{translateApp("Новая зона")}</h2>
        <p>{translateApp("Нужно только понятное вам название. Растения добавлять необязательно.")}</p>
        <form className="compact-form" onSubmit={handleCreate}><label>{translateApp("Название")}<input value={name} onChange={(event) => setName(event.target.value)} required maxLength="120" /></label><Button type="submit" variant="primary" isLoading={busy === 'create'}>{translateApp("Создать зону")}</Button></form>
      </section>
    </div>
  );
}

export default AppZones;
