import React from 'react';
import './FormField.css';
import './form-controls.css';

function FormField({ label, error, hint, children, htmlFor, layout = 'column', className }) {
  const targetId = htmlFor || (React.isValidElement(children) ? children.props.id : undefined);
  const control = React.isValidElement(children)
    ? React.cloneElement(children, {
        className: [children.props.className, 'gh-control'].filter(Boolean).join(' '),
        id: children.props.id || targetId,
      })
    : children;

  const wrapperClass = [
    'gh-field',
    layout === 'row' ? 'gh-row' : '',
    className || '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={wrapperClass}>
      {label ? <label className="gh-label" htmlFor={targetId}>{label}</label> : null}
      {control}
      {hint ? <div className="gh-hint">{hint}</div> : null}
      {error ? <div className="gh-error">{error}</div> : null}
    </div>
  );
}

export default FormField;
