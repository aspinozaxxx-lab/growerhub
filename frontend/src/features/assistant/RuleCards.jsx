import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ruleRequest } from '../../api/assistant';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';
import { translateApp as t } from '../../locales/i18n';
import RuleExplanation from './RuleExplanation';
import './assistant.css';
export default function RuleCards({ onChange, refreshKey, farms = [] }) {
  const [rules, setRules] = useState([]); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const [selected, setSelected] = useState(null); const [versions, setVersions] = useState([]); const [history, setHistory] = useState([]); const [preview, setPreview] = useState(null);
  const resourceNames = Object.fromEntries(farms.flatMap(f => [...(f.slots || []), ...(f.greenhouses || []).flatMap(g => g.slots || [])]).map(r => [r.id, r.label]));
  const scopeName = d => d.scopeType === 'ROOM' ? farms.find(f => f.id === d.scopeId)?.name : farms.flatMap(f => f.greenhouses || []).find(g => g.id === d.scopeId)?.name;
  const load = useCallback(async () => { const rows = await ruleRequest(); setRules(rows); onChange?.(rows.some(r => r.enabled)); }, [onChange]);
  useEffect(() => { load().catch(e => setError(e.message)); }, [load, refreshKey]);
  async function run(action) { if (busy) return; setBusy(true); setError(''); try { await action(); await load(); } catch (e) { setError(e.message); } finally { setBusy(false); } }
  async function inspect(rule) {
    setSelected(rule); setVersions([]); setHistory([]); setPreview(null);
    await run(async () => { const [v, h, p] = await Promise.all([ruleRequest(`/${rule.id}/versions`), ruleRequest(`/${rule.id}/history`), ruleRequest(`/preview?id=${rule.id}`, 'POST', rule.definition)]); setVersions(v); setHistory(h); setPreview(p); });
  }
  return <section className="assistant-rules ym-hide-content"><header><div><h2>{t('Мои сценарии')}</h2><p>{t('Опишите свою задачу помощнику — он подготовит сценарий для проверки.')}</p></div><Link className="gh-btn gh-btn--secondary" to="/app/assistant/" state={{ prompt: t('Помоги создать сценарий для моей фермы. Сначала уточни, что я хочу автоматизировать.') }}>{t('Создать с помощником')}</Link></header>
    {error && <p role="alert" className="assistant-error">{error}</p>}
    {rules.map(rule => <article key={rule.id} className="assistant-rule"><h3>{rule.definition.name}</h3><span>{t(rule.enabled ? 'Включён' : 'Выключен')} · {t('Версия')} {rule.revision}</span><p>{t(rule.reason)}</p><RuleExplanation definition={rule.definition} resourceNames={resourceNames} scopeName={scopeName(rule.definition)} /><div className="assistant-actions"><Button disabled={busy} onClick={() => inspect(rule)}>{t('Проверка и история')}</Button>{rule.enabled && <Button disabled={busy} onClick={() => run(() => ruleRequest(`/${rule.id}/enabled`, 'PUT', { revision: rule.revision, enabled: false }))}>{t('Выключить')}</Button>}<Link to="/app/assistant/" state={{ prompt: `${t('Помоги изменить мой сценарий')} «${rule.definition.name}» (ruleId=${rule.id}).` }}>{t('Изменить с помощником')}</Link></div></article>)}
    {selected && <Modal isOpen title={selected.definition.name} onClose={() => setSelected(null)}><RuleExplanation definition={selected.definition} resourceNames={resourceNames} scopeName={scopeName(selected.definition)} />
      {preview && <><p>{t('Предварительная проверка')}</p>{[...preview.conflicts, ...preview.readiness].map((v, i) => <p key={i} role="status">{v}</p>)}{!preview.conflicts.length && !preview.readiness.length && <p>{t('Устройства готовы, конфликтов нет')}</p>}</>}
      {error && <p role="alert">{error}</p>}
      {!selected.enabled && <Button variant="primary" disabled={busy || !preview || preview.conflicts.length > 0 || preview.readiness.length > 0} onClick={() => run(async () => { await ruleRequest(`/${selected.id}/enabled`, 'PUT', { revision: selected.revision, enabled: true }); setSelected(null); })}>{t('Подтвердить и включить')}</Button>}
      <h3>{t('Последние запуски')}</h3>{!history.length && <p>{t('Запусков пока нет')}</p>}<ul>{history.map(r => <li key={r.id}>{r.startedAt} UTC · {r.reason}</li>)}</ul>
      <h3>{t('Предыдущие версии')}</h3>{versions.filter(v => v.revision !== selected.revision).map(v => <p key={v.revision}>{t('Версия')} {v.revision} · {v.definition.name} <Button size="sm" disabled={busy} onClick={() => run(async () => { await ruleRequest(`/${selected.id}/restore`, 'POST', { revision: selected.revision, sourceRevision: v.revision }); setSelected(null); })}>{t('Восстановить выключенной')}</Button></p>)}
    </Modal>}
  </section>;
}
