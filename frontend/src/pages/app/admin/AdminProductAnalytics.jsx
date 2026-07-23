import { useEffect, useState } from 'react';
import { fetchAdminProductAnalytics } from '../../../api/admin';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import { useAuth } from '../../../features/auth/AuthContext';
import './AdminPages.css';

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
      <p>Только агрегаты без email, user ID, coordinator ID и IEEE. Обновлено {new Date(data.generated_at).toLocaleString('ru-RU')}.</p>
      <div className="admin-funnel-grid">
        {FUNNEL.map(([key, label]) => <article key={key}><span>{label}</span><strong>{data[key]}</strong><small>уникальных пользователей</small></article>)}
      </div>
      <section className="admin-section">
        <h2>Координаторы</h2>
        <div className="admin-funnel-grid">
          <article><span>Создано</span><strong>{data.coordinators_created}</strong></article>
          <article><span>Подключалось</span><strong>{data.coordinators_connected}</strong></article>
          <article><span>Активны 1 день</span><strong>{data.active_coordinators_1d}</strong></article>
          <article><span>Активны 7 дней</span><strong>{data.active_coordinators_7d}</strong></article>
          <article><span>Активны 28 дней</span><strong>{data.active_coordinators_28d}</strong></article>
          <article><span>Зон / автоматизаций</span><strong>{data.zones_created} / {data.automations_enabled}</strong></article>
        </div>
      </section>
    </div>
  );
}

export default AdminProductAnalytics;
