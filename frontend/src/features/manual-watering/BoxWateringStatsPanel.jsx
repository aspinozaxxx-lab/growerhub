import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, Droplets, Square } from 'lucide-react';
import { fetchAdminBoxWateringStatistics, stopAdminManualWatering } from '../../api/admin';
import { isSessionExpiredError } from '../../api/client';
import SidePanel from '../../components/ui/SidePanel';
import Button from '../../components/ui/Button';
import { useAuth } from '../auth/AuthContext';
import {
  completionReasonLabel,
  formatDateTime,
  formatDurationSeconds,
  formatVolumeLiters,
  listOrEmpty,
  mergeSessionsById,
  modeLabel,
  phaseLabel,
  sourceLabel,
} from './manualWateringModel';
import './BoxWateringStatsPanel.css';

const RANGE_OPTIONS = [
  { key: 'day', label: 'День' },
  { key: 'week', label: 'Неделя' },
  { key: 'month', label: 'Месяц' },
];
const PAGE_SIZE = 10;

function countEntries(value) {
  return Object.entries(value && typeof value === 'object' ? value : {})
    .filter(([, count]) => Number(count) > 0);
}

function StatisticsSession({ session }) {
  return (
    <article className="box-watering-session">
      <div className="box-watering-session__header">
        <strong>{formatDateTime(session.started_at)}</strong>
        <span>{sourceLabel(session.source)}</span>
      </div>
      <div className="box-watering-session__facts">
        <span>{modeLabel(session.mode)}</span>
        <span>{formatDurationSeconds(session.active_duration_s)}</span>
      </div>
      <span className="box-watering-session__reason">
        {session.finished_at ? completionReasonLabel(session.completion_reason) : phaseLabel(session.phase)}
      </span>
    </article>
  );
}

function BoxWateringStatsPanel({ target, onClose }) {
  const { token } = useAuth();
  const [range, setRange] = useState('day');
  const [statistics, setStatistics] = useState(null);
  const [nextBeforeId, setNextBeforeId] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isStopping, setIsStopping] = useState(false);
  const [error, setError] = useState('');
  const requestVersionRef = useRef(0);
  const hasLoadedMoreRef = useRef(false);
  const nextBeforeIdRef = useRef(null);

  const loadStatistics = useCallback(async ({ append = false, silent = false } = {}) => {
    if (!target?.boxId) return;
    const requestVersion = requestVersionRef.current;
    if (!silent) {
      if (append) setIsLoadingMore(true);
      else setIsLoading(true);
    }
    try {
      const data = await fetchAdminBoxWateringStatistics(target.boxId, {
        range,
        limit: PAGE_SIZE,
        beforeId: append ? nextBeforeIdRef.current : null,
      }, token);
      if (requestVersion !== requestVersionRef.current) return;
      setStatistics((previous) => ({
        ...(data || {}),
        sessions: append
          ? mergeSessionsById(previous?.sessions, data?.sessions)
          : silent
            ? mergeSessionsById(data?.sessions, previous?.sessions)
            : listOrEmpty(data?.sessions),
      }));
      if (append) {
        hasLoadedMoreRef.current = true;
        nextBeforeIdRef.current = data?.next_before_id ?? null;
        setNextBeforeId(nextBeforeIdRef.current);
      } else if (!silent || !hasLoadedMoreRef.current) {
        nextBeforeIdRef.current = data?.next_before_id ?? null;
        setNextBeforeId(nextBeforeIdRef.current);
      }
      setError('');
    } catch (err) {
      if (requestVersion !== requestVersionRef.current) return;
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось загрузить статистику полива');
    } finally {
      if (requestVersion === requestVersionRef.current && !silent) {
        setIsLoading(false);
        setIsLoadingMore(false);
      }
    }
  }, [range, target?.boxId, token]);

  useEffect(() => {
    if (target?.boxId) loadStatistics();
  }, [range, target?.boxId]); // eslint-disable-line react-hooks/exhaustive-deps

  const hasActiveSession = Boolean(statistics?.active_session);

  useEffect(() => {
    if (!target?.boxId || !hasActiveSession) return undefined;
    const poll = () => {
      if (document.visibilityState === 'visible') loadStatistics({ silent: true });
    };
    const intervalId = window.setInterval(poll, 3000);
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') poll();
    };
    const handleFocus = () => poll();
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('focus', handleFocus);
    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, [hasActiveSession, loadStatistics, target?.boxId]);

  const handleStop = async () => {
    const session = statistics?.active_session;
    const pumpId = session?.pump_id || target?.pumpId;
    if (!pumpId) return;
    const accepted = window.confirm('Остановить насос? Полив завершится во всех привязанных к нему боксах.');
    if (!accepted) return;
    setIsStopping(true);
    setError('');
    try {
      await stopAdminManualWatering(pumpId, token);
      await loadStatistics({ silent: true });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось остановить полив');
    } finally {
      setIsStopping(false);
    }
  };

  if (!target) return null;

  const modeCounts = countEntries(statistics?.mode_counts);
  const reasonCounts = countEntries(statistics?.reason_counts);
  const sessions = listOrEmpty(statistics?.sessions);
  const activeSession = statistics?.active_session;

  return (
    <SidePanel
      isOpen
      onClose={onClose}
      title="Статистика полива"
      subtitle={target.title || 'Бокс'}
      width="lg"
    >
      <div className="box-watering-stats__ranges" role="group" aria-label="Период статистики">
        {RANGE_OPTIONS.map((option) => (
          <button
            key={option.key}
            type="button"
            className={range === option.key ? 'is-active' : ''}
            onClick={() => {
              if (option.key === range) {
                if (!statistics && !isLoading) loadStatistics();
                return;
              }
              requestVersionRef.current += 1;
              hasLoadedMoreRef.current = false;
              nextBeforeIdRef.current = null;
              setStatistics(null);
              setNextBeforeId(null);
              setIsLoading(false);
              setIsLoadingMore(false);
              setError('');
              setRange(option.key);
            }}
            aria-pressed={range === option.key}
          >
            {option.label}
          </button>
        ))}
      </div>

      {error ? <div className="box-watering-stats__state is-error" role="alert">{error}</div> : null}
      {isLoading && !statistics ? <div className="box-watering-stats__state">Загрузка...</div> : null}

      {statistics ? (
        <div className="box-watering-stats">
          {activeSession ? (
            <section className="box-watering-stats__active">
              <Droplets size={22} aria-hidden="true" />
              <div>
                <strong>{phaseLabel(activeSession.phase)}</strong>
                <span>{modeLabel(activeSession.mode)} · {formatDurationSeconds(activeSession.active_duration_s)}</span>
                <small>
                  <AlertTriangle size={13} aria-hidden="true" />
                  Остановка затронет все боксы этого насоса.
                </small>
              </div>
              <Button type="button" variant="danger" size="sm" onClick={handleStop} isLoading={isStopping}>
                <Square size={13} aria-hidden="true" />
                Остановить
              </Button>
            </section>
          ) : null}

          <section className="box-watering-stats__summary" aria-label="Итоги периода">
            <div><span>Сессии</span><strong>{statistics.session_count ?? 0}</strong></div>
            <div><span>Активное время</span><strong>{formatDurationSeconds(statistics.active_duration_s)}</strong></div>
            <div>
              <span>Рассчитанный объём</span>
              <strong>{formatVolumeLiters(statistics.known_volume_l)}</strong>
              {statistics.partial_volume ? <small>Есть растения без указанной скорости</small> : null}
            </div>
          </section>

          <section className="box-watering-stats__counts">
            <div>
              <h3>Режимы</h3>
              {modeCounts.length === 0 ? <span>Нет данных</span> : modeCounts.map(([mode, count]) => (
                <span key={mode}>{modeLabel(mode)}: <strong>{count}</strong></span>
              ))}
            </div>
            <div>
              <h3>Причины завершения</h3>
              {reasonCounts.length === 0 ? <span>Нет данных</span> : reasonCounts.map(([reason, count]) => (
                <span key={reason}>{completionReasonLabel(reason)}: <strong>{count}</strong></span>
              ))}
            </div>
          </section>

          <section className="box-watering-stats__sessions">
            <h3>Сессии полива</h3>
            {sessions.length === 0 ? (
              <div className="box-watering-stats__state">В выбранном периоде поливов не было</div>
            ) : sessions.map((session) => <StatisticsSession key={session.id} session={session} />)}
            {nextBeforeId ? (
              <Button type="button" variant="ghost" size="sm" onClick={() => loadStatistics({ append: true })} isLoading={isLoadingMore}>
                Показать ещё
              </Button>
            ) : null}
          </section>
        </div>
      ) : null}
    </SidePanel>
  );
}

export default BoxWateringStatsPanel;
