import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Heart, Send, Camera, Settings2 } from 'lucide-react';
import Button from '../../components/ui/Button';
import { translateApp as t } from '../../locales/i18n';
import { assistantPhoto } from '../../api/assistant';
import useAssistant from './useAssistant';
import RuleExplanation from './RuleExplanation';
import AssistantText from './AssistantText';
import './assistant.css';

const status = { queued: 'В очереди', running: 'Думаю…', completed: 'Готово', incomplete: 'Ответ остановлен на лимите', failed: 'Ответ недоступен', uncertain: 'Результат неизвестен', cancelled: 'Остановлено', pending: 'Ждёт подтверждения', confirmed: 'Подтверждено', applying: 'Применяется', applied: 'Изменение применено', stale: 'Настройки изменились', rejected: 'Отклонено', expired: 'Предложение устарело' };
function Photo({ id }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    let live = true, objectUrl;
    assistantPhoto(id).then(blob => { if (live) { objectUrl = URL.createObjectURL(blob); setUrl(objectUrl); } }).catch(() => {});
    return () => { live = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [id]);
  return url ? <img src={url} className="assistant-photo" alt={t('Фото растения')} /> : null;
}
function Proposal({ item, onAction, busy }) {
  const p = item.payload || {};
  return <section className="assistant-proposal">
    <strong>{item.title}</strong>
    {item.kind === 'device' && <p>{p.type === 'water' ? `${t('Полив')}: ${p.durationSeconds} ${t('с')}` : `${t('Состояние')}: ${p.value}`}</p>}
    {item.kind === 'reminder' && <p>{p.title} · {p.dueAt} UTC</p>}
    {item.kind === 'care' && <p>{p.text}</p>}
    {['rule', 'scenario'].includes(item.kind) && <p>{t(item.status === 'applied' ? (p.enabled ? 'Сохранён включённым' : 'Сохранён выключенным') : (p.enabled ? 'Начнёт работать при ближайшем выполнении условий' : 'Сохранится выключенным'))}</p>}
    {item.kind === 'rule' && <RuleExplanation definition={p.definition} resourceNames={p.resourceNames} scopeName={p.scopeName} />}
    {item.status === 'pending' && <small>{t('Изменение начнётся только после вашего подтверждения.')}</small>}
    {item.kind === 'scenario' && <details><summary>{t('Настройки сценария')}</summary><dl>{Object.entries(p.config || {}).map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{String(v)}</dd></div>)}</dl></details>}
    <p role="status">{t(item.kind === 'device' && item.status === 'applied' ? 'Команда передана устройству. Фактическое состояние смотрите на ферме.' : status[item.status] || item.status)}</p>
    {item.status === 'pending' && <div className="assistant-actions"><Button variant="primary" disabled={busy} onClick={() => onAction(item, false)}>{t('Подтвердить')}</Button><Button disabled={busy} onClick={() => onAction(item, true)}>{t('Не применять')}</Button></div>}
  </section>;
}
export default function AssistantChat({ initialText = '' }) {
  const a = useAssistant(initialText); const [settingsOpen, setSettingsOpen] = useState(false);
  const messagesRef = useRef(null); const followEnd = useRef(true); const previousCount = useRef(0);
  useEffect(() => {
    const element = messagesRef.current;
    if (element && (followEnd.current || previousCount.current !== a.messages.length)) element.scrollTop = a.messages.length ? element.scrollHeight : 0;
    previousCount.current = a.messages.length;
  }, [a.messages]);
  const cap = a.capabilities; const prefs = cap?.preferences;
  const selectedModel = cap?.models?.find(m => m.id === a.options.model);
  return <section className="assistant-chat ym-hide-content ym-disable-keys" aria-label={t('Помощник GrowerHub')}>
    {!a.messages.length && <header className="assistant-intro"><Heart aria-hidden="true" /><div><h2>{t('Рядом с вашими растениями')}</h2><p>{t('Разберёмся в уходе, проверим ферму и настроим удобный сценарий.')}</p></div></header>}
    {a.error && <p className="assistant-error" role="alert">{a.error}</p>}
    {!cap ? <p role="status">{t('Загрузка…')}</p> : !cap.enabled ? <p>{t('Помощник ещё настраивается')}</p> : <>
      <div className="assistant-toolbar">
        <label className="assistant-conversation"><span className="sr-only">{t('Разговор')}</span><select value={a.chatId || ''} disabled={a.busy || !!a.active || !!a.pending} onChange={e => a.choose(e.target.value)}>
          {!a.chatId && <option value="">{t('Новый разговор')}</option>}{a.chats.map(c => <option key={c.id} value={c.id}>{t(c.title)}</option>)}
        </select></label>
        <Button size="sm" onClick={a.create} disabled={a.busy || !!a.active || !!a.pending}>{t('Новый')}</Button>
        {!cap.demo && <Button size="sm" aria-expanded={settingsOpen} aria-label={t('Настройки помощника')} onClick={() => setSettingsOpen(!settingsOpen)}><Settings2 size={18} /></Button>}
      </div>
      {cap.owner && <div className="assistant-models"><label>{t('Модель')}<select disabled={a.busy || !!a.active || !!a.pending} value={a.options.model || ''} onChange={e => { const m = cap.models.find(m => m.id === e.target.value); a.configure({ model: m.id, effort: m.efforts.includes(a.options.effort) ? a.options.effort : m.defaultEffort }); }}>{cap.models.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}</select></label><label>{t('Усилие')}<select disabled={a.busy || !!a.active || !!a.pending} value={a.options.effort || ''} onChange={e => a.configure({ ...a.options, effort: e.target.value })}>{selectedModel?.efforts.map(e => <option key={e} value={e}>{e}</option>)}</select></label><Button size="sm" disabled={a.busy || !!a.active || !!a.pending} onClick={a.refreshModels}>{t('Обновить список моделей')}</Button><small>{t('Выбор сохраняется для этого разговора, включая Telegram.')}</small></div>}
      {settingsOpen && prefs && <form className="assistant-settings" onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); a.preferences({ telegram: f.get('telegram') === 'on', report: f.get('report'), hour: Number(f.get('hour')), weekday: Number(f.get('weekday')) }); }} key={JSON.stringify(prefs)}>
        <Link to="/app/settings/notifications/">{t('Привязать Telegram')}</Link>
        <label><input type="checkbox" name="telegram" defaultChecked={prefs.telegram} /> {t('Общаться с помощником в Telegram')}</label>
        <label>{t('Сводка фермы')}<select name="report" defaultValue={prefs.report}><option value="off">{t('По запросу')}</option><option value="daily">{t('Каждый день')}</option><option value="weekly">{t('Раз в неделю')}</option></select></label>
        <div className="assistant-models"><label>{t('Час отправки')}<input type="number" name="hour" min="0" max="23" defaultValue={prefs.hour} required /></label><label>{t('День недели (1 — понедельник)')}<input type="number" name="weekday" min="1" max="7" defaultValue={prefs.weekday} required /></label></div>
        <small>{t('Время — по часовому поясу аккаунта. Сводка сохраняется здесь и приходит в подключённый Telegram.')}</small><Button type="submit" disabled={a.busy}>{t('Сохранить')}</Button>
      </form>}
      <div className="assistant-messages" ref={messagesRef} onScroll={e => { const el = e.currentTarget; followEnd.current = el.scrollHeight - el.clientHeight - el.scrollTop < 64; }} aria-label={t('История разговора')}>
        {!a.messages.length && <div className="assistant-welcome"><span aria-hidden="true">🌱</span><h3>{t('С чего начнём?')}</h3><div className="assistant-suggestions">{['Что сейчас с моей фермой?', 'Помоги составить расписание света', 'На что обратить внимание в уходе?'].map(q => <button type="button" key={q} onClick={() => a.setText(t(q))}>{t(q)}</button>)}</div><p>{t('Можно приложить фото растения. Помощник предложит изменения, а решение останется за вами.')}</p></div>}
        {a.messages.map(m => <article key={m.id} className="assistant-exchange"><div className="assistant-question"><small>{t('Вы')}</small><p>{m.text}</p><div className="assistant-photos">{m.attachments?.map(id => <Photo key={id} id={id} />)}</div></div><div className="assistant-answer"><small>{t('Помощник')}</small><AssistantText text={m.answer} /><span className="assistant-status">{t(status[m.status] || m.status)}</span>{m.error && <p>{t('Попробуйте задать вопрос короче или открыть новый разговор.')}</p>}{m.proposals?.map(p => <Proposal key={p.id} item={p} onAction={a.proposal} busy={a.busy} />)}</div></article>)}
      </div>
      <form className="assistant-compose" onSubmit={e => { e.preventDefault(); a.send(); }}>
        <label className="sr-only" htmlFor="assistant-text">{t('Ваш вопрос')}</label><textarea id="assistant-text" rows="2" maxLength={6000} placeholder={t('Напишите, с чем нужна помощь…')} value={a.text} disabled={!!a.pending || a.busy} onChange={e => a.setText(e.target.value)} />
        {a.files.length > 0 && <div className="assistant-files">{a.files.map((f, i) => <span key={`${f.name}:${i}`}>{f.name}<button type="button" disabled={a.busy || !!a.pending} aria-label={t('Удалить фото')} onClick={() => a.setFiles(a.files.filter((_, n) => i !== n))}>×</button></span>)}</div>}
        <div className="assistant-actions"><label className="assistant-upload"><Camera size={20} aria-hidden="true" /><span>{t('Фото')}</span><input type="file" accept="image/jpeg,image/png" multiple disabled={a.busy || !!a.pending || !!a.active} onChange={e => { const files = Array.from(e.target.files || []); a.setFiles(files.slice(0, 2)); e.target.value = ''; }} /></label>
          {a.active ? <Button onClick={a.cancel} disabled={a.busy}>{t('Остановить')}</Button> : <Button type="submit" variant="primary" disabled={a.busy || !cap.available || a.retrySeconds > 0 || (!a.pending && !a.text.trim() && !a.files.length) || a.files.some(f => f.size > 6 * 1024 * 1024)}><Send size={16} aria-hidden="true" />{t(a.pending ? 'Проверить отправку' : 'Отправить')}</Button>}
        </div>
        <small>{a.retrySeconds > 0 ? t('Повторить через {{seconds}} с', { seconds: a.retrySeconds }) : t('Осталось сообщений за сутки: {{count}}', { count: cap.limits.remainingDay })} · {t('До 2 фото, каждое до 6 МБ')}</small>
        {a.files.some(f => f.size > 6 * 1024 * 1024) && <p role="alert">{t('Уменьшите фото до 6 МБ')}</p>}
        {!cap.available && <p role="status">{t('Помощник временно недоступен. Ферма и сценарии продолжают работать.')}</p>}
      </form>
    </>}
  </section>;
}
