import { useEffect, useState } from 'react';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import Button from '../../components/ui/Button';
import { createZone, createZoneSection, deleteZone, fetchAutomationOverview, updateZone } from '../../api/selfService';
import { trackProductGoal } from '../../utils/metrika';
import './SelfServicePages.css';

function AppZones() {
  const [overview, setOverview] = useState(null);
  const [name, setName] = useState('Новая зона');
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
      setName('Новая зона');
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
    if (!window.confirm(`Удалить зону «${zone.name}» и её настройки автоматизации?`)) return;
    setBusy(zone.id);
    try {
      await deleteZone(zone.id);
      await load();
    } catch (requestError) {
      setError(requestError.message);
      setBusy('');
    }
  };

  if (busy === 'loading') return <AppPageState kind="loading" title="Загружаем зоны…" />;

  return (
    <div className="self-service-page">
      <AppPageHeader title="Зоны" />
      {error ? <AppPageState kind="error" title={error} /> : null}
      <div className="zone-grid">
        {(overview?.rooms || []).map((zone) => (
          <article className="zone-card" key={zone.id}>
            <div className="zone-card__header"><h2>{zone.name}</h2><span className={zone.enabled ? 'status-chip is-online' : 'status-chip'}>{zone.enabled ? 'Активна' : 'Выключена'}</span></div>
            <p>{zone.boxes.length} секций · {zone.boxes.reduce((sum, section) => sum + section.resources.length, 0)} назначенных ресурсов</p>
            <div className="zone-sections">{zone.boxes.map((section) => <div key={section.id}><strong>{section.name}</strong><span>{section.resources.length ? section.resources.map((item) => item.label || item.role).join(', ') : 'устройства не назначены'}</span></div>)}</div>
            <div className="inline-actions"><Button onClick={() => toggleZone(zone)} isLoading={busy === zone.id}>{zone.enabled ? 'Выключить' : 'Включить'}</Button><Button variant="danger" onClick={() => removeZone(zone)} disabled={busy === zone.id}>Удалить</Button></div>
          </article>
        ))}
      </div>
      <section className="self-service-section">
        <h2>Новая зона</h2>
        <p>Нужно только понятное вам название. Растения добавлять необязательно.</p>
        <form className="compact-form" onSubmit={handleCreate}><label>Название<input value={name} onChange={(event) => setName(event.target.value)} required maxLength="120" /></label><Button type="submit" variant="primary" isLoading={busy === 'create'}>Создать зону</Button></form>
      </section>
    </div>
  );
}

export default AppZones;
