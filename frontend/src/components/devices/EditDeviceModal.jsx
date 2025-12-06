import React, { useEffect, useMemo, useState } from 'react';
import './EditDeviceModal.css';

function EditDeviceModal({
  device,
  plants,
  onClose,
  onSave,
  isSaving,
  error,
}) {
  const [wateringSpeed, setWateringSpeed] = useState('');
  const [plantCsv, setPlantCsv] = useState('');

  useEffect(() => {
    if (device?.watering_speed_lph !== undefined && device?.watering_speed_lph !== null) {
      setWateringSpeed(String(device.watering_speed_lph));
    } else {
      setWateringSpeed('');
    }
    if (device?.plant_ids && Array.isArray(device.plant_ids)) {
      setPlantCsv(device.plant_ids.join(','));
    } else {
      setPlantCsv('');
    }
  }, [device]);

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
    // Validate CSV
    for (const idStr of ids) {
      const num = Number(idStr);
      if (!Number.isInteger(num) || num <= 0) {
        return onSave({ error: 'plant_ids CSV invalid' });
      }
      parsedIds.push(num);
    }

    onSave({
      watering_speed_lph: parsedSpeed,
      plant_ids: parsedIds,
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
          <label className="form-control">
            <div className="form-control__label">Скорость полива (л/ч)</div>
            <input
              type="number"
              step="0.1"
              min="0"
              value={wateringSpeed}
              onChange={(e) => setWateringSpeed(e.target.value)}
              placeholder="например, 1.5"
            />
          </label>

          <label className="form-control">
            <div className="form-control__label">plant_ids (CSV)</div>
            <input
              type="text"
              value={plantCsv}
              onChange={(e) => setPlantCsv(e.target.value)}
              placeholder="1,2,3"
            />
            <div className="form-control__hint">
              Доступные id: {plants.map((p) => `${p.id} (${plantMap.get(p.id) || ''})`).join(', ')}
            </div>
          </label>

          {error && <div className="modal__error">{error}</div>}

          <div className="modal__actions">
            <button type="button" className="modal__btn" onClick={onClose} disabled={isSaving}>
              Отмена
            </button>
            <button type="submit" className="modal__btn modal__btn--primary" disabled={isSaving}>
              {isSaving ? 'Сохранение...' : 'Сохранить'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default EditDeviceModal;
