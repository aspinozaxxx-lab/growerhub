import { useEffect, useState } from 'react';
import SidePanel from '../../components/ui/SidePanel';
import Button from '../../components/ui/Button';
import { fetchWaterMeterStatistics } from '../../api/selfService';
import { isSessionExpiredError } from '../../api/client';
import { formatDateTime, formatVolumeLiters } from './manualWateringModel';
import { translateApp as t, getIntlLocale } from '../../locales/i18n';
import './WaterMeterPanel.css';

export default function WaterMeterPanel({ device, onClose }) {
  const [month, setMonth] = useState('');
  const [result, setResult] = useState(null);
  const [revision, setRevision] = useState(0);
  const requestKey = `${device.coordinator_id}:${device.ieee_address}:${month}:${revision}`;
  const loading = result?.key !== requestKey;
  const statistics = loading ? null : result?.data;
  const error = loading ? '' : result?.error;
  useEffect(() => {
    let current = true;
    fetchWaterMeterStatistics(device.coordinator_id, device.ieee_address, month).then((data) => {
      if (current) setResult({ key: requestKey, data });
    }).catch((err) => {
      if (current && !isSessionExpiredError(err)) setResult({ key: requestKey, error: err.message || t('Не удалось загрузить учёт воды') });
    });
    return () => { current = false; };
  }, [device.coordinator_id, device.ieee_address, month, requestKey]);
  const today = statistics?.days?.find((day) => day.date === statistics.today);
  return <SidePanel isOpen title={t('Учёт воды')} subtitle={device.friendly_name} onClose={onClose} width="lg">
    <div className="water-meter-panel">
      <label className="water-meter-panel__month">{t('Месяц')}<input type="month" aria-label={t('Месяц')}
        value={month || statistics?.month || ''} onChange={(event) => setMonth(event.target.value)} /></label>
      {error ? <div role="alert">{error}<Button size="sm" onClick={() => setRevision((value) => value + 1)}>{t('Повторить')}</Button></div> : null}
      {loading ? <p role="status">{t('Загрузка...')}</p> : !error && statistics ? <>
        {!statistics.supported ? <p>{t('Для этого устройства учёт воды пока недоступен')}</p> : <>
          {statistics.simulated ? <p className="water-meter-panel__note">{t('Демонстрационные показания')}</p> : null}
          <div className="water-meter-panel__summary">
            {today ? <div><span>{t('Сегодня по отчётам')}</span><strong>{formatVolumeLiters(today.known_volume_l)}</strong></div> : null}
            <div><span>{t('Месяц по отчётам')}</span><strong>{formatVolumeLiters(statistics.known_volume_l)}</strong></div>
          </div>
          <p className="water-meter-panel__note">{t('Сумма уникальных завершённых поливов по расходомеру. Охват неполный: пропущенные отчёты не считаются нулём.')}</p>
          <details className="water-meter-panel__details"><summary>{t('Последние показания и точность')}</summary>
            <p>{t('Последний расход')}: {statistics.flow_l_min == null ? '—' : `${Number(statistics.flow_l_min).toLocaleString(getIntlLocale(), { maximumFractionDigits: 2 })} ${t('л/мин')}`}</p>
            <small>{formatDateTime(statistics.flow_observed_at)}</small>
            <p>{t('Дневной счётчик устройства')}: {formatVolumeLiters(statistics.reported_daily_l)}</p>
            <small>{t('Граница суток счётчика ещё не проверена. Он не прибавляется к объёму поливов.')}</small>
            <p>{t('Полив через полночь относится к дате окончания. Литры не распределяются между растениями без измерения.')}</p>
            {statistics.history_truncated ? <p>{t('Объём истории превышает доступный предел. Итог содержит только полученную часть.')}</p> : null}
          </details>
          <section><h3>{t('По дням')}</h3><div className="water-meter-panel__days">
            {(statistics.days || []).map((day) => <div key={day.date}><span>{day.date.slice(8)}.{day.date.slice(5, 7)}</span>
              <strong>{day.known_volume_l == null ? t('Нет данных') : formatVolumeLiters(day.known_volume_l)}</strong>
              <small>{day.operation_count ? t('{{count}} поливов', { count: day.operation_count }) : '—'}</small></div>)}
          </div></section>
          <section><h3>{t('Отчёты о поливе')}</h3>
            {(statistics.operations || []).length === 0 ? <p>{t('Завершённых поливов с подтверждёнными литрами за этот месяц нет')}</p> : null}
            {(statistics.operations || []).map((operation) => <article className="water-meter-panel__operation" key={operation.started_at}>
              <div><strong>{formatDateTime(operation.started_at)}</strong><strong>{formatVolumeLiters(operation.volume_l)}</strong></div>
              <small>{t('Окончание')}: {formatDateTime(operation.finished_at)}</small>
              {operation.imported ? <small>{t('Получен после завершения; это история, а не новый полив')}</small> : null}
              {operation.issue ? <small>{t('Отчёты противоречат друг другу. Литры не включены в итог.')}</small> : null}
            </article>)}
          </section>
        </>}
      </> : null}
    </div>
  </SidePanel>;
}
