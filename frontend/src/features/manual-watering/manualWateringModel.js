import { formatDateTimeDDMMYYYY } from '../../utils/formatters';
import { getIntlLocale, translateApp } from '../../locales/i18n';

const MODE_LABELS = {
  timed: translateApp("По времени"),
  until_leak: translateApp("До протечки"),
};

const PHASE_LABELS = {
  active: translateApp("Полив активен"),
  running: translateApp("Насос работает"),
  pause: translateApp("Пауза импульса"),
  stopping: translateApp("Останавливается"),
  terminal: translateApp("Завершён"),
  completed: translateApp("Завершён"),
  failed: translateApp("Ошибка"),
};

const SOURCE_LABELS = {
  admin_manual: translateApp("Ручной полив администратора"),
  automation: translateApp("Автоматизация"),
  user_manual: translateApp("Ручной полив пользователя"),
};

const REASON_LABELS = {
  duration: translateApp("Завершён по времени"),
  leak: translateApp("Остановлен по протечке"),
  limit: translateApp("Остановлен по предельному времени"),
  manual: translateApp("Остановлен вручную"),
  error: translateApp("Остановлен из-за ошибки"),
  command_error: translateApp("Остановлен из-за ошибки команды"),
  device_offline: translateApp("Остановлен из-за потери связи с насосом"),
  sensor_unavailable: translateApp("Остановлен из-за потери датчиков протечки"),
  recovery: translateApp("Завершён при восстановлении после перезапуска"),
};

const BLOCK_REASON_LABELS = {
  pump_offline: translateApp("Насос не в сети"),
  pump_running: translateApp("Насос уже включён"),
  pump_session_active: translateApp("У насоса уже есть активная сессия"),
  device_busy: translateApp("Физическое устройство занято"),
  no_boxes: translateApp("К насосу не привязаны боксы"),
  no_plants: translateApp("В привязанных боксах нет растений"),
  leak_triggered: translateApp("Один из датчиков уже показывает протечку"),
};

export function listOrEmpty(value) {
  return Array.isArray(value) ? value : [];
}

export function normalizeManualWateringOverview(value) {
  const data = value && typeof value === 'object' ? value : {};
  return {
    defaults: data.defaults && typeof data.defaults === 'object' ? data.defaults : {},
    pumps: listOrEmpty(data.pumps),
  };
}

export function wateringDefaultsReady(defaults) {
  return [
    defaults?.timed_duration_s,
    defaults?.until_leak_max_active_duration_s,
    defaults?.pulse_run_s,
    defaults?.pulse_pause_s,
  ].every((value) => Number.isInteger(Number(value)) && Number(value) > 0);
}

export function normalizeSessionPage(value) {
  if (Array.isArray(value)) {
    return { items: value, nextBeforeId: null };
  }
  const data = value && typeof value === 'object' ? value : {};
  const items = listOrEmpty(data.items ?? data.sessions);
  const nextBeforeId = data.next_before_id ?? data.nextBeforeId ?? null;
  return { items, nextBeforeId };
}

export function mergeSessionsById(primary, secondary) {
  const result = [];
  const seen = new Set();
  [...listOrEmpty(primary), ...listOrEmpty(secondary)].forEach((session) => {
    const key = session?.id ?? session?.correlation_id;
    if (key !== null && key !== undefined && seen.has(String(key))) return;
    if (key !== null && key !== undefined) seen.add(String(key));
    result.push(session);
  });
  return result;
}

export function pumpCurrentSession(pump) {
  return pump?.current_session || pump?.active_session || null;
}

export function overviewHasActiveSession(overview) {
  return listOrEmpty(overview?.pumps).some((pump) => Boolean(pumpCurrentSession(pump)));
}

export function pumpStartBlockReasons(pump) {
  return listOrEmpty(pump?.capabilities?.start_block_reasons);
}

export function pumpCanStart(pump) {
  return pump?.capabilities?.can_start === true;
}

export function pumpCanStop(pump) {
  return pump?.capabilities?.can_stop === true || Boolean(pumpCurrentSession(pump));
}

export function modeAvailable(pump, mode) {
  return pump?.capabilities?.[mode] === true;
}

export function formatDurationSeconds(value) {
  const seconds = Math.max(0, Math.round(Number(value) || 0));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const restSeconds = seconds % 60;
  const parts = [];
  if (hours > 0) parts.push(translateApp("{{value1}} ч", { value1: hours }));
  if (minutes > 0) parts.push(translateApp("{{value1}} мин", { value1: minutes }));
  if (restSeconds > 0 || parts.length === 0) parts.push(translateApp("{{value1}} сек", { value1: restSeconds }));
  return parts.join(' ');
}

export function formatVolumeLiters(value, emptyLabel = translateApp("Не рассчитан")) {
  if (value === null || value === undefined || value === '' || !Number.isFinite(Number(value))) {
    return emptyLabel;
  }
  return translateApp("{{value1}} л", { value1: new Intl.NumberFormat(getIntlLocale(), { maximumFractionDigits: 3 }).format(Number(value)) });
}

export function formatDateTime(value) {
  return formatDateTimeDDMMYYYY(value) || translateApp("Время неизвестно");
}

export function modeLabel(value) {
  return MODE_LABELS[value] || translateApp("Режим неизвестен");
}

export function phaseLabel(value) {
  return PHASE_LABELS[value] || translateApp("Состояние обновляется");
}

export function sourceLabel(value) {
  return SOURCE_LABELS[value] || translateApp("Источник неизвестен");
}

export function completionReasonLabel(value) {
  return REASON_LABELS[value] || (value ? translateApp("Причина: {{value1}}", { value1: value }) : translateApp("Причина не указана"));
}

export function startBlockReasonLabel(value) {
  return BLOCK_REASON_LABELS[value] || value || translateApp("Запуск недоступен");
}

export function sessionBoxesLabel(session) {
  const names = listOrEmpty(session?.boxes)
    .map((box) => box.box_name || box.name)
    .filter(Boolean);
  return names.length > 0 ? names.join(', ') : translateApp("Боксы не указаны");
}

export function sessionPlantsCount(session) {
  return listOrEmpty(session?.boxes).reduce(
    (total, box) => total + listOrEmpty(box?.plants).length,
    0,
  );
}
