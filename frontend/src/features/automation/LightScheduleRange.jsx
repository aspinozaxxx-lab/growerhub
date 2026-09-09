import { useRef } from 'react';
import { GripVertical, MoveHorizontal } from 'lucide-react';
import { translateApp as t } from '../../locales/i18n';
import { lightDuration, MINUTES_PER_DAY, minutesToTime, moveLightInterval, timeToMinutes } from './lightSchedule';
import './LightScheduleRange.css';

export default function LightScheduleRange({ name, config, disabled, onChange, onSelect }) {
  const trackRef = useRef(null);
  const dragRef = useRef(null);
  const start = timeToMinutes(config.start_time) ?? 360;
  const end = timeToMinutes(config.end_time) ?? 1320;
  const duration = lightDuration(start, end);
  const segments = duration === MINUTES_PER_DAY
    ? [[0, MINUTES_PER_DAY]]
    : (end > start ? [[start, end]] : [[0, end], [start, MINUTES_PER_DAY]]);
  const largestSegment = segments.reduce((largest, segment) => (
    segment[1] - segment[0] > largest[1] - largest[0] ? segment : largest
  ));

  const change = (kind, initialStart, initialEnd, delta) => {
    onChange(kind === 'interval'
      ? moveLightInterval(initialStart, initialEnd, delta)
      : { [kind === 'start' ? 'start_time' : 'end_time']: minutesToTime(
        (kind === 'start' ? initialStart : initialEnd) + delta,
      ) });
  };

  const beginDrag = (event, kind) => {
    if (disabled || event.button !== 0) return;
    event.preventDefault();
    onSelect();
    const width = trackRef.current.getBoundingClientRect().width;
    if (!width) return;
    dragRef.current = { kind, start, end, x: event.clientX, width };
    event.currentTarget.focus();
    trackRef.current.setPointerCapture?.(event.pointerId);
  };

  const moveDrag = (event) => {
    const drag = dragRef.current;
    if (!drag) return;
    const delta = Math.round((event.clientX - drag.x) / drag.width * MINUTES_PER_DAY / 5) * 5;
    change(drag.kind, drag.start, drag.end, delta);
  };

  const finishDrag = (event, cancel = false) => {
    const drag = dragRef.current;
    dragRef.current = null;
    if (cancel && drag) onChange(moveLightInterval(drag.start, drag.end, 0));
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const keyChange = (event, kind) => {
    const value = kind === 'end' ? end : start;
    const steps = {
      ArrowLeft: event.shiftKey ? -15 : -1,
      ArrowDown: event.shiftKey ? -15 : -1,
      ArrowRight: event.shiftKey ? 15 : 1,
      ArrowUp: event.shiftKey ? 15 : 1,
      PageDown: -60,
      PageUp: 60,
      Home: -value,
      End: MINUTES_PER_DAY - 1 - value,
    };
    if (steps[event.key] === undefined || disabled) return;
    event.preventDefault();
    onSelect();
    change(kind, start, end, steps[event.key]);
  };

  const controls = (kind) => ({
    type: 'button',
    role: 'slider',
    disabled,
    'aria-valuemin': 0,
    'aria-valuemax': MINUTES_PER_DAY - 1,
    'aria-valuenow': kind === 'end' ? end : start,
    'aria-valuetext': kind === 'interval'
      ? `${minutesToTime(start)} — ${minutesToTime(end)}`
      : minutesToTime(kind === 'end' ? end : start),
    title: kind === 'interval' ? `${minutesToTime(start)} — ${minutesToTime(end)}` : minutesToTime(kind === 'end' ? end : start),
    onPointerDown: (event) => beginDrag(event, kind),
    onKeyDown: (event) => keyChange(event, kind),
  });

  return (
    <div className={`light-range ${duration === MINUTES_PER_DAY ? 'is-all-day' : ''}`} ref={trackRef}
      onPointerMove={moveDrag} onPointerUp={finishDrag} onPointerCancel={(event) => finishDrag(event, true)}
      onLostPointerCapture={() => { dragRef.current = null; }}>
      <div className="light-range__grid" aria-hidden="true"><i /><i /><i /><i /><i /></div>
      <div className="light-range__night" aria-hidden="true" />
      {segments.filter(([from, to]) => to > from).map((segment) => (
        <button
          key={segment[0] === 0 ? 'early' : 'late'}
          {...controls('interval')}
          className="light-range__interval"
          style={{ left: `${segment[0] / MINUTES_PER_DAY * 100}%`, width: `${(segment[1] - segment[0]) / MINUTES_PER_DAY * 100}%` }}
          aria-label={t('Перенести интервал: {{name}}', { name })}
        >
          {segment === largestSegment ? (
            <span className="light-range__grip" aria-hidden="true">
              <GripVertical size={16} /><MoveHorizontal size={17} />
              <span>{t('{{hours}} ч света', { hours: Math.floor(duration / 60) })}
                {duration % 60 ? ` ${t('{{minutes}} мин', { minutes: duration % 60 })}` : ''}</span>
            </span>
          ) : null}
        </button>
      ))}
      <button
        {...controls('start')}
        className="light-range__handle light-range__handle--start"
        style={{ left: `${start / MINUTES_PER_DAY * 100}%` }}
        aria-label={t('Начало освещения: {{name}}', { name })}
      />
      <button
        {...controls('end')}
        className="light-range__handle light-range__handle--end"
        style={{ left: `${(end === 0 && start !== end ? MINUTES_PER_DAY : end) / MINUTES_PER_DAY * 100}%` }}
        aria-label={t('Конец освещения: {{name}}', { name })}
      />
    </div>
  );
}
