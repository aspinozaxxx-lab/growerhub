import React, { useEffect, useState } from 'react';
import DeviceCard from '../../components/devices/DeviceCard';
import EditDeviceModal from '../../components/devices/EditDeviceModal';
import { fetchMyDevices } from '../../api/devices';
import { fetchPlants } from '../../api/plants';
import { isSessionExpiredError } from '../../api/client';
import { useAuth } from '../../features/auth/AuthContext';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import AppGrid from '../../components/layout/AppGrid';
import './AppDevices.css';

function AppDevices() {
  const { token } = useAuth();
  const [devices, setDevices] = useState([]);
  const [plants, setPlants] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [modalDevice, setModalDevice] = useState(null);

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
          if (isSessionExpiredError(err)) return;
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

  const refreshDevices = async () => {
    try {
      const devs = await fetchMyDevices(token);
      setDevices(Array.isArray(devs) ? devs : []);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Ne udalos obnovit ustrojstva');
    }
  };

  const handleOpenModal = (device) => {
    setModalDevice(device);
  };

  const handleCloseModal = () => {
    setModalDevice(null);
  };

  const handleSaved = async () => {
    await refreshDevices();
    handleCloseModal();
  };

  return (
    <div className="app-devices">
      <AppPageHeader title="Устройства" />
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && devices.length === 0 && (
        <AppPageState kind="empty" title="Пока нет ваших устройств." />
      )}

      <AppGrid min={280}>
        {devices.map((device) => (
          <DeviceCard
            key={device.id}
            device={device}
            onEdit={() => handleOpenModal(device)}
          />
        ))}
      </AppGrid>

      {modalDevice && (
        <EditDeviceModal
          device={modalDevice}
          plants={plants}
          onClose={handleCloseModal}
          onSaved={handleSaved}
          token={token}
        />
      )}
    </div>
  );
}

export default AppDevices;
