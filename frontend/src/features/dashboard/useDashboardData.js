import { useEffect, useMemo, useState } from 'react';
import { fetchPlants } from '../../api/plants';
import { fetchMyDevices } from '../../api/devices';
import { isSessionExpiredError } from '../../api/client';
import { useAuth } from '../auth/AuthContext';

export function useDashboardData() {
  const { token } = useAuth();
  const [plants, setPlants] = useState([]);
  const [devices, setDevices] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isCancelled = false;
    async function load() {
      setIsLoading(true);
      setError(null);
      try {
        const [plantsPayload, devicesPayload] = await Promise.all([
          fetchPlants(token),
          fetchMyDevices(token),
        ]);
        if (!isCancelled) {
          setPlants(Array.isArray(plantsPayload) ? plantsPayload : []);
          setDevices(Array.isArray(devicesPayload) ? devicesPayload : []);
        }
      } catch (err) {
        if (!isCancelled) {
          if (isSessionExpiredError(err)) return;
          setError(err?.message || 'Не удалось загрузить данные');
        }
      } finally {
        if (!isCancelled) {
          setIsLoading(false);
        }
      }
    }
    load();
    return () => {
      isCancelled = true;
    };
  }, [token]);

  const freeDevices = useMemo(() => {
    return devices.filter((device) => {
      const sensors = Array.isArray(device.sensors) ? device.sensors : [];
      const pumps = Array.isArray(device.pumps) ? device.pumps : [];
      const hasSensorBindings = sensors.some((sensor) => Array.isArray(sensor.bound_plants) && sensor.bound_plants.length > 0);
      const hasPumpBindings = pumps.some((pump) => Array.isArray(pump.bound_plants) && pump.bound_plants.length > 0);
      return !hasSensorBindings && !hasPumpBindings;
    });
  }, [devices]);

  return {
    plants,
    devices,
    freeDevices,
    isLoading,
    error,
  };
}
