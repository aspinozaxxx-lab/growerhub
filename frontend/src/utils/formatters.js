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

// Translitem: vremennaya zona dlya otobrazheniya dat/ vremeni v UI.
// TODO(translit): kogda poyavitsya nastroika polzovatelya, podmenit na ee znachenie.
const UI_TIME_ZONE = 'Europe/Moscow';
const UI_LOCALE = 'ru-RU';

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
function _getDateTimeParts(date) {
  const formatter = new Intl.DateTimeFormat(UI_LOCALE, {
    timeZone: UI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
  const parts = formatter.formatToParts(date);
  const result = {};
  for (const part of parts) {
    if (part.type === 'year' || part.type === 'month' || part.type === 'day' || part.type === 'hour' || part.type === 'minute') {
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

export function formatTimestampLabel(timestamp) {
  const date = parseBackendTimestamp(timestamp);
  if (!date) return '';
  const { day, month, hour, minute } = _getDateTimeParts(date);
  if (!day || !month || !hour || !minute) return '';
  return `${day}.${month} ${hour}:${minute}`;
}

export function formatTimeHHMM(dateOrString) {
  const date = parseBackendTimestamp(dateOrString);
  if (!date) return '';
  const { hour, minute } = _getDateTimeParts(date);
  if (!hour || !minute) return '';
  return `${hour}:${minute}`;
}
