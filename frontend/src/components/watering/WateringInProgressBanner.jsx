import React, { useMemo, useState } from 'react';
/* eslint-disable react-refresh/only-export-components */
import Button from '../ui/Button';
import './WateringInProgressBanner.css';
import { translateApp } from '../../locales/i18n';

function formatRemaining(seconds) {
  if (seconds === null || seconds === undefined) {
    return '—:--';
  }
  const numeric = Number(seconds);
  if (!Number.isFinite(numeric)) {
    return '—:--';
  }
  const safeSeconds = Math.max(0, Math.floor(numeric));
  const minutes = Math.floor(safeSeconds / 60);
  const secs = safeSeconds % 60;
  return `${minutes}:${String(secs).padStart(2, '0')}`;
}

function WateringInProgressBanner({ isWatering, remainingSeconds, onStop, disabled = false }) {
  const [isStopping, setIsStopping] = useState(false);
  const label = useMemo(() => formatRemaining(remainingSeconds), [remainingSeconds]);

  if (!isWatering) {
    return null;
  }

  const handleStop = async () => {
    if (!onStop || disabled || isStopping) {
      return;
    }
    setIsStopping(true);
    try {
      await onStop();
    } finally {
      setIsStopping(false);
    }
  };

  return (
    <div className="watering-progress" role="status" aria-live="polite">
      <span className="watering-progress__text">{translateApp("Полив еще")} {label}</span>
      <Button
        variant="ghost"
        size="sm"
        onClick={handleStop}
        disabled={disabled}
        isLoading={isStopping}
      >{translateApp("Остановить")}</Button>
    </div>
  );
}

export { formatRemaining };
export default WateringInProgressBanner;
