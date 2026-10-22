import { Link } from 'react-router-dom';
import { ChevronRight, Droplets, MessageSquarePlus, Workflow } from 'lucide-react';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';
import { translateApp as t } from '../../locales/i18n';
import RuleExplanation from './RuleExplanation';
import './assistant.css';

function scheduleLabel(trigger) {
  if (trigger.type === 'calendar') return `${t('Каждые')} ${trigger.every} ${t(trigger.unit === 'week' ? 'нед.' : 'дн.')} · ${trigger.at}`;
  if (trigger.type === 'schedule') return `${trigger.at}${!trigger.days || trigger.days.length === 7 ? ` · ${t('Каждый день')}` : ''}`;
  if (trigger.type === 'interval' || trigger.type === 'repeat_while') {
    const unit = trigger.seconds % 3600 === 0 ? 3600 : trigger.seconds % 60 === 0 ? 60 : 1;
    return `${t('Каждые')} ${trigger.seconds / unit} ${t(unit === 3600 ? 'ч' : unit === 60 ? 'мин' : 'с')}${trigger.type === 'repeat_while' ? ` · ${t('Пока выполняется условие')}` : ''}`;
  }
  return t('При изменении показаний');
}

export default function RuleCards({ rules, scopeType, scopeId, scopeName, onOpen, onToggle, busy, error, errorRuleId }) {
  const scoped = rules.filter(r => r.definition.scopeType === scopeType && r.definition.scopeId === scopeId);
  const title = scopeType === 'ROOM' ? t('Автоматизации фермы') : t('Свои сценарии');
  return <section className="automations-rules-panel ym-hide-content" aria-label={`${title}: ${scopeName}`}>
    <header><h2>{title}<span> · {scopeName}</span></h2><Link className="automations-rules-create" to="/app/assistant/"
      aria-label={t('Создать сценарий: {{name}}', { name: scopeName })}
      state={{ prompt: `${t(scopeType === 'ROOM' ? 'Помоги создать сценарий для всей фермы' : 'Помоги создать сценарий для теплицы')} «${scopeName}». ${t('Сначала уточни, что я хочу автоматизировать.')}` }}>
      <MessageSquarePlus size={16} aria-hidden="true" />{t('Создать')}</Link></header>
    {scoped.length > 0 && <ul>{scoped.map(rule => {
      const Icon = rule.definition.actions.some(a => a.type === 'water') ? Droplets : Workflow;
      return <li key={rule.id} id={`rule-${rule.id}`}><div className="automations-rule-line"><button type="button" className="automations-rule-row" disabled={busy}
        aria-label={t('Открыть сценарий «{{name}}»', { name: rule.definition.name })} onClick={() => onOpen(rule)}>
        <Icon size={19} aria-hidden="true" /><span className="automations-rule-name"><strong>{rule.definition.name}</strong><small>{scheduleLabel(rule.definition.trigger)}</small></span>
        <ChevronRight size={16} aria-hidden="true" />
      </button><button type="button" role="switch" className="automation-switch" disabled={busy}
        aria-label={t('Сценарий: {{name}}', { name: rule.definition.name })} aria-checked={rule.enabled}
        title={t(rule.enabled ? 'Выключить' : 'Включить')} onClick={() => onToggle(rule)}><span aria-hidden="true" /></button></div>
        {errorRuleId === rule.id && error && <p className="automations-rule-error" role="alert">{error}</p>}</li>;
    })}</ul>}
  </section>;
}

export function RuleDetails({ manager, farms, onClose, compact }) {
  const { selected, preview, history, versions, error, busy, detailLoading } = manager;
  if (!selected) return null;
  const d = selected.definition;
  const resourceNames = Object.fromEntries(farms.flatMap(f => [...(f.slots || []), ...(f.greenhouses || []).flatMap(g => g.slots || [])]).map(r => [r.id, r.label]));
  const scopeName = d.scopeType === 'ROOM' ? farms.find(f => f.id === d.scopeId)?.name : farms.flatMap(f => f.greenhouses || []).find(g => g.id === d.scopeId)?.name;
  return <Modal isOpen title={d.name} onClose={onClose} presentation={compact ? 'sheet' : 'dialog'}>
    <div className="assistant-rule-details ym-hide-content"><p className="automations-rule-status">{t(selected.enabled ? 'Включён' : 'Выключен')}</p>
      <RuleExplanation definition={d} resourceNames={resourceNames} scopeName={scopeName} timing={preview?.timing} coordination={preview?.coordination} />
      {detailLoading && <p role="status">{t('Проверяем сценарий…')}</p>}
      {error && <p role="alert">{error}</p>}
      {preview && <section><h3>{t('Предварительная проверка')}</h3>{[...preview.conflicts, ...preview.readiness].map((v, i) => <p key={i} role="status">{t(v)}</p>)}
        {!preview.conflicts.length && !preview.readiness.length && <p>{t('Устройства готовы, конфликтов нет')}</p>}</section>}
      <div className="assistant-actions">
        {selected.enabled ? <Button disabled={busy} onClick={() => manager.toggle(false)}>{t('Выключить')}</Button>
          : <Button variant="primary" disabled={busy || detailLoading || !preview || preview.conflicts.length > 0 || preview.readiness.length > 0} onClick={() => manager.toggle(true)}>{t('Подтвердить и включить')}</Button>}
        <Link to="/app/assistant/" state={{ prompt: `${t('Помоги изменить мой сценарий')} «${d.name}»${scopeName ? ` (${scopeName})` : ''}.` }}>{t('Изменить с помощником')}</Link>
      </div>
      <section><h3>{t('Последние запуски')}</h3>{!detailLoading && !history.length && <p>{t('Запусков пока нет')}</p>}
        <ul>{history.map(r => <li key={r.id}>{r.startedAt} UTC · {r.status === 'partial' ? `${t('Выполнено частично')} · ` : ''}{t(r.reason)}</li>)}</ul></section>
      <section><h3>{t('Предыдущие версии')}</h3>{versions.filter(v => v.revision !== selected.revision).map(v => <p key={v.revision}>{t('Версия')} {v.revision} · {v.definition.name} <Button size="sm" disabled={busy} onClick={() => manager.restore(v.revision)}>{t('Восстановить выключенной')}</Button></p>)}</section>
    </div>
  </Modal>;
}
