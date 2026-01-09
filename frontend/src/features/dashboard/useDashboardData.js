import { useEffect, useState } from 'react';
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
          const nextPlants = Array.isArray(plantsPayload)
            ? plantsPayload.filter((plant) => !plant?.harvested_at)
            : [];
          setPlants(nextPlants);
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


  return {
    plants,
    devices,
    isLoading,
    error,
  };
}
