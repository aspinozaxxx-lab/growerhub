import { useCallback, useEffect, useState } from 'react';
import { fetchAdminPushokPilots, markPushokPilotContacted } from '../../../api/pushokPilot';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Button from '../../../components/ui/Button';
import Surface from '../../../components/ui/Surface';
import { formatDateTimeDDMMYYYY } from '../../../utils/formatters';
import '../../../components/PushokPilot.css';

function AdminPushokPilots() {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try { setEntries(await fetchAdminPushokPilots()); }
    catch { setError('Не удалось загрузить заявки'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);
  const markContacted = async (userId) => {
    setBusy(userId);
    setError('');
    try {
      const { request } = await markPushokPilotContacted(userId);
      setEntries((current) => current.map((entry) => entry.user_id === userId ? { ...entry, request } : entry));
    } catch { setError('Не удалось обновить заявку'); }
    finally { setBusy(null); }
  };

  return <div className="admin-page ym-hide-content">
    <AppPageHeader title="Пилот ПушОк" right={<Button onClick={load} disabled={loading}>Обновить</Button>} />
    <p>Новых заявок: {entries.filter((entry) => !entry.request.contacted_at).length}. Всего: {entries.length}.</p>
    <p>Это запросы на обсуждение пилота. Кнопка «Связались» только отмечает обработку заявки.</p>
    {error ? <p role="alert" className="admin-error">{error}</p> : null}
    {loading ? <AppPageState kind="loading" title="Загружаем заявки…" /> : null}
    {!loading && !error && !entries.length ? <AppPageState kind="empty" title="Заявок пока нет" /> : null}
    {entries.map(({ user_id: userId, username, email, request }) => <Surface key={userId} variant="card" padding="md" className="admin-section">
      <h2>{username || email || `Пользователь ${userId}`}</h2>
      <p>{request.contacted_at ? 'Связались' : 'Новая заявка'} · {formatDateTimeDDMMYYYY(request.requested_at)}</p>
      <p className="pushok-pilot__contact"><strong>{({ TELEGRAM: 'Telegram', EMAIL: 'Email', OTHER: 'Другой способ' })[request.contact_method]}:</strong> {request.contact}</p>
      {request.equipment ? <p className="pushok-pilot__contact">{request.equipment}</p> : null}
      {!request.contacted_at ? <Button onClick={() => markContacted(userId)} isLoading={busy === userId} disabled={busy !== null}>Связались</Button> : null}
    </Surface>)}
  </div>;
}

export default AdminPushokPilots;
