export const MINUTES_PER_DAY = 24 * 60;

export function timeToMinutes(value) {
  if (typeof value !== 'string' || !/^\d{2}:\d{2}(:00)?$/.test(value)) return null;
  const [hours, minutes] = value.split(':').map(Number);
  return hours < 24 && minutes < 60 ? hours * 60 + minutes : null;
}

export function minutesToTime(value) {
  const minutes = ((Math.round(value) % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
}

export function lightDuration(start, end) {
  return ((end - start + MINUTES_PER_DAY) % MINUTES_PER_DAY) || MINUTES_PER_DAY;
}

export function moveLightInterval(start, end, delta) {
  return { start_time: minutesToTime(start + delta), end_time: minutesToTime(end + delta) };
}
