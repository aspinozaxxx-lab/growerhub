import React from 'react';
import './SensorPill.css';

function SensorPill({
  icon = null,
  label = null,
  value,
  unit = null,
  variant = 'button',
  isHighlight = false,
  disabled = false,
  onClick,
}) {
  const isClickable = typeof onClick === 'function';
  const Element = isClickable || variant === 'button' ? 'button' : 'div';
  const classNames = [
    'sensor-pill',
    isHighlight ? 'is-highlight' : '',
    isClickable ? 'is-clickable' : '',
    disabled ? 'is-disabled' : '',
  ]
    .filter(Boolean)
    .join(' ');

  const content = (
    <>
      {icon ? <span className="sensor-pill__icon" aria-hidden="true">{icon}</span> : null}
      <div className="sensor-pill__body">
        {label ? <span className="sensor-pill__label">{label}</span> : null}
        <span className="sensor-pill__value">
          {value}
          {unit ? <span className="sensor-pill__unit">{unit}</span> : null}
        </span>
      </div>
    </>
  );

  if (Element === 'button') {
    return (
      <Element type="button" className={classNames} onClick={onClick} disabled={disabled || !isClickable}>
        {content}
      </Element>
    );
  }

  return <Element className={classNames}>{content}</Element>;
}

export default SensorPill;
