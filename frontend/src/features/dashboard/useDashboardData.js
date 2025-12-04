import { useEffect, useMemo, useState } from 'react';
import { fetchPlants } from '../../api/plants';
import { fetchMyDevices } from '../../api/devices';
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

  const freeDevices = useMemo(
    () => devices.filter((device) => !device.plant_ids || device.plant_ids.length === 0),
    [devices],
  );

  return {
    plants,
    devices,
    freeDevices,
    isLoading,
    error,
  };
}
