import React, { useEffect, useMemo, useState } from 'react';
import { fetchDeviceSettings } from '../../api/devices';
import './EditDeviceModal.css';

function EditDeviceModal({
  device,
  plants,
  onClose,
  onSave,
  isSaving,
  error,
  token,
}) {
  const [wateringSpeed, setWateringSpeed] = useState('');
  const [plantCsv, setPlantCsv] = useState('');
  const [settings, setSettings] = useState(null);
  const [isLoadingSettings, setIsLoadingSettings] = useState(false);
  const [settingsError, setSettingsError] = useState(null);

  useEffect(() => {
    if (!device) {
      setSettings(null);
      return;
    }
    let cancelled = false;
    const load = async () => {
      setIsLoadingSettings(true);
      setSettingsError(null);
      try {
        const data = await fetchDeviceSettings(device.device_id, token);
        if (cancelled) return;
        setSettings(data);
        if (data?.watering_speed_lph !== undefined && data?.watering_speed_lph !== null) {
          setWateringSpeed(String(data.watering_speed_lph));
        } else {
          setWateringSpeed('');
        }
        if (device?.plant_ids && Array.isArray(device.plant_ids)) {
          setPlantCsv(device.plant_ids.join(','));
        } else {
          setPlantCsv('');
        }
      } catch (err) {
        if (!cancelled) {
          setSettingsError(err?.message || 'Ne udalos zagruzit nastroiki');
        }
      } finally {
        if (!cancelled) {
          setIsLoadingSettings(false);
        }
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [device, token]);

  const plantMap = useMemo(() => {
    const map = new Map();
    plants.forEach((plant) => map.set(plant.id, plant.name));
    return map;
  }, [plants]);

  const handleSubmit = (evt) => {
    evt.preventDefault();
    const parsedSpeed = wateringSpeed === '' ? null : Number(wateringSpeed);
    if (parsedSpeed !== null && (Number.isNaN(parsedSpeed) || parsedSpeed <= 0)) {
      return onSave({ error: 'watering_speed_lph must be > 0' });
    }

    const csv = plantCsv.trim();
    const ids = csv === '' ? [] : csv.split(',').map((s) => s.trim()).filter(Boolean);
    const parsedIds = [];
    for (const idStr of ids) {
      const num = Number(idStr);
      if (!Number.isInteger(num) || num <= 0) {
        return onSave({ error: 'plant_ids CSV invalid' });
      }
      parsedIds.push(num);
    }

    const fullSettings = settings ? { ...settings, watering_speed_lph: parsedSpeed } : { watering_speed_lph: parsedSpeed };

    onSave({
      watering_speed_lph: parsedSpeed,
      plant_ids: parsedIds,
      fullSettings,
    });
  };

  if (!device) {
    return null;
  }

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <div>
            <div className="modal__title">Редактирование устройства</div>
            <div className="modal__subtitle">{device.device_id}</div>
          </div>
          <button type="button" className="modal__close" onClick={onClose} aria-label="Закрыть">
            ✕
          </button>
        </div>
        <form className="modal__body" onSubmit={handleSubmit}>
          {isLoadingSettings && <div className="modal__state">Загрузка настроек...</div>}
          {settingsError && <div className="modal__error">{settingsError}</div>}
          <label className="form-control">
            <div className="form-control__label">Скорость полива (л/ч)</div>
            <input
              type="number"
              step="0.1"
              min="0"
              value={wateringSpeed}
              onChange={(e) => {
                setWateringSpeed(e.target.value);
                setSettings((prev) => (prev ? { ...prev, watering_speed_lph: e.target.value === '' ? null : Number(e.target.value) } : prev));
              }}
              placeholder="например, 1.5"
              disabled={isLoadingSettings}
            />
          </label>

          <label className="form-control">
            <div className="form-control__label">plant_ids (CSV)</div>
            <input
              type="text"
              value={plantCsv}
              onChange={(e) => setPlantCsv(e.target.value)}
              placeholder="1,2,3"
              disabled={isLoadingSettings}
            />
            <div className="form-control__hint">
              Доступные id: {plants.map((p) => `${p.id} (${plantMap.get(p.id) || ''})`).join(', ')}
            </div>
          </label>

          {error && <div className="modal__error">{error}</div>}
          {!error && settingsError && <div className="modal__error">{settingsError}</div>}

          <div className="modal__actions">
            <button type="button" className="modal__btn" onClick={onClose} disabled={isSaving}>
              Отмена
            </button>
            <button type="submit" className="modal__btn modal__btn--primary" disabled={isSaving || isLoadingSettings || !settings}>
              {isSaving ? 'Сохранение...' : 'Сохранить'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default EditDeviceModal;
