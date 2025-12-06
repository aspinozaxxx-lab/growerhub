import React, { useEffect, useMemo, useState } from 'react';
import DeviceCard from '../../components/devices/DeviceCard';
import EditDeviceModal from '../../components/devices/EditDeviceModal';
import { fetchMyDevices, updateDeviceSettings, assignDeviceToPlant, unassignDeviceFromPlant } from '../../api/devices';
import { fetchPlants } from '../../api/plants';
import { useAuth } from '../../features/auth/AuthContext';
import './AppDevices.css';

function AppDevices() {
  const { token } = useAuth();
  const [devices, setDevices] = useState([]);
  const [plants, setPlants] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [modalDevice, setModalDevice] = useState(null);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const [devs, plantsList] = await Promise.all([
          fetchMyDevices(token),
          fetchPlants(token),
        ]);
        if (!cancelled) {
          setDevices(Array.isArray(devs) ? devs : []);
          setPlants(Array.isArray(plantsList) ? plantsList : []);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err?.message || 'Ne udalos zagruzit dannye');
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [token]);

  const plantsById = useMemo(() => {
    const map = new Map();
    plants.forEach((p) => map.set(p.id, p));
    return map;
  }, [plants]);

  const refreshDevices = async () => {
    try {
      const devs = await fetchMyDevices(token);
      setDevices(Array.isArray(devs) ? devs : []);
    } catch (err) {
      setError(err?.message || 'Ne udalos obnovit ustrojstva');
    }
  };

  const handleOpenModal = (device) => {
    setSaveError(null);
    setModalDevice(device);
  };

  const handleCloseModal = () => {
    setModalDevice(null);
    setSaveError(null);
  };

  const handleSave = async ({ watering_speed_lph, plant_ids, error: validationError }) => {
    if (validationError) {
      setSaveError(validationError);
      return;
    }
    if (!modalDevice) {
      return;
    }
    setIsSaving(true);
    setSaveError(null);
    try {
      // watering_speed_lph
      const currentSpeed = modalDevice.watering_speed_lph ?? null;
      if (watering_speed_lph !== currentSpeed) {
        await updateDeviceSettings(modalDevice.device_id, { watering_speed_lph }, token);
      }
      // plant diff
      const current = new Set(modalDevice.plant_ids || []);
      const next = new Set(plant_ids || []);
      const toAdd = [...next].filter((id) => !current.has(id));
      const toRemove = [...current].filter((id) => !next.has(id));
      for (const pid of toAdd) {
        await assignDeviceToPlant(modalDevice.id, pid, token);
      }
      for (const pid of toRemove) {
        await unassignDeviceFromPlant(modalDevice.id, pid, token);
      }
      await refreshDevices();
      handleCloseModal();
    } catch (err) {
      setSaveError(err?.message || 'Ne udalos sohranit');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="app-devices">
      <div className="app-devices__header">
        <h2>Устройства</h2>
      </div>
      {isLoading && <div className="app-devices__state">Загрузка...</div>}
      {error && <div className="app-devices__state app-devices__state--error">{error}</div>}

      {!isLoading && !error && devices.length === 0 && (
        <div className="app-devices__state">Пока нет устройств.</div>
      )}

      <div className="app-devices__grid">
        {devices.map((device) => (
          <DeviceCard
            key={device.id}
            device={device}
            plantsById={plantsById}
            onEdit={() => handleOpenModal(device)}
          />
        ))}
      </div>

      {modalDevice && (
        <EditDeviceModal
          device={modalDevice}
          plants={plants}
          onClose={handleCloseModal}
          onSave={handleSave}
          isSaving={isSaving}
          error={saveError}
        />
      )}
    </div>
  );
}

export default AppDevices;
