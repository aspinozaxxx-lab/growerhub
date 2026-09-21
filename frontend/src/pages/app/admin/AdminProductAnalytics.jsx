import { useEffect, useState } from 'react';
import { fetchAdminProductAnalytics } from '../../../api/admin';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import { useAuth } from '../../../features/auth/AuthContext';
import './AdminPages.css';
import { formatDateTimeDDMMYYYY } from '../../../utils/formatters';

const FUNNEL = [
  ['registrations', 'Регистрация'],
  ['users_with_coordinator', 'Создан координатор'],
  ['users_with_connected_coordinator', 'Координатор подключён'],
  ['users_with_first_device', 'Первое устройство'],
  ['users_with_zone', 'Первая зона'],
  ['users_with_automation', 'Первая автоматизация'],
];

function AdminProductAnalytics() {
  const { token } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    fetchAdminProductAnalytics(token)
      .then((result) => { if (!cancelled) setData(result); })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); });
    return () => { cancelled = true; };
  }, [token]);

  if (!data && !error) return <AppPageState kind="loading" title="Загружаем воронку…" />;
  if (error) return <AppPageState kind="error" title={error} />;

  return (
    <div className="admin-page">
      <AppPageHeader title="Продуктовая воронка" />
      <p>Воронка зарегистрированных аккаунтов: демо-аккаунты и администраторы исключены. Обновлено {formatDateTimeDDMMYYYY(data.generated_at)}.</p>
      <div className="admin-funnel-grid">
        {FUNNEL.map(([key, label]) => <article key={key}><span>{label}</span><strong>{data[key]}</strong><small>уникальных аккаунтов</small></article>)}
      </div>
      <section className="admin-section">
        <h2>Все реальные фермы</h2>
        <p>Включая фермы администраторов. В периоды входят неархивные координаторы, выходившие на связь хотя бы раз за указанное время.</p>
        <div className="admin-funnel-grid">
          <article><span>Создано координаторов</span><strong>{data.coordinators_created}</strong></article>
          <article><span>Подключалось координаторов</span><strong>{data.coordinators_connected}</strong></article>
          <article><span>Связь за последние 24 часа</span><strong>{data.active_coordinators_1d}</strong></article>
          <article><span>Связь за последние 7 дней</span><strong>{data.active_coordinators_7d}</strong></article>
          <article><span>Связь за последние 28 дней</span><strong>{data.active_coordinators_28d}</strong></article>
          <article><span>Зон / включённых сценариев</span><strong>{data.zones_created} / {data.automations_enabled}</strong></article>
        </div>
      </section>
    </div>
  );
}

export default AdminProductAnalytics;
