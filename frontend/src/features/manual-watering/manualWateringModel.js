import { formatDateTimeDDMMYYYY } from '../../utils/formatters';

const MODE_LABELS = {
  timed: 'По времени',
  until_leak: 'До протечки',
};

const PHASE_LABELS = {
  active: 'Полив активен',
  running: 'Насос работает',
  pause: 'Пауза импульса',
  stopping: 'Останавливается',
  terminal: 'Завершён',
  completed: 'Завершён',
  failed: 'Ошибка',
};

const SOURCE_LABELS = {
  admin_manual: 'Ручной полив администратора',
  automation: 'Автоматизация',
  user_manual: 'Ручной полив пользователя',
};

const REASON_LABELS = {
  duration: 'Завершён по времени',
  leak: 'Остановлен по протечке',
  limit: 'Остановлен по предельному времени',
  manual: 'Остановлен вручную',
  error: 'Остановлен из-за ошибки',
  command_error: 'Остановлен из-за ошибки команды',
  device_offline: 'Остановлен из-за потери связи с насосом',
  sensor_unavailable: 'Остановлен из-за потери датчиков протечки',
  recovery: 'Завершён при восстановлении после перезапуска',
};

const BLOCK_REASON_LABELS = {
  pump_offline: 'Насос не в сети',
  pump_running: 'Насос уже включён',
  pump_session_active: 'У насоса уже есть активная сессия',
  device_busy: 'Физическое устройство занято',
  no_boxes: 'К насосу не привязаны боксы',
  no_plants: 'В привязанных боксах нет растений',
  leak_triggered: 'Один из датчиков уже показывает протечку',
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
  if (hours > 0) parts.push(`${hours} ч`);
  if (minutes > 0) parts.push(`${minutes} мин`);
  if (restSeconds > 0 || parts.length === 0) parts.push(`${restSeconds} сек`);
  return parts.join(' ');
}

export function formatVolumeLiters(value, emptyLabel = 'Не рассчитан') {
  if (value === null || value === undefined || value === '' || !Number.isFinite(Number(value))) {
    return emptyLabel;
  }
  return `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 3 }).format(Number(value))} л`;
}

export function formatDateTime(value) {
  return formatDateTimeDDMMYYYY(value) || 'Время неизвестно';
}

export function modeLabel(value) {
  return MODE_LABELS[value] || 'Режим неизвестен';
}

export function phaseLabel(value) {
  return PHASE_LABELS[value] || 'Состояние обновляется';
}

export function sourceLabel(value) {
  return SOURCE_LABELS[value] || 'Источник неизвестен';
}

export function completionReasonLabel(value) {
  return REASON_LABELS[value] || (value ? `Причина: ${value}` : 'Причина не указана');
}

export function startBlockReasonLabel(value) {
  return BLOCK_REASON_LABELS[value] || value || 'Запуск недоступен';
}

export function sessionBoxesLabel(session) {
  const names = listOrEmpty(session?.boxes)
    .map((box) => box.box_name || box.name)
    .filter(Boolean);
  return names.length > 0 ? names.join(', ') : 'Боксы не указаны';
}

export function sessionPlantsCount(session) {
  return listOrEmpty(session?.boxes).reduce(
    (total, box) => total + listOrEmpty(box?.plants).length,
    0,
  );
}
