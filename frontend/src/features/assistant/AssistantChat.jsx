import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Camera, Settings2, ArrowLeft, ArrowUp, MessageSquarePlus, RefreshCw, Square, X } from 'lucide-react';
import Button from '../../components/ui/Button';
import { translateApp as t } from '../../locales/i18n';
import { assistantPhoto } from '../../api/assistant';
import useAssistant from './useAssistant';
import RuleExplanation from './RuleExplanation';
import AssistantText from './AssistantText';
import { demoPrompts } from './demoPrompts';
import { careAction } from '../care/careModel';
import { formatDateTimeDDMMYYYY } from '../../utils/formatters';
import { getPlantAvatarOptions } from '../../domain/plants';
import { getCurrentLocale } from '../../locales/i18n';
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
  const plantId = item.kind === 'plant' ? item.result?.id : item.kind === 'care' ? item.result?.entry?.plantId : null;
  return <section className="assistant-proposal">
    <strong>{item.title}</strong>
    {item.kind === 'device' && <p>{p.type === 'water' ? `${t('Полив')}: ${p.durationSeconds} ${t('с')}` : `${t('Состояние')}: ${p.value}`}</p>}
    {item.kind === 'device' && p.type === 'set' && p.ttlSeconds > 0 && <p>{p.ttlSeconds % 60 === 0
      ? t('Ручное управление на {{minutes}} мин', { minutes: p.ttlSeconds / 60 })
      : t('Ручное управление на {{seconds}} с', { seconds: p.ttlSeconds })}</p>}
    {item.kind === 'reminder' && <p>{p.title} · {p.dueAt} UTC</p>}
    {item.kind === 'plant' && <PlantChange payload={p} />}
    {item.kind === 'care' && <div className="assistant-change-summary"><p><strong>{p.plantName}</strong> · {t(careAction(p.action)[1])}</p>
      {p.eventAt && <p>{formatDateTimeDDMMYYYY(p.eventAt)}</p>}<p>{p.text}</p>
      {p.photoIds?.length > 0 && <><p>{t('Фото в журнал: {{count}}', { count: p.photoIds.length })}</p><div className="assistant-photos">{p.photoIds.map(id => <Photo key={id} id={id} />)}</div></>}
    </div>}
    {item.kind === 'feature_request' && <><p>{p.description}</p><p><strong>{t('Зачем это нужно:')}</strong> {p.benefit}</p><small>{t('Команда получит этот текст и номер вашего аккаунта. Решение о доработке появится после рассмотрения.')}</small></>}
    {['rule', 'scenario'].includes(item.kind) && <p>{t(item.status === 'applied' ? (p.enabled ? 'Сохранён включённым' : 'Сохранён выключенным') : (p.enabled ? 'Начнёт работать при ближайшем выполнении условий' : 'Сохранится выключенным'))}</p>}
    {item.kind === 'rule' && <RuleExplanation definition={p.definition} resourceNames={p.resourceNames} scopeName={p.scopeName} timing={p.timing} coordination={p.coordination} />}
    {item.status === 'pending' && <small>{t(item.kind === 'feature_request' ? 'Запрос отправится только после вашего подтверждения.' : 'Изменение начнётся только после вашего подтверждения.')}</small>}
    {item.kind === 'scenario' && <details><summary>{t('Настройки сценария')}</summary><dl>{Object.entries(p.config || {}).map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{String(v)}</dd></div>)}</dl></details>}
    <p role="status">{t(item.kind === 'feature_request' && item.status === 'applied' ? 'Запрос передан команде GrowerHub на рассмотрение.' : item.kind === 'device' && item.status === 'applied' ? 'Команда передана устройству. Фактическое состояние смотрите на ферме.' : status[item.status] || item.status)}</p>
    {item.kind === 'rule' && item.status === 'applied' && item.result?.id && <Link className="gh-btn gh-btn--secondary" to={`/app/automations/?rule=${encodeURIComponent(item.result.id)}`}>{t('Открыть сценарий')}</Link>}
    {plantId && item.status === 'applied' && <Link className="gh-btn gh-btn--secondary" to={`/app/plants/${encodeURIComponent(plantId)}/journal/`}>{t('Открыть журнал растения')}</Link>}
    {item.status === 'pending' && <div className="assistant-actions"><Button variant="primary" disabled={busy} onClick={() => onAction(item, false)}>{t(item.kind === 'feature_request' ? 'Отправить запрос' : 'Подтвердить')}</Button><Button disabled={busy} onClick={() => onAction(item, true)}>{t(item.kind === 'feature_request' ? 'Не отправлять' : 'Не применять')}</Button></div>}
  </section>;
}
function PlantChange({ payload: p }) {
  const labels = { name: 'Название', plantType: 'Вид', strain: 'Сорт', growthStage: 'Стадия', description: 'Описание', locationLabel: 'Место', plantedAt: 'Дата посадки' };
  const pictureName = fields => {
    const avatar = getPlantAvatarOptions(getCurrentLocale()).find(item => item.value === fields?.avatarKey);
    return avatar ? `${avatar.label} · ${(avatar.stages.find(stage => stage.value === fields?.avatarStage) || avatar.stages.at(-1)).label}` : t('Автоматически');
  };
  return <div className="assistant-change-summary"><dl>{Object.entries(p.fields || {}).filter(([key]) => labels[key]).map(([key, value]) => <div key={key}>
    <dt>{t(labels[key])}</dt><dd>{p.operation === 'update' && <>{p.before?.[key] ? (key === 'plantedAt' ? formatDateTimeDDMMYYYY(p.before[key]) : p.before[key]) : t('Не указано')} → </>}{key === 'plantedAt' ? formatDateTimeDDMMYYYY(value) : value || t('Не указано')}</dd>
  </div>)}{p.fields?.dateUnknown && <div><dt>{t('Дата посадки')}</dt><dd>{t('Неизвестна')}</dd></div>}
    {p.pictureName && <div><dt>{t('Картинка растения')}</dt><dd>{p.operation === 'update' && <>{pictureName(p.before)} → </>}{pictureName({ ...p.before, ...p.fields })}</dd></div>}
    {p.zoneChanged && <div><dt>{t('Теплица')}</dt><dd>{p.operation === 'update' && <>{p.previousZoneName || t('Без теплицы')} → </>}{p.zoneName || t('Без теплицы')}</dd></div>}
  </dl></div>;
}
export default function AssistantChat({ initialText = '', backTo, onClose }) {
  const a = useAssistant(initialText); const [settingsOpen, setSettingsOpen] = useState(false);
  const messagesRef = useRef(null); const textareaRef = useRef(null); const followEnd = useRef(true); const previousCount = useRef(0);
  useEffect(() => { followEnd.current = true; previousCount.current = 0; }, [a.chatId]);
  useEffect(() => {
    const element = messagesRef.current;
    if (element && (followEnd.current || previousCount.current !== a.messages.length)) element.scrollTop = a.messages.length ? element.scrollHeight : 0;
    previousCount.current = a.messages.length;
  }, [a.messages]);
  useEffect(() => {
    const element = textareaRef.current;
    if (element) { element.style.height = 'auto'; element.style.height = Math.min(element.scrollHeight || 48, 144) + 'px'; }
  }, [a.text]);
  const cap = a.capabilities; const prefs = cap?.preferences;
  const selectedModel = cap?.models?.find(m => m.id === a.options.model);
  const locked = a.busy || !!a.active || !!a.pending;
  const canSend = !!cap?.available && !a.busy && a.retrySeconds === 0 && (!!a.pending || !!a.text.trim() || !!a.files.length) && !a.files.some(f => f.size > 6 * 1024 * 1024);
  const pickPrompt = text => { a.setText(t(text)); textareaRef.current?.focus(); };
  const examples = <div className="assistant-suggestions assistant-demo-prompts">{demoPrompts.map(p => <button type="button" key={p.id} disabled={locked} onClick={() => pickPrompt(p.text)}><span aria-hidden="true">{p.icon}</span><span><strong>{t(p.title)}</strong><small>{t(p.hint)}</small></span></button>)}</div>;
  return <section className="assistant-chat ym-hide-content ym-disable-keys" aria-label={t('Помощник GrowerHub')}
    onClick={event => { if (!event.ctrlKey && !event.metaKey && !event.shiftKey && event.target.closest('a[href]')) onClose?.(); }}>
    <header className="assistant-toolbar">
      {backTo && <Link className="assistant-icon-button" to={backTo} aria-label={t('Вернуться к ферме')} title={t('Вернуться к ферме')}><ArrowLeft size={19} aria-hidden="true" /></Link>}
      <h1>{t('Помощник')}{cap?.demo && <small>{t('Демо')}</small>}</h1>
      <label className="assistant-conversation"><span className="sr-only">{t('Разговор')}</span><select value={a.chatId || ''} disabled={!cap || locked} onChange={e => a.choose(e.target.value)}>
        {!a.chatId && <option value="">{t('Новый разговор')}</option>}{a.chats.map(c => <option key={c.id} value={c.id}>{t(c.title)}</option>)}
      </select></label>
      <button type="button" className="assistant-icon-button" onClick={a.create} disabled={!cap || locked} aria-label={t('Новый разговор')} title={t('Новый разговор')}><MessageSquarePlus size={19} aria-hidden="true" /></button>
      {cap && !cap.demo && <button type="button" className="assistant-icon-button" aria-expanded={settingsOpen} aria-label={t('Настройки помощника')} title={t('Настройки помощника')} onClick={() => setSettingsOpen(!settingsOpen)}><Settings2 size={19} aria-hidden="true" /></button>}
      {onClose && <button type="button" className="assistant-icon-button" aria-label={t('Закрыть')} title={t('Закрыть')} onClick={onClose}><X size={19} aria-hidden="true" /></button>}
    </header>
    {a.error && <p className="assistant-error" role="alert">{a.error}</p>}
    {!cap ? <p role="status">{t('Загрузка…')}</p> : !cap.enabled ? <p>{t('Помощник ещё настраивается')}</p> : <>
      {settingsOpen && prefs && <form className="assistant-settings" onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); a.preferences({ telegram: f.get('telegram') === 'on', report: f.get('report'), hour: Number(f.get('hour')), weekday: Number(f.get('weekday')) }); }} key={JSON.stringify(prefs)}>
        <Link to="/app/settings/notifications/">{t('Привязать Telegram')}</Link>
        <label><input type="checkbox" name="telegram" defaultChecked={prefs.telegram} /> {t('Общаться с помощником в Telegram')}</label>
        <label>{t('Сводка фермы')}<select name="report" defaultValue={prefs.report}><option value="off">{t('По запросу')}</option><option value="daily">{t('Каждый день')}</option><option value="weekly">{t('Раз в неделю')}</option></select></label>
        <div className="assistant-models"><label>{t('Час отправки')}<input type="number" name="hour" min="0" max="23" defaultValue={prefs.hour} required /></label><label>{t('День недели (1 — понедельник)')}<input type="number" name="weekday" min="1" max="7" defaultValue={prefs.weekday} required /></label></div>
        <small>{t('Время — по часовому поясу аккаунта. Сводка сохраняется здесь и приходит в подключённый Telegram.')}</small><Button type="submit" disabled={a.busy}>{t('Сохранить')}</Button>
        {cap.owner && <><button type="button" className="assistant-refresh-models" disabled={locked} onClick={a.refreshModels}><RefreshCw size={15} aria-hidden="true" />{t('Обновить список моделей')}</button><small>{t('Выбор сохраняется для этого разговора, включая Telegram.')}</small></>}
      </form>}
      <div className="assistant-messages" ref={messagesRef} onScroll={e => { const el = e.currentTarget; followEnd.current = el.scrollHeight - el.clientHeight - el.scrollTop < 64; }} aria-label={t('История разговора')}>
        {!a.messages.length && <div className="assistant-welcome"><span aria-hidden="true">🌱</span><h2>{t(cap.demo ? 'Попробуем вместе?' : 'С чего начнём?')}</h2>{cap.demo ? <>{examples}<p>{t('Выберите задачу, прочитайте готовый вопрос и нажмите «Отправить». Данные и устройства здесь учебные.')}</p><small>{t('В демо сценарий можно сохранить; уведомления в настоящий Telegram не отправляются.')}</small></> : <><div className="assistant-suggestions">{['Что сейчас с моей фермой?', 'Помоги составить расписание света', 'На что обратить внимание в уходе?'].map(q => <button type="button" key={q} onClick={() => pickPrompt(q)}>{t(q)}</button>)}</div><p>{t('Можно приложить фото растения. Помощник предложит изменения, а решение останется за вами.')}</p></>}</div>}
        {a.messages.map(m => <article key={m.id} className="assistant-exchange"><div className="assistant-question"><small className="sr-only">{t('Вы')}</small><p>{m.text}</p><div className="assistant-photos">{m.attachments?.map(id => <Photo key={id} id={id} />)}</div></div><div className="assistant-answer"><small>{t('Помощник')}</small><AssistantText text={m.answer} />{m.status !== 'completed' && <span className="assistant-status" role={['queued', 'running'].includes(m.status) ? 'status' : undefined}>{t(status[m.status] || m.status)}</span>}{m.error && <p>{t('Попробуйте задать вопрос короче или открыть новый разговор.')}</p>}{m.proposals?.map(p => <Proposal key={p.id} item={p} onAction={a.proposal} busy={a.busy} />)}</div></article>)}
        {cap.demo && a.messages.length > 0 && <details className="assistant-demo-more"><summary>{t('Попробовать другую задачу')}</summary>{examples}</details>}
      </div>
      <form className="assistant-compose" onSubmit={e => { e.preventDefault(); if (canSend && !a.active) a.send(); }}>
        <label className="sr-only" htmlFor="assistant-text">{t('Ваш вопрос')}</label><textarea ref={textareaRef} id="assistant-text" rows="1" maxLength={6000} placeholder={t('Напишите, с чем нужна помощь…')} value={a.text} disabled={!!a.pending || a.busy} onChange={e => a.setText(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing && !a.active && canSend) { e.preventDefault(); a.send(); } }} />
        {a.files.length > 0 && <div className="assistant-files">{a.files.map((f, i) => <span key={`${f.name}:${i}`}>{f.name}<button type="button" disabled={a.busy || !!a.pending} aria-label={t('Удалить фото')} onClick={() => a.setFiles(a.files.filter((_, n) => i !== n))}>×</button></span>)}</div>}
        <div className="assistant-compose-tools">
          <label className="assistant-upload" title={t('До 2 фото, каждое до 6 МБ')}><Camera size={20} aria-hidden="true" /><span className="sr-only">{t('Фото')}</span><input aria-label={t('Приложить фото')} type="file" accept="image/jpeg,image/png" multiple disabled={locked} onChange={e => { a.setFiles(Array.from(e.target.files || []).slice(0, 2)); e.target.value = ''; }} /></label>
          {cap.owner && <div className="assistant-composer-models">
            <label><span className="sr-only">{t('Модель')}</span><select disabled={locked} aria-label={t('Модель')} title={selectedModel?.name || a.options.model} value={a.options.model || ''} onChange={e => { const m = cap.models.find(m => m.id === e.target.value); a.configure({ model: m.id, effort: m.efforts.includes(a.options.effort) ? a.options.effort : m.defaultEffort }); }}>{cap.models.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}</select></label>
            <label><span className="sr-only">{t('Усилие')}</span><select disabled={locked} aria-label={t('Усилие')} title={t('Усилие')} value={a.options.effort || ''} onChange={e => a.configure({ ...a.options, effort: e.target.value })}>{selectedModel?.efforts.map(e => <option key={e} value={e}>{e}</option>)}</select></label>
          </div>}
          {a.active ? <button type="button" className="assistant-send is-stop" onClick={a.cancel} disabled={a.busy} aria-label={t('Остановить')} title={t('Остановить')}><Square size={17} aria-hidden="true" /></button> : <button type="submit" className="assistant-send" disabled={!canSend} aria-label={t(a.pending ? 'Проверить отправку' : 'Отправить')} title={t(a.pending ? 'Проверить отправку' : 'Отправить')}><ArrowUp size={21} aria-hidden="true" /></button>}
        </div>
        <small className="assistant-compose-hint">{a.retrySeconds > 0 ? t('Повторить через {{seconds}} с', { seconds: a.retrySeconds }) : t('Осталось сообщений за сутки: {{count}}', { count: cap.limits.remainingDay })} · {t('До 2 фото, каждое до 6 МБ')}</small>
        {a.files.some(f => f.size > 6 * 1024 * 1024) && <p role="alert">{t('Уменьшите фото до 6 МБ')}</p>}
        {!cap.available && <p role="status">{t('Помощник временно недоступен. Ферма и сценарии продолжают работать.')}</p>}
      </form>
    </>}
  </section>;
}
