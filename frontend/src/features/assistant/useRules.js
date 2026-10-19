import { useCallback, useEffect, useRef, useState } from 'react';
import { ruleRequest } from '../../api/assistant';

export default function useRules({ refreshKey, selectedId, onClose }) {
  const [rules, setRules] = useState([]); const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const [errorRuleId, setErrorRuleId] = useState(null);
  const [preview, setPreview] = useState(null); const [history, setHistory] = useState([]); const [versions, setVersions] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false); const locked = useRef(false); const live = useRef(true);
  const selected = rules.find(r => r.id === selectedId) || null;
  useEffect(() => { live.current = true; return () => { live.current = false; }; }, []);
  const load = useCallback(async () => { const rows = await ruleRequest(); if (live.current) { setRules(rows); setLoaded(true); } }, []);
  useEffect(() => { if (refreshKey) load().catch(e => { if (live.current) setError(e.message); }); }, [refreshKey, load]);
  useEffect(() => {
    let keep = true; setPreview(null); setHistory([]); setVersions([]); setError('');
    if (!selected) { setDetailLoading(false); return undefined; }
    setDetailLoading(true);
    Promise.all([ruleRequest(`/${selected.id}/versions`), ruleRequest(`/${selected.id}/history`), ruleRequest(`/preview?id=${selected.id}`, 'POST', selected.definition)])
      .then(([v, h, p]) => { if (keep) { setVersions(v); setHistory(h); setPreview(p); } })
      .catch(e => { if (keep) setError(e.message); })
      .finally(() => { if (keep) setDetailLoading(false); });
    return () => { keep = false; };
  }, [selected]);
  async function run(action, close = true, ruleId = null) {
    if (locked.current) return;
    locked.current = true; setBusy(true); setError(''); setErrorRuleId(ruleId);
    try { await action(); await load(); if (live.current && close) onClose(); }
    catch (e) {
      if (live.current) setError(e.message);
      if (!close && live.current) await load().catch(() => {});
    }
    finally { locked.current = false; if (live.current) setBusy(false); }
  }
  return { rules, selected, preview, history, versions, detailLoading, loaded, error, errorRuleId, busy,
    missing: loaded && !!selectedId && !selected,
    toggle: enabled => selected && run(() => ruleRequest(`/${selected.id}/enabled`, 'PUT', { revision: selected.revision, enabled })),
    switchRule: rule => run(() => ruleRequest(`/${rule.id}/enabled`, 'PUT', { revision: rule.revision, enabled: !rule.enabled }), false, rule.id),
    restore: sourceRevision => selected && run(() => ruleRequest(`/${selected.id}/restore`, 'POST', { revision: selected.revision, sourceRevision })),
  };
}
