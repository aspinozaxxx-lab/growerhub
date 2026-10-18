import { useEffect, useState } from 'react';
import { assistantRequest } from '../../../api/assistant';
import CodexStatus from '../../../features/assistant/CodexStatus';
import FeatureRequests from '../../../features/assistant/FeatureRequests';
import Button from '../../../components/ui/Button';
export default function AdminAssistant() {
  const [data, setData] = useState(null); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const load = () => assistantRequest('/usage').then(setData).catch(e => setError(e.message));
  useEffect(() => { load(); }, []);
  const money = n => `$${(n / 1000000).toFixed(4)}`;
  return <section className="care-panel"><h1>Помощник: расходы и очередь</h1>{error && <p role="alert">{error}</p>}{data && <><p>Месячный предел: {money(data.monthLimitMicros)}, месяц UTC. Учёт использует полный тариф без скидок провайдера.</p><table><thead><tr><th>Показатель</th><th>Значение</th></tr></thead><tbody>{[['Месяц, включая резервы', money(data.monthMicros)], ['Последние 24 часа', money(data.dayMicros)], ['Последний час', money(data.hourMicros)], ['Активные резервы', money(data.reservedMicros)], ['Запросов в журнале', data.requests], ['Отклонено по ограничениям', data.rejected], ...Object.entries(data.statuses), ...Object.entries(data.denials || {})].map(([k, v]) => <tr key={k}><td>{k}</td><td>{v}</td></tr>)}</tbody></table><p>Публичный помощник: {data.publicEnabled ? 'включён' : 'выключен'}</p><Button disabled={busy} onClick={async () => { setBusy(true); try { setData(await assistantRequest('/public', 'PUT', { enabled: !data.publicEnabled })); } catch (e) { setError(e.message); } finally { setBusy(false); } }}>{data.publicEnabled ? 'Остановить публичные ответы' : 'Включить публичные ответы'}</Button></>}<Button disabled={busy} onClick={load}>Обновить</Button><CodexStatus /><FeatureRequests /></section>;
}
