import React from 'react';
import './Stack.css';

function Stack({
  direction = 'column',
  gap = '2',
  align = 'stretch',
  justify = 'flex-start',
  wrap = 'nowrap',
  className,
  as: Component = 'div',
  children,
}) {
  const styles = {
    '--stack-gap': `var(--space-${gap})`,
    flexDirection: direction,
    alignItems: align,
    justifyContent: justify,
    flexWrap: wrap,
  };

  const classes = ['gh-stack', className || ''].filter(Boolean).join(' ');

  return (
    <Component className={classes} style={styles}>
      {children}
    </Component>
  );
}

export default Stack;
