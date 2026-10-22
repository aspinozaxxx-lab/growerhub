import { getCurrentLocale, translateApp as t } from '../../locales/i18n';

const roles = { set: 'Установить состояние', hold: 'Удерживать состояние', water: 'Полив', delay: 'Пауза', notify: 'Уведомление' };
const weekdays = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
function Condition({ node, resourceNames }) {
  if (!node) return null;
  if (['all', 'any'].includes(node.type)) return <li>{t(node.type === 'all' ? 'Все условия' : 'Хотя бы одно условие')}<ul>{node.conditions?.map((c, i) => <Condition key={i} node={c} resourceNames={resourceNames} />)}</ul></li>;
  if (node.type === 'not') return <li>{t('Условие не выполняется')}<ul><Condition node={node.condition} resourceNames={resourceNames} /></ul></li>;
  if (node.type === 'time') return <li>{t('Время')}: {node.from}–{node.until}</li>;
  if (node.type === 'stage') return <li>{t('Растение')} №{node.plantId}: {node.stage}</li>;
  return <li>{resourceNames[node.resourceId] || `${t('Датчик')} №${node.resourceId}`}: {({ gt: '>', gte: '≥', lt: '<', lte: '≤', eq: '=', ne: '≠' })[node.op]} {String(node.value)}{node.holdSeconds > 0 ? ` · ${t('Выдержка')}: ${node.holdSeconds} ${t('с')}` : ''}<small> · {t('Показания не старше')} {node.maxAgeSeconds} {t('с')}</small></li>;
}
const duration = seconds => Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
const switchValue = value => /^(on|true)$/i.test(String(value)) ? t('Включено') : /^(off|false)$/i.test(String(value)) ? t('Выключено') : String(value);
function coordinationLabel(message, resourceNames) {
  const hold = /^Удержание устройства №(\d+): приоритет (\d+), (\d+) сек\. после фактического подтверждения\. По окончании управление возвращается актуальным требованиям\.$/.exec(message);
  if (hold) return t('{{device}}: приоритет {{priority}}, удержание {{seconds}} с после подтверждения. Затем остальные требования оцениваются заново.', {
    device: resourceNames[hold[1]] || `${t('Устройство')} №${hold[1]}`, priority: hold[2], seconds: hold[3],
  });
  const relation = /^«(.+)»: приоритет (\d+)\. Удержание с приоритетом (\d+) (.+)\.$/.exec(message);
  if (relation) return t('«{{name}}»: приоритет {{priority}}. Удержание с приоритетом {{holdPriority}} — {{relation}}.', {
    name: relation[1], priority: relation[2], holdPriority: relation[3], relation: t(relation[4]),
  });
  const other = /^На этих каналах также работает «(.+)»: выигрывает больший приоритет, равные — в неизменном порядке создания и ключа\.$/.exec(message);
  if (other) return t('Также работает «{{name}}». Выигрывает больший приоритет, при равенстве — ранее созданное требование и ключ источника.', { name: other[1] });
  return t(message);
}
function Timing({ timing, resourceNames }) {
  if (!timing) return null;
  const notes = { device_ack_delay: 'Длительность плановая: подтверждения устройств могут добавить задержку.', misfire_run_once: 'После пропуска выполнится только последний актуальный запуск.', misfire_skip: 'Опоздавший более чем на {{seconds}} с запуск будет пропущен.', timeline_truncated: 'Показаны первые {{count}} этапов, итог учитывает весь цикл.', legacy_nonblocking_water: 'Для ожидания завершения полива подготовьте правило версии 2.', repeat_while_no_catchup: 'Циклы повторяются только при выполнении условия. Пропущенные циклы не догоняются.', priority_arbitration: 'Временное удержание согласуется с другими сценариями по приоритету.' };
  return <section className="assistant-timing" aria-label={t('Предварительный план')}>
    <p><strong>{t('Весь цикл')}: {duration(timing.cycleSeconds)}</strong>{timing.activeWateringSeconds > 0 && <> · {t('Работа насоса')}: {duration(timing.activeWateringSeconds)}</>}</p>
    {timing.nextRuns?.length > 0 && <><strong>{t('Ближайшие запуски')}</strong><ol>{timing.nextRuns.map(r => <li key={r.at}>{new Date(r.at).toLocaleString(getCurrentLocale(), { timeZone: timing.timezone, day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</li>)}</ol><small>{timing.timezone}</small></>}
    {timing.timeline?.length > 0 && <details><summary>{t('Этапы цикла')}</summary><ol>{timing.timeline.map((s, i) => <li key={i}>{duration(s.startsAfterSeconds)}–{duration(s.startsAfterSeconds + s.durationSeconds)} · {t(roles[s.type] || s.type)}{s.resourceId ? ` · ${resourceNames[s.resourceId] || t('Устройство')}` : ''}</li>)}</ol></details>}
    {timing.notes?.map(n => <small key={n}>{t(notes[n] || n, { seconds: timing.scheduleGraceSeconds, count: timing.previewStepLimit })}</small>)}
  </section>;
}
export default function RuleExplanation({ definition: d, resourceNames = {}, scopeName, timing, coordination }) {
  if (!d) return null;
  const trigger = d.trigger;
  return <div className="assistant-rule-description">{scopeName && <p>{scopeName}</p>}<p><strong>{t('Когда')}</strong>: {trigger?.type === 'schedule' ? trigger.at : ['interval', 'repeat_while'].includes(trigger?.type) ? `${t('Каждые')} ${trigger.seconds % 60 === 0 ? trigger.seconds / 60 : trigger.seconds} ${t(trigger.seconds % 60 === 0 ? 'мин' : 'с')}` : trigger?.type === 'calendar' ? `${t('Каждые')} ${trigger.every} ${t(trigger.unit === 'week' ? 'нед.' : 'дн.')} · ${trigger.at} · ${t('С даты')} ${trigger.startDate}` : t('При изменении показаний')}{trigger?.days?.length ? ` · ${trigger.days.length === 7 ? t('Каждый день') : trigger.days.map(day => t(weekdays[day - 1])).join(', ')}` : ''}</p>
    {trigger?.type === 'repeat_while' && <><p>{t(trigger.first === 'after_interval' ? 'Первый запуск через один период после выполнения условия.' : 'Первый запуск сразу при выполнении условия.')}</p><p>{t(trigger.onConditionLoss === 'finish' ? 'Если условие пропадёт, текущий цикл завершится. Устаревшие данные отменяют удержание.' : 'Если условие пропадёт или данные устареют, удержание сразу отменится.')}</p></>}
    {d.condition && <ul><Condition node={d.condition} resourceNames={resourceNames} /></ul>}
    <ol>{d.actions?.map((a, i) => <li key={i}>{t(roles[a.type] || a.type)}{a.resourceId ? ` · ${resourceNames[a.resourceId] || t(a.type === 'water' ? 'Поливной канал' : 'Устройство')}` : ''}{a.type === 'hold' ? <>: {switchValue(a.value)} · {duration(a.durationSeconds)} · {t('Приоритет')}: {a.priority}</> : a.type === 'set' ? `: ${a.value}` : a.type === 'water' ? <>: {duration(a.durationSeconds)}{a.pulse && <> · {t('Импульс')}: {duration(a.pulse.runSeconds)} · {t('Пауза')}: {duration(a.pulse.pauseSeconds)}</>}</> : a.type === 'delay' ? `: ${a.seconds} ${t('с')}` : `: ${a.text}`}</li>)}</ol>
    {d.actions?.some(a => a.type === 'hold') && <p>{t('Удержание отсчитывается от подтверждения устройства. По окончании остальные сценарии оцениваются заново.')}</p>}
    <small>{t('Пауза между запусками')}: {d.cooldownSeconds} {t('с')}</small>
    {coordination?.length > 0 && <section className="assistant-rule-coordination"><h3>{t('Совместная работа сценариев')}</h3>
      <ul>{coordination.map((message, i) => <li key={i}>{coordinationLabel(message, resourceNames)}</li>)}</ul></section>}
    <Timing timing={timing} resourceNames={resourceNames} />
  </div>;
}
