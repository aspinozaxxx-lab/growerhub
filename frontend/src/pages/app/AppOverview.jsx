import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import { fetchAutomationOverview, fetchCoordinators, fetchOnboardingStatus } from '../../api/selfService';
import './SelfServicePages.css';

function AppOverview() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    Promise.all([fetchOnboardingStatus(), fetchCoordinators(), fetchAutomationOverview()])
      .then(([status, coordinators, automation]) => {
        if (!cancelled) setData({ status, coordinators, automation });
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message);
      });
    return () => { cancelled = true; };
  }, []);

  if (!data && !error) return <AppPageState kind="loading" title="Загружаем обзор…" />;
  if (error) return <AppPageState kind="error" title={error} />;

  const deviceCount = data.coordinators.reduce((sum, item) => sum + (item.device_count || 0), 0);
  const zones = data.automation.rooms || [];

  return (
    <div className="self-service-page">
      <AppPageHeader title="Обзор" right={!data.status.zone_created ? <Link className="gh-btn gh-btn--primary gh-btn--md" to="/app/onboarding/">Продолжить настройку</Link> : null} />

      <div className="summary-grid">
        <article><span>Подключения</span><strong>{data.coordinators.length}</strong><small>{data.status.coordinator_connected ? 'есть связь' : 'ожидают запуска'}</small></article>
        <article><span>Устройства</span><strong>{deviceCount}</strong><small>{data.status.first_device_seen ? 'получают данные' : 'пока не найдены'}</small></article>
        <article><span>Зоны</span><strong>{zones.length}</strong><small>{data.status.zone_created ? 'настроены' : 'создайте первую зону'}</small></article>
        <article><span>Автоматизации</span><strong>{data.status.automation_enabled ? 'Вкл.' : 'Выкл.'}</strong><small>включаются по желанию</small></article>
      </div>

      {zones.length === 0 ? (
        <AppPageState kind="empty" title="Пока нет зон" hint="Подключите координатор, дождитесь первого устройства и создайте зону только по названию.">
          <Link className="hero-cta" to="/app/onboarding/">Начать настройку</Link>
        </AppPageState>
      ) : (
        <section className="self-service-section">
          <div className="section-heading"><div><h2>Зоны</h2><p>Текущие показания назначенных устройств.</p></div><Link to="/app/zones/">Все зоны</Link></div>
          <div className="zone-grid">
            {zones.map((zone) => (
              <article className="zone-card" key={zone.id}>
                <div className="zone-card__header"><h3>{zone.name}</h3><span className={zone.enabled ? 'status-chip is-online' : 'status-chip'}>{zone.enabled ? 'Активна' : 'Выключена'}</span></div>
                <div className="zone-card__metrics">
                  {zone.boxes.flatMap((section) => section.resources || []).slice(0, 4).map((resource) => (
                    <div key={resource.id}><span>{resource.label || resource.role}</span><strong>{resource.current_value ?? '—'}</strong></div>
                  ))}
                  {zone.boxes.every((section) => (section.resources || []).length === 0) ? <p>Назначьте устройства в разделе «Зоны».</p> : null}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

export default AppOverview;
