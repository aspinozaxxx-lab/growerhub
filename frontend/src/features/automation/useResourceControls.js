import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchResourceControls, releaseResourceControl, setResourceControl } from '../../api/automationControl';

export default function useResourceControls(refreshKey) {
  const [controls, setControls] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(null);
  const alive = useRef(false);
  const locked = useRef(false);
  const pending = useRef(new Map());
  const generation = useRef(0);
  const load = useCallback(async signal => {
    const at = generation.current;
    const result = await fetchResourceControls(signal);
    if (alive.current && !signal?.aborted && at === generation.current) { setControls(result); setError(''); }
  }, []);

  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; };
  }, []);

  useEffect(() => {
    if (!refreshKey) return undefined;
    const controller = new AbortController();
    let reading = false;
    const refresh = async () => {
      if (reading || document.visibilityState === 'hidden' || locked.current) return;
      reading = true;
      try { await load(controller.signal); }
      catch (failure) { if (alive.current && !controller.signal.aborted) setError(failure.message); }
      finally { reading = false; }
    };
    refresh();
    const timer = setInterval(refresh, 15000);
    document.addEventListener('visibilitychange', refresh);
    return () => { controller.abort(); clearInterval(timer); document.removeEventListener('visibilitychange', refresh); };
  }, [load, refreshKey]);

  const run = async (id, action) => {
    if (locked.current) return false;
    locked.current = true; setBusy(id); setError('');
    generation.current++;
    try {
      const result = await action();
      if (result && alive.current) setControls(current => [...current.filter(control => control.resourceId !== result.resourceId), result]);
      try { await load(); } catch (failure) { if (alive.current) setError(failure.message); }
      return true;
    } catch (failure) {
      if (alive.current) setError(failure.message);
      return false;
    } finally {
      locked.current = false;
      if (alive.current) setBusy(null);
    }
  };

  return { controls, error, busy,
    refresh: () => run('refresh', () => Promise.resolve()),
    set: (id, value, ttlSeconds) => run(id, async () => {
      const previous = pending.current.get(id);
      const command = previous?.value === value && previous?.ttlSeconds === ttlSeconds
        ? previous : { requestId: crypto.randomUUID(), value, ttlSeconds };
      pending.current.set(id, command);
      const result = await setResourceControl(id, command);
      pending.current.delete(id);
      return result;
    }),
    release: (id, demand) => run(id, () => releaseResourceControl(id, demand)),
  };
}
