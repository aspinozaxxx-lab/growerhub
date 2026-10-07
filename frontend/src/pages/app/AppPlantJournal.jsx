import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchPlant } from '../../api/plants';
import { downloadPlantJournalMarkdown, downloadJournalPhotoBlob } from '../../api/plantJournal';
import { careRequest } from '../../api/care';
import { useAuth } from '../../features/auth/AuthContext';
import { completionReasonLabel, formatDurationSeconds, modeLabel } from '../../features/manual-watering/manualWateringModel';
import { formatDateLong, formatTimeHHMM } from '../../utils/formatters';
import { translateApp } from '../../locales/i18n';
import { CARE_ACTIONS, careAction, careUtc } from '../../features/care/careModel';
import CarePhoto from '../../features/care/CarePhoto';
import CareEntryEditor from '../../features/care/CareEntryEditor';
import CareReminders from '../../features/care/CareReminders';
import PlantEditDialog from '../../components/plants/PlantEditDialog';
import Button from '../../components/ui/Button';
import './AppPlantJournal.css';
import '../../features/care/care.css';
const JOURNAL_TYPE_CONFIG = {
  watering: { label: translateApp('Полив'), icon: '💧' },
  photo: { label: translateApp('Фото'), icon: '📷' },
  other: { label: translateApp('Заметка'), icon: '✍️' },
};
function formatVolumeL(value) {
  if (value === null || value === undefined) return '';
  const str = Number(value).toFixed(3).replace(/\.?0+$/, '').replace('.', ',');
  return translateApp("{{value1}} л", { value1: str });
}

function PhotoPreview({ photo, token, cache, setCache }) {
  const [status, setStatus] = useState(photo?.has_data ? 'idle' : 'empty');

  useEffect(() => {
    let isMounted = true;
    if (!photo || !photo.has_data || cache[photo.id]) {
      return undefined;
    }
    downloadJournalPhotoBlob(photo.id, token)
      .then((blob) => {
        if (!isMounted) return;
        const url = URL.createObjectURL(blob);
        setCache((prev) => ({ ...prev, [photo.id]: url }));
        setStatus('ready');
      })
      .catch(() => {
        if (isMounted) setStatus('error');
      });
    return () => {
      isMounted = false;
    };
  }, [cache, photo, setCache, token]);

  if (!photo || !photo.has_data) {
    return <div className="journal-entry__photo-placeholder">{translateApp("Фото недоступно")}</div>;
  }
  if (status === 'loading') {
    return <div className="journal-entry__photo-placeholder">{translateApp("Загрузка фото...")}</div>;
  }
  if (status === 'error') {
    return <div className="journal-entry__photo-placeholder">{translateApp("Ошибка загрузки фото")}</div>;
  }
  const objectUrl = cache[photo.id];
  if (!objectUrl) {
    return <div className="journal-entry__photo-placeholder">{translateApp("Загрузка фото...")}</div>;
  }
  return <img src={objectUrl} alt={photo.caption || translateApp("Фото")} className="journal-entry__photo" />;
}

export function JournalEntryCard({ entry, onEdit, photoCache, setPhotoCache, token }) {
  const config = JOURNAL_TYPE_CONFIG[entry.type] || JOURNAL_TYPE_CONFIG.other;
  const time = formatTimeHHMM(entry.event_at);
  const details = entry.watering_details || null;
  const volume = details ? formatVolumeL(details.water_volume_l) : '';
  const fertilizers = details?.fertilizers_per_liter;
  const duration = details?.duration_s !== null && details?.duration_s !== undefined
    ? formatDurationSeconds(details.duration_s)
    : '';
  const wateringMode = details?.mode ? modeLabel(details.mode) : '';
  const completionReason = details?.completion_reason
    ? completionReasonLabel(details.completion_reason)
    : '';
  const hasPhoto = Array.isArray(entry.photos) && entry.photos.length > 0;
  const mainPhoto = hasPhoto ? entry.photos[0] : null;

  let content = entry.text || '';
  if (entry.type === 'watering' && (volume || fertilizers)) {
    const parts = [];
    if (volume) parts.push(volume);
    if (fertilizers) parts.push(translateApp("удобрения: {{value1}}", { value1: fertilizers }));
    content = parts.join('   ');
  }
  if (entry.type === 'photo' && !content) {
    content = translateApp("Фото");
  }

  return (
    <div className="journal-entry">
      {entry.type === 'watering' ? (
        <div className="journal-entry__watering">
          <span className="journal-entry__time">{time}</span>
          <span className="journal-entry__icon journal-entry__icon--big">{config.icon}</span>
          <div className="journal-entry__watering-summary">
            <span className="journal-entry__volume">
              {details ? (volume || translateApp("Объём не рассчитан")) : (entry.text || translateApp("Детали полива не указаны"))}
            </span>
            <div className="journal-entry__watering-facts">
              {duration ? <span>{translateApp("Длительность:")} {duration}</span> : null}
              {details?.volume_source === 'measured' ? <span>{translateApp('По расходомеру')}</span> : null}
              {details?.volume_source === 'estimated' ? <span>{translateApp('Расчётный объём')}</span> : null}
              {wateringMode ? <span>{translateApp("Режим:")} {wateringMode}</span> : null}
              {completionReason ? <span>{completionReason}</span> : null}
              {fertilizers ? <span>{translateApp("Удобрения: {{value1}}", { value1: fertilizers })}</span> : null}
            </div>
          </div>
        </div>
      ) : (
        <>
          <div className="journal-entry__meta">
            <div className="journal-entry__time">{time}</div>
            <div className="journal-entry__type">
              <span className="journal-entry__icon">{config.icon}</span>
              <span>{config.label}</span>
            </div>
          </div>
          <div className="journal-entry__body">
            {entry.type === 'photo' ? (
              <div className="journal-entry__photo-block">
                <div className="journal-entry__text">{content}</div>
                <PhotoPreview photo={mainPhoto} token={token} cache={photoCache} setCache={setPhotoCache} />
              </div>
            ) : (
              <div className="journal-entry__text">{content || '-'}</div>
            )}
          </div>
        </>
      )}
      {!details && <button type="button" className="journal-entry__edit" onClick={() => onEdit(entry)} title={translateApp("Редактировать")}>
        ✏
      </button>}
    </div>
  );
}


function AppPlantJournal() {
  const { plantId } = useParams(); const { token } = useAuth();
  const [plant, setPlant] = useState(null); const [items, setItems] = useState([]); const [more, setMore] = useState(false);
  const [query, setQuery] = useState(''); const [action, setAction] = useState(''); const [date, setDate] = useState('');
  const [page, setPage] = useState(0); const [reminders, setReminders] = useState([]); const [cover, setCover] = useState(null);
  const [editor, setEditor] = useState(null); const [editPlant, setEditPlant] = useState(false);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const request = useRef(0);
  const plantRequest = useRef(0);
  const reloadPlant = useCallback(async () => {
    const generation = ++plantRequest.current;
    const [value, tasks, summaries] = await Promise.all([fetchPlant(token, plantId), careRequest('/reminders'), careRequest('/summaries')]);
    if (generation !== plantRequest.current) return;
    setPlant(value); setReminders(tasks.filter(r => r.plantId === Number(plantId))); setCover(summaries.find(s => s.plantId === Number(plantId))?.coverPhotoId);
  }, [plantId, token]);
  const load = useCallback(async (nextPage = 0) => {
    const generation = ++request.current; setBusy(true); setError('');
    try {
      const params = new URLSearchParams({ plant_id: plantId, query, action, page: nextPage });
      if (date) { params.set('from', careUtc(`${date}T00:00`)); const day = new Date(`${date}T12:00:00Z`); day.setUTCDate(day.getUTCDate() + 1); params.set('until', careUtc(`${day.toISOString().slice(0, 10)}T00:00`)); }
      const result = await careRequest(`/journal?${params}`);
      if (generation !== request.current) return;
      setItems(old => nextPage ? [...old, ...result.items] : result.items); setPage(nextPage); setMore(result.hasMore);
    } catch (e) { if (generation === request.current) setError(e.message); }
    finally { if (generation === request.current) setBusy(false); }
  }, [plantId, query, action, date]);
  useEffect(() => {
    let active = true;
    const sequence = plantRequest;
    reloadPlant().catch(e => { if (active) setError(e.message); });
    return () => { active = false; sequence.current++; };
  }, [reloadPlant]);
  useEffect(() => { const sequence = request; const timer = setTimeout(() => load(), 250); return () => { clearTimeout(timer); sequence.current++; }; }, [load]);
  async function changed() { await Promise.all([reloadPlant(), load()]); }
  async function remove(id) { if (!window.confirm(translateApp('Удалить запись и её фотографии?'))) return; try { await careRequest(`/entries/${id}`, 'DELETE'); await changed(); } catch (e) { setError(e.message); } }
  return <div className="care-page ym-hide-content">
    <div className="care-topline"><Link to="/app/plants/">← {translateApp('Мои растения')}</Link><Button onClick={() => downloadPlantJournalMarkdown(plantId, token).catch(e => setError(e.message))}>{translateApp('Скачать журнал')}</Button></div>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {plant && <section className="care-hero"><CarePhoto id={cover} alt={plant.name} className="care-hero-photo" /><div><span className="care-kicker">{plant.location_label || plant.zone?.name || translateApp('Мой зелёный уголок')}</span><h1>{plant.name}</h1>{plant.strain && <p>{plant.strain}</p>}{plant.description && <p>{plant.description}</p>}<small>{plant.planted_at ? `${translateApp('Дата посадки')}: ${formatDateLong(plant.planted_at)}` : translateApp('Дата посадки не указана')}</small></div><div className="care-actions"><Button onClick={() => setEditPlant(true)}>{translateApp('О растении')}</Button><Link to="/app/settings/notifications/">{translateApp('Напоминания в Telegram')}</Link></div></section>}
    <div className="care-quick-actions">{CARE_ACTIONS.filter(([key]) => ['watering', 'photo', 'note', 'fertilizing'].includes(key)).map(([key, label, emoji]) => <Button key={key} onClick={() => setEditor({ action: key })}>{emoji} {translateApp(label)}</Button>)}</div>
    <CareReminders plantId={plantId} reminders={reminders} onChanged={changed} />
    <div className="care-section-heading"><h2>{translateApp('История растения')}</h2><Button variant="primary" onClick={() => setEditor({ action: 'note' })}>{translateApp('Добавить запись')}</Button></div>
    <div className="care-filters"><input aria-label={translateApp('Поиск в журнале')} placeholder={translateApp('Найти в записях…')} value={query} onChange={e => setQuery(e.target.value)} /><select aria-label={translateApp('Вид ухода')} value={action} onChange={e => setAction(e.target.value)}><option value="">{translateApp('Все действия')}</option>{CARE_ACTIONS.map(([key, label]) => <option key={key} value={key}>{translateApp(label)}</option>)}<option value="automatic">{translateApp('Полив оборудованием')}</option></select><input type="date" aria-label={translateApp('Дата')} value={date} onInput={e => setDate(e.target.value)} onChange={e => setDate(e.target.value)} />{date && <Button onClick={() => setDate('')}>{translateApp('Все даты')}</Button>}</div>
    {busy && <p role="status">{translateApp('Загрузка...')}</p>}
    {!busy && !items.length && <section className="care-empty"><span>🌱</span><h3>{translateApp(query || action || date ? 'По этому запросу записей нет' : 'История начинается с первого наблюдения')}</h3><p>{translateApp('Сфотографируйте растение или запишите, как оно себя чувствует.')}</p></section>}
    <div className="care-timeline">{items.map(item => { const entry = item.entry; const config = careAction(item.action || entry.type); return <article className="care-entry" key={entry.id}><div className="care-entry-heading"><span>{config[2]} {translateApp(item.editable ? config[1] : 'Полив оборудованием')}</span><time>{formatDateLong(entry.eventAt)} · {formatTimeHHMM(entry.eventAt)}</time></div>
      {entry.wateringDetails ? <JournalEntryCard entry={{ ...entry, event_at: entry.eventAt, watering_details: { water_volume_l: entry.wateringDetails.waterVolumeL, duration_s: entry.wateringDetails.durationS, mode: entry.wateringDetails.mode, completion_reason: entry.wateringDetails.completionReason, volume_source: entry.wateringDetails.volumeSource, fertilizers_per_liter: entry.wateringDetails.fertilizersPerLiter } }} photoCache={{}} /> : <p className="care-entry-text">{entry.text}</p>}
      {!!entry.photos?.length && <div className="care-entry-photos">{entry.photos.filter(p => p.hasData).map(p => <CarePhoto preview key={p.id} id={p.id} alt={plant?.name || translateApp('Фото')} />)}</div>}
      {item.editable && <div className="care-actions"><button onClick={() => setEditor({ item })}>{translateApp('Редактировать')}</button><button onClick={() => remove(entry.id)}>{translateApp('Удалить')}</button></div>}
    </article>; })}</div>
    {more && <Button disabled={busy} onClick={() => load(page + 1)}>{translateApp('Показать ещё')}</Button>}
    {editor && <CareEntryEditor plantId={plantId} {...editor} onClose={() => setEditor(null)} onSaved={async () => { setNotice(translateApp('Запись сохранена')); try { await changed(); } catch (e) { setError(e.message); } }} />}
    <PlantEditDialog isOpen={editPlant} mode="edit" plant={plant} zones={plant?.zone ? [plant.zone] : []} onClose={() => setEditPlant(false)} onSaved={() => reloadPlant().catch(e => setError(e.message))} />
  </div>;
}
export default AppPlantJournal;
