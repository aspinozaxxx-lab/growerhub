import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { startManualWatering, getManualWateringStatus } from '../../api/manualWatering';
import { useWateringSidebar } from './WateringSidebarContext';
import FormField from '../../components/ui/FormField';
import '../sensors/SensorStatsSidebar.css';
import './WateringSidebar.css';

function WateringSidebar() {
  const {
    isOpen,
    deviceId,
    plantId,
    closeWateringSidebar,
    setWateringStatus,
  } = useWateringSidebar();
  const { token } = useAuth();
  const [waterVolume, setWaterVolume] = useState(0.5);
  const [ph, setPh] = useState('');
  const [fertilizers, setFertilizers] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      setError(null);
      setSuccess(false);
      return;
    }
    setSuccess(false);
  }, [isOpen]);

  const title = useMemo(() => {
    return deviceId ? `Полив устройства ${deviceId}` : 'Полив';
  }, [deviceId]);

  if (!isOpen) {
    return null;
  }

  const handleStart = async () => {
    if (!deviceId) {
      return;
    }
    setIsSubmitting(true);
    setError(null);
    setSuccess(false);
    try {
      await startManualWatering({
        deviceId,
        waterVolumeL: waterVolume,
        ph: ph === '' ? null : Number(ph),
        fertilizersPerLiter: fertilizers || null,
        token,
      });
      const status = await getManualWateringStatus(deviceId, token);
      const startTime = status.start_time || status.started_at || status.startTime || status.startedAt || null;
      const duration = status.duration ?? status.duration_s ?? status.durationS ?? null;
      if (startTime && duration) {
        setWateringStatus(deviceId, { startTime, duration: Number(duration), plantId });
      }
      setSuccess(true);
    } catch (err) {
      setError(err?.message || 'Ne udalos startovat poliv');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={`sensor-sidebar ${isOpen ? 'is-open' : ''}`}>
      <button className="sensor-sidebar__backdrop" type="button" onClick={closeWateringSidebar} aria-label="Zakryt" />
      <aside className="sensor-sidebar__panel" aria-label="Nastroiki poliva">
        <header className="sensor-sidebar__header">
          <div>
            <div className="sensor-sidebar__metric">{title}</div>
            {plantId && <div className="sensor-sidebar__device">Plant #{plantId}</div>}
          </div>
          <button
            type="button"
            className="sensor-sidebar__close"
            onClick={closeWateringSidebar}
            aria-label="Zakryt nastroiki poliva"
          >
            ?
          </button>
        </header>

        <div className="sensor-sidebar__body">
          <FormField label="Obem vody, l" htmlFor="water-volume">
            <input
              id="water-volume"
              type="range"
              min="0.1"
              max="2.0"
              step="0.1"
              value={waterVolume}
              onChange={(e) => setWaterVolume(Number(e.target.value))}
            />
          </FormField>
          <div className="gh-hint">{waterVolume.toFixed(1)} l</div>

          <FormField label="pH (opcionalno)" htmlFor="water-ph">
            <input
              id="water-ph"
              type="number"
              step="0.1"
              value={ph}
              onChange={(e) => setPh(e.target.value)}
              placeholder="6.5"
            />
          </FormField>

          <FormField label="Udobreniya na litr (opcionalno)" htmlFor="water-fertilizers">
            <textarea
              id="water-fertilizers"
              rows={3}
              value={fertilizers}
              onChange={(e) => setFertilizers(e.target.value)}
              placeholder="NPK 10-10-10"
            />
          </FormField>

          {error && <div className="sensor-sidebar__state sensor-sidebar__state--error">{error}</div>}
          {success && <div className="sensor-sidebar__state">Poliv zapushchen</div>}

          <button
            type="button"
            className="hero-cta"
            onClick={handleStart}
            disabled={isSubmitting}
            style={{ width: '100%', marginTop: 12 }}
          >
            {isSubmitting ? 'Zapusk...' : 'Nachat poliv'}
          </button>
        </div>
      </aside>
    </div>
  );
}

export default WateringSidebar;
