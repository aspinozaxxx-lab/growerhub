import { getIntlLocale } from '../locales/i18n';

export const DEFAULT_UI_TIME_ZONE = 'Europe/Moscow';
let uiTimeZone = DEFAULT_UI_TIME_ZONE;

export function isValidTimeZone(value) {
  if (!value || typeof value !== 'string') return false;
  try {
    new Intl.DateTimeFormat('en', { timeZone: value }).format();
    return true;
  } catch {
    return false;
  }
}

export function setUiTimeZone(value) {
  uiTimeZone = isValidTimeZone(value) ? value : DEFAULT_UI_TIME_ZONE;
  return uiTimeZone;
}

export function getUiTimeZone() {
  return uiTimeZone;
}

export function getSupportedTimeZones() {
  if (typeof Intl.supportedValuesOf === 'function') {
    return Intl.supportedValuesOf('timeZone');
  }
  return [
    'Europe/Moscow',
    'Europe/Kaliningrad',
    'Europe/Samara',
    'Asia/Yekaterinburg',
    'Asia/Omsk',
    'Asia/Krasnoyarsk',
    'Asia/Irkutsk',
    'Asia/Yakutsk',
    'Asia/Vladivostok',
    'Asia/Magadan',
    'Asia/Kamchatka',
    'UTC',
  ];
}

export function formatSensorValue(value, fractionDigits = 1) {
  if (value === null || value === undefined) {
    return '-';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return '-';
  }
  return num.toFixed(fractionDigits);
}

// Translitem: backend chasto otdaet datetime bez timezone (naive) no po smyslu eto UTC.
function _normalizeBackendIso(value) {
  if (typeof value !== 'string') return value;
  const raw = value.trim();
  if (!raw) return raw;

  // Translitem: SQLite mozhet dat format s probelom, privodim k ISO s "T".
  const withT = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d+)?$/.test(raw)
    ? raw.replace(' ', 'T')
    : raw;

  // Translitem: esli timezone net, a eto datetime (YYYY-MM-DDTHH:MM...), dobavlyaem "Z" (UTC).
  const hasZone = /Z$/i.test(withT) || /[+-]\d{2}:\d{2}$/.test(withT);
  const looksLikeDateTime = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(withT);
  if (!hasZone && looksLikeDateTime) {
    return `${withT}Z`;
  }

  return withT;
}

// Translitem: bezopasno parsim timestamp iz backend v Date.
export function parseBackendTimestamp(value) {
  if (!value) return null;
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }
  if (typeof value === 'number') {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  if (typeof value === 'string') {
    const normalized = _normalizeBackendIso(value);
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  return null;
}

// Translitem: raskladyvaem Date na y/m/d/h/m v nuzhnoj timezone (Moskva).
function _getDateTimeParts(date, options = {}) {
  const formatter = new Intl.DateTimeFormat(getIntlLocale(), {
    timeZone: options.timeZone || uiTimeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
  const parts = formatter.formatToParts(date);
  const result = {};
  for (const part of parts) {
    if (['year', 'month', 'day', 'hour', 'minute', 'second'].includes(part.type)) {
      result[part.type] = part.value;
    }
  }
  return result;
}

export function formatDateKeyYYYYMMDD(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  const { year, month, day } = _getDateTimeParts(date);
  if (!year || !month || !day) return '';
  return `${year}-${month}-${day}`;
}

export function formatDateDDMM(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  const { day, month } = _getDateTimeParts(date);
  if (!day || !month) return '';
  return `${day}.${month}`;
}

export function formatDateOnly(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  return new Intl.DateTimeFormat(getIntlLocale(), {
    timeZone: uiTimeZone,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

export function formatDateLong(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  return new Intl.DateTimeFormat(getIntlLocale(), {
    timeZone: uiTimeZone,
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

export function formatCalendarDateLong(dateKey) {
  const match = String(dateKey || '').match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return '';
  return new Intl.DateTimeFormat(getIntlLocale(), {
    timeZone: 'UTC',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))));
}

export function formatTimestampLabel(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  if (getIntlLocale() === 'en-GB') {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: uiTimeZone,
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date);
  }
  const { day, month, hour, minute } = _getDateTimeParts(date);
  if (!day || !month || !hour || !minute) return '';
  return `${day}.${month} ${hour}:${minute}`;
}

export function formatDateTimeDDMMYYYY(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  if (getIntlLocale() === 'en-GB') {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: uiTimeZone,
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date);
  }
  const { day, month, year, hour, minute } = _getDateTimeParts(date);
  if (!day || !month || !year || !hour || !minute) return '';
  return `${day}.${month}.${year}, ${hour}:${minute}`;
}

export function formatTimeHHMM(dateOrString) {
  const date = parseBackendTimestamp(dateOrString);
  if (!date) return '';
  const { hour, minute } = _getDateTimeParts(date);
  if (!hour || !minute) return '';
  return `${hour}:${minute}`;
}

function zonedPartsToUtc(parts, timeZone = uiTimeZone) {
  const desiredUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    Number(parts.hour || 0),
    Number(parts.minute || 0),
    Number(parts.second || 0),
  );
  let candidate = desiredUtc;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const actual = _getDateTimeParts(new Date(candidate), { timeZone });
    const actualUtc = Date.UTC(
      Number(actual.year),
      Number(actual.month) - 1,
      Number(actual.day),
      Number(actual.hour),
      Number(actual.minute),
      Number(actual.second),
    );
    const correction = desiredUtc - actualUtc;
    candidate += correction;
    if (correction === 0) break;
  }
  return new Date(candidate);
}

export function zonedDateTimeInputToUtc(value, timeZone = uiTimeZone) {
  const match = String(value || '').match(
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/,
  );
  if (!match || !isValidTimeZone(timeZone)) return null;
  return zonedPartsToUtc({
    year: match[1],
    month: match[2],
    day: match[3],
    hour: match[4],
    minute: match[5],
    second: match[6] || '00',
  }, timeZone);
}

export function formatDateTimeInput(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  const parts = _getDateTimeParts(date);
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

export function startOfUiDayMs(timestamp = Date.now()) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return null;
  const parts = _getDateTimeParts(date);
  return zonedPartsToUtc({
    year: parts.year,
    month: parts.month,
    day: parts.day,
    hour: '00',
    minute: '00',
    second: '00',
  }).getTime();
}

export function addUiCalendarDaysMs(timestamp, days) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return null;
  const parts = _getDateTimeParts(date);
  const calendar = new Date(Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day) + Number(days || 0),
  ));
  return zonedPartsToUtc({
    year: String(calendar.getUTCFullYear()),
    month: String(calendar.getUTCMonth() + 1).padStart(2, '0'),
    day: String(calendar.getUTCDate()).padStart(2, '0'),
    hour: parts.hour,
    minute: parts.minute,
    second: parts.second,
  }).getTime();
}
