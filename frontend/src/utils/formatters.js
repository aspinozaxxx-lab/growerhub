export function formatSensorValue(value, fractionDigits = 1) {
  if (value === null || value === undefined) {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return '—';
  }
  return num.toFixed(fractionDigits);
}

export function formatTimestampLabel(timestamp) {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const hours = `${date.getHours()}`.padStart(2, '0');
  const minutes = `${date.getMinutes()}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  return `${day}.${month} ${hours}:${minutes}`;
}

export function formatTimeHHMM(dateOrString) {
  const date = typeof dateOrString === 'string' ? new Date(dateOrString) : dateOrString;
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    return '';
  }
  const hours = `${date.getHours()}`.padStart(2, '0');
  const minutes = `${date.getMinutes()}`.padStart(2, '0');
  return `${hours}:${minutes}`;
}
