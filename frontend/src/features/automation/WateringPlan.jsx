import { useEffect, useState } from 'react';
import { fetchWateringPlan } from '../../api/selfService';
import { formatDateTimeDDMMYYYY as formatDateTime } from '../../utils/formatters';
import { translateApp as t } from '../../locales/i18n';

export default function WateringPlan({ greenhouseId, revision }) {
  const [result, setResult] = useState(null);
  const [retry, setRetry] = useState(0);
  const requestKey = `${greenhouseId}:${revision}:${retry}`;
  useEffect(() => {
    let cancelled = false;
    fetchWateringPlan(greenhouseId).then((plan) => {
      if (!cancelled) setResult({ key: requestKey, plan });
    }).catch((error) => { if (!cancelled) setResult({ key: requestKey, error: error.message }); });
    return () => { cancelled = true; };
  }, [greenhouseId, requestKey]);
  if (result?.key !== requestKey) return <p className="watering-plan">{t('Рассчитываем план…')}</p>;
  if (result.error) return <div className="watering-plan"><p role="alert">{result.error}</p>
    <button type="button" onClick={() => setRetry((n) => n + 1)}>{t('Повторить')}</button></div>;
  const plan = result.plan;
  if (!plan) return null;
  const weatherBlocked = ['postponed', 'unavailable', 'expired'].includes(plan.weather?.status);
  return <details className="watering-plan">
    <summary><span>{t(plan.observe_only ? 'План · наблюдение' : 'План полива')}</span>
      <strong>{weatherBlocked ? t(plan.weather.status === 'expired' ? 'Слот пропущен' : 'Ожидание погоды') : plan.planned_at ? formatDateTime(plan.planned_at) : t('Ждём данных')}</strong></summary>
    <div className="watering-plan-facts">
      <span>{t('Подача воды')}<strong>{t('{{seconds}} сек', { seconds: plan.run_seconds })}</strong></span>
      <span>{t('Последний полив')}<strong>{plan.last_watered_at ? formatDateTime(plan.last_watered_at) : t('Нет истории')}</strong></span>
      <span>{t('Сегодня, время подачи')}<strong>{t('{{seconds}} сек', { seconds: plan.used_today_seconds })}</strong></span>
      {plan.pulse_enabled ? <span>{t('Импульс / пауза')}<strong>{plan.pulse_run_seconds} / {plan.pulse_pause_seconds} {t('сек')}</strong></span> : null}
      {plan.weather?.expected_mm != null ? <>
        <span>{t('Прогноз осадков')}<strong>{plan.weather.expected_mm.toFixed(1)} {t('мм')}</strong></span>
        <span>{t('Вероятность в одном интервале')}<strong>{plan.weather.max_period_probability == null ? t('Не предоставлена') : `${plan.weather.max_period_probability}%`}</strong></span>
      </> : null}
    </div>
    <ul>{plan.reasons.map((reason) => <li key={reason}>{reason.startsWith('Последний полив:')
      ? `${t('Последний полив')}${reason.slice('Последний полив'.length)}` : t(reason)}</li>)}</ul>
    {plan.soil ? <div className="watering-plan-soil">
      <p>{t('Последнее показание')}: {plan.soil.current ?? '—'} · {plan.soil.observed_at ? formatDateTime(plan.soil.observed_at) : '—'}</p>
      <p>{t('Доля пути от влажного к сухому ориентиру')}: {plan.soil.drying_fraction == null ? '—' : `${(plan.soil.drying_fraction * 100).toFixed(0)}%`}</p>
      <p>{t('Отклик на обычный полив')}: {t(plan.soil.response_verified ? 'Подтверждён по истории' : 'Ещё не подтверждён')}</p>
    </div> : null}
    {plan.weather ? <div className="watering-plan-weather">
      {plan.weather.pending ? <p>{t('Исходный слот')}: {formatDateTime(plan.weather.pending.originalAt)}<br />
        {t('Предел ожидания')}: {formatDateTime(plan.weather.pending.deadline)}</p> : null}
      {plan.weather.from && plan.weather.to ? <p>{t('Интервал прогноза')}: {formatDateTime(plan.weather.from)} — {formatDateTime(plan.weather.to)}</p> : null}
      {plan.weather.updated_at ? <p>{t('Модель обновлена')}: {formatDateTime(plan.weather.updated_at)}</p> : null}
      {plan.weather.source === 'SIMULATED' ? <small>{t('Погода моделируется в демо; это не прогноз для вашей фермы.')}</small>
        : plan.weather.source === 'MET Norway' ? <small>{t('Прогноз')}: <a href="https://api.met.no/" target="_blank" rel="noreferrer">MET Norway</a> · <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a></small> : null}
    </div> : null}
    <small>{t('Предварительный расчёт пересматривается перед запуском. Прогноз осадков не является фактом полива. Вероятность — максимум для одного показанного интервала, а не вероятность дождя за всё окно.')}</small>
    <button type="button" onClick={() => setRetry((n) => n + 1)}>{t('Обновить план')}</button>
  </details>;
}
