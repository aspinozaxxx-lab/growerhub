import { translateApp as t } from '../../locales/i18n';

const roles = { set: 'Установить состояние', water: 'Полив', delay: 'Пауза', notify: 'Уведомление' };
const weekdays = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
function Condition({ node, resourceNames }) {
  if (!node) return null;
  if (['all', 'any'].includes(node.type)) return <li>{t(node.type === 'all' ? 'Все условия' : 'Хотя бы одно условие')}<ul>{node.conditions?.map((c, i) => <Condition key={i} node={c} resourceNames={resourceNames} />)}</ul></li>;
  if (node.type === 'not') return <li>{t('Условие не выполняется')}<ul><Condition node={node.condition} resourceNames={resourceNames} /></ul></li>;
  if (node.type === 'time') return <li>{t('Время')}: {node.from}–{node.until}</li>;
  if (node.type === 'stage') return <li>{t('Растение')} №{node.plantId}: {node.stage}</li>;
  return <li>{resourceNames[node.resourceId] || `${t('Датчик')} №${node.resourceId}`}: {({ gt: '>', gte: '≥', lt: '<', lte: '≤', eq: '=', ne: '≠' })[node.op]} {String(node.value)}{node.holdSeconds > 0 ? ` · ${t('Выдержка')}: ${node.holdSeconds} ${t('с')}` : ''}<small> · {t('Показания не старше')} {node.maxAgeSeconds} {t('с')}</small></li>;
}
export default function RuleExplanation({ definition: d, resourceNames = {}, scopeName }) {
  if (!d) return null;
  const trigger = d.trigger;
  return <div className="assistant-rule-description">{scopeName && <p>{scopeName}</p>}<p><strong>{t('Когда')}</strong>: {trigger?.type === 'schedule' ? trigger.at : trigger?.type === 'interval' ? `${t('Каждые')} ${trigger.seconds} ${t('с')}` : t('При изменении показаний')}{trigger?.days?.length ? ` · ${trigger.days.length === 7 ? t('Каждый день') : trigger.days.map(day => t(weekdays[day - 1])).join(', ')}` : ''}</p>
    {d.condition && <ul><Condition node={d.condition} resourceNames={resourceNames} /></ul>}
    <ol>{d.actions?.map((a, i) => <li key={i}>{t(roles[a.type] || a.type)}{a.resourceId ? ` · ${resourceNames[a.resourceId] || `${t('Устройство')} №${a.resourceId}`}` : ''}{a.type === 'set' ? `: ${a.value}` : a.type === 'water' ? `: ${a.durationSeconds} ${t('с')}` : a.type === 'delay' ? `: ${a.seconds} ${t('с')}` : `: ${a.text}`}</li>)}</ol>
    <small>{t('Пауза между запусками')}: {d.cooldownSeconds} {t('с')}</small>
  </div>;
}
