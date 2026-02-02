import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { startPumpWatering, fetchPumpWateringStatus } from '../../api/pumps';
import { isSessionExpiredError } from '../../api/client';
import { useWateringSidebar } from './WateringSidebarContext';
import FormField from '../../components/ui/FormField';
import SidePanel from '../../components/ui/SidePanel';
import Button from '../../components/ui/Button';
import '../sensors/SensorStatsSidebar.css';
import './WateringSidebar.css';

function WateringSidebar() {
  const {
    isOpen,
    pumpId,
    pumpLabel,
    plantId,
    wateringAdvice,
    wateringPrevious,
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
    if (wateringPrevious && wateringPrevious.water_volume_l !== null && wateringPrevious.water_volume_l !== undefined) {
      setWaterVolume(Number(wateringPrevious.water_volume_l));
    } else {
      setWaterVolume(0.5);
    }
    if (wateringPrevious && wateringPrevious.ph !== null && wateringPrevious.ph !== undefined) {
      setPh(String(wateringPrevious.ph));
    } else {
      setPh('');
    }
    if (wateringPrevious && wateringPrevious.fertilizers_per_liter) {
      setFertilizers(wateringPrevious.fertilizers_per_liter);
    } else {
      setFertilizers('');
    }
  }, [isOpen, wateringPrevious]);

  const title = useMemo(() => {
    if (pumpLabel) {
      return `Полив: ${pumpLabel}`;
    }
    return pumpId ? `Полив насоса #${pumpId}` : 'Полив';
  }, [pumpId, pumpLabel]);

  if (!isOpen) {
    return null;
  }

  const handleStart = async () => {
    if (!pumpId) {
      return;
    }
    setIsSubmitting(true);
    setError(null);
    setSuccess(false);
    try {
      await startPumpWatering({
        pumpId,
        waterVolumeL: waterVolume,
        ph: ph === '' ? null : Number(ph),
        fertilizersPerLiter: fertilizers || null,
        token,
      });
      const status = await fetchPumpWateringStatus(pumpId, token);
      const startTime = status.start_time || status.started_at || status.startTime || status.startedAt || null;
      const duration = status.duration ?? status.duration_s ?? status.durationS ?? null;
      if (startTime && duration) {
        setWateringStatus(pumpId, { startTime, duration: Number(duration) });
      }
      setSuccess(true);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось запустить полив');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SidePanel
      isOpen={isOpen}
      onClose={closeWateringSidebar}
      title={title}
      subtitle={plantId ? `Растение №${plantId}` : ''}
    >
      <FormField label="Объем воды, l" htmlFor="water-volume">
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
      {wateringAdvice && (
        <div className="watering-recommendation">
          Rekomendaciya: {wateringAdvice.recommended_water_volume_l ?? '—'} l
        </div>
      )}

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
      {wateringAdvice && (
        <div className="watering-recommendation">
          Rekomendaciya: {wateringAdvice.recommended_ph ?? '—'}
        </div>
      )}

      <FormField label="Udobreniya na litr (opcionalno)" htmlFor="water-fertilizers">
        <textarea
          id="water-fertilizers"
          rows={3}
          value={fertilizers}
          onChange={(e) => setFertilizers(e.target.value)}
          placeholder="NPK 10-10-10"
        />
      </FormField>
      {wateringAdvice && (
        <div className="watering-recommendation">
          Rekomendaciya: {wateringAdvice.recommended_fertilizers_per_liter ?? '—'}
        </div>
      )}

      {error && <div className="sensor-sidebar__state sensor-sidebar__state--error">{error}</div>}
      {success && <div className="sensor-sidebar__state">Полив запущен</div>}

      <Button variant="primary" onClick={handleStart} disabled={isSubmitting} style={{ width: '100%', marginTop: 12 }}>
        {isSubmitting ? 'Запуск...' : 'Начать полив'}
      </Button>
    </SidePanel>
  );
}

export default WateringSidebar;
