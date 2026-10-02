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
  return <details className="watering-plan">
    <summary><span>{t(plan.observe_only ? 'План · наблюдение' : 'План полива')}</span>
      <strong>{plan.planned_at ? formatDateTime(plan.planned_at) : t('Ждём данных')}</strong></summary>
    <div className="watering-plan-facts">
      <span>{t('Подача воды')}<strong>{t('{{seconds}} сек', { seconds: plan.run_seconds })}</strong></span>
      <span>{t('Последний полив')}<strong>{plan.last_watered_at ? formatDateTime(plan.last_watered_at) : t('Нет истории')}</strong></span>
      <span>{t('Сегодня, время подачи')}<strong>{t('{{seconds}} сек', { seconds: plan.used_today_seconds })}</strong></span>
      {plan.pulse_enabled ? <span>{t('Импульс / пауза')}<strong>{plan.pulse_run_seconds} / {plan.pulse_pause_seconds} {t('сек')}</strong></span> : null}
    </div>
    <ul>{plan.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
    {plan.soil ? <div className="watering-plan-soil">
      <p>{t('Последнее показание')}: {plan.soil.current ?? '—'} · {plan.soil.observed_at ? formatDateTime(plan.soil.observed_at) : '—'}</p>
      <p>{t('Доля пути от влажного к сухому ориентиру')}: {plan.soil.drying_fraction == null ? '—' : `${(plan.soil.drying_fraction * 100).toFixed(0)}%`}</p>
      <p>{t('Отклик на обычный полив')}: {t(plan.soil.response_verified ? 'Подтверждён по истории' : 'Ещё не подтверждён')}</p>
    </div> : null}
    <small>{t('Предварительный расчёт пересматривается перед запуском. Погодная поправка на этом этапе не применяется.')}</small>
  </details>;
}
