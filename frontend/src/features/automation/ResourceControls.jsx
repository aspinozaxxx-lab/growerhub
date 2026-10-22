import { useState } from 'react';
import { Clock3, SlidersHorizontal } from 'lucide-react';
import Button from '../../components/ui/Button';
import { getCurrentLocale, translateApp as t } from '../../locales/i18n';
import { SCENARIO_LABELS, SLOT_ROLE_LABELS } from '../farm/farmModel';
import useResourceControls from './useResourceControls';
import './ResourceControls.css';

const switchRoles = new Set(['LIGHT_SWITCH', 'EXHAUST_SWITCH', 'AC_SWITCH']);
const states = { loading: 'Проверяем управление…', pending: 'Ждём подтверждения устройства', active: 'Устройство подтвердило состояние', blocked: 'Уступает более важному сценарию', error: 'Устройство не подтвердило команду', idle: 'Нет активного управления' };
const time = value => value ? new Date(value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`)
  .toLocaleTimeString(getCurrentLocale(), { hour: '2-digit', minute: '2-digit' }) : '';
const isManual = demand => demand.sourceType === 'manual';
const sourceLabel = value => t(value === 'ROOM_CLIMATE' ? 'Климат фермы' : SCENARIO_LABELS[value] || value);
const reasonLabel = value => {
  const temperature = /^Температура (-?[\d.]+) °C$/.exec(value || '');
  return temperature ? t('Температура {{temperature}} °C', { temperature: temperature[1] }) : t(value || '');
};
const stateOf = (value, resource) => {
  const text = String(value).toLowerCase();
  if (value != null && text === String(resource.on_value ?? 'ON').toLowerCase()) return true;
  if (value != null && text === String(resource.off_value ?? 'OFF').toLowerCase()) return false;
  return value === true || text === 'on' ? true : value === false || text === 'off' ? false : null;
};
const valueLabel = (value, resource) => stateOf(value, resource) === true ? t('Включено') : stateOf(value, resource) === false ? t('Выключено') : t('Нет данных');

function ResourceControlRow({ resource, control, manager }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('ON');
  const [minutes, setMinutes] = useState('15');
  const name = t(SLOT_ROLE_LABELS[resource.role] || resource.role);
  const manual = control?.demands?.find(isManual);
  const disabled = manager.busy !== null;
  const status = control?.status || 'loading';
  const open = () => { setValue(stateOf(control?.actualValue ?? resource.current_value, resource) === true ? 'OFF' : 'ON'); setEditing(true); };
  const submit = async event => {
    event.preventDefault();
    if (await manager.set(resource.id, value, Number(minutes) * 60)) setEditing(false);
  };

  return <li className={`resource-control-row${status === 'error' ? ' is-error' : ''}`}>
    <div className="resource-control-summary"><div><strong>{name}</strong><span>{resource.label}</span></div>
      <span className="resource-control-actual">{valueLabel(control?.actualValue ?? resource.current_value, resource)}</span></div>
    <p className="resource-control-owner">{sourceLabel(control?.sourceName || states[status])}
      {control?.expiresAt && <span><Clock3 size={13} aria-hidden="true" />{t('До {{time}}', { time: time(control.expiresAt) })}</span>}</p>
    {control && status !== 'idle' && <small className="resource-control-detail">{t(states[status] || status)}{control.reason ? ` · ${reasonLabel(control.reason)}` : ''}</small>}
    {control?.error && <p role="alert">{t(control.error)}</p>}
    {control?.demands?.length > 1 && <details className="resource-control-demands"><summary>{t('Другие требования')}: {control.demands.length - 1}</summary>
      <ul>{control.demands.filter(d => d.sourceId !== control.sourceId || d.sourceType !== control.sourceType).map(d => <li key={d.id || `${d.sourceType}:${d.sourceId}`}>
        <span>{sourceLabel(d.sourceName)} · {valueLabel(d.desiredValue, resource)}</span><small>{t('Приоритет')}: {d.priority}{d.expiresAt ? ` · ${t('До {{time}}', { time: time(d.expiresAt) })}` : ''}</small>
      </li>)}</ul></details>}
    <div className="resource-control-actions"><Button size="sm" disabled={disabled || !control || resource.ready === false} onClick={open}>{t('Изменить на время')}</Button>
      {manual && <Button size="sm" disabled={disabled} onClick={() => manager.release(resource.id, manual)}>{t('Снять ручное управление')}</Button>}</div>
    {editing && <form className="resource-control-editor" onSubmit={submit}>
      <p>{t('Временно управлять устройством «{{name}}»', { name })}</p>
      <label>{t('Состояние')}<select value={value} disabled={disabled} onChange={e => setValue(e.target.value)}>
        <option value="ON">{t('Включить')}</option><option value="OFF">{t('Выключить')}</option></select></label>
      <label>{t('На сколько минут')}<input type="number" min="1" max="60" required value={minutes} disabled={disabled} onChange={e => setMinutes(e.target.value)} /></label>
      <small>{t('На это время ручное управление важнее сценариев. Затем GrowerHub заново оценит их условия.')}</small>
      <div><Button type="submit" variant="primary" size="sm" disabled={disabled}>{t('Подтвердить')}</Button><Button size="sm" disabled={disabled} onClick={() => setEditing(false)}>{t('Отмена')}</Button></div>
    </form>}
  </li>;
}

export default function ResourceControls({ resources, refreshKey, scopeName }) {
  const switches = (resources || []).filter(resource => switchRoles.has(resource.role));
  const manager = useResourceControls(switches.length ? refreshKey : null);
  if (!switches.length) return null;
  return <section className="resource-controls ym-hide-content" aria-label={t('Кто управляет: {{name}}', { name: scopeName })}>
    <header><SlidersHorizontal size={18} aria-hidden="true" /><h2>{t('Кто сейчас управляет')}</h2><span>{scopeName}</span></header>
    {manager.error && <div className="resource-controls-error" role="alert">{manager.error}<Button size="sm" disabled={manager.busy !== null} onClick={manager.refresh}>{t('Проверить состояние')}</Button></div>}
    <ul>{switches.map(resource => <ResourceControlRow key={resource.id} resource={resource} control={manager.controls.find(c => c.resourceId === resource.id)} manager={manager} />)}</ul>
  </section>;
}
