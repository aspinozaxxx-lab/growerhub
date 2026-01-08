import React from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthContext";
import { useDashboardData } from "../../features/dashboard/useDashboardData";
import { useSensorStatsContext } from "../../features/sensors/SensorStatsContext";
import { useWateringSidebar } from "../../features/watering/WateringSidebarContext";
import { fetchPumpWateringStatus } from "../../api/pumps";
import { Title } from "../../components/ui/Typography";
import AppPageState from "../../components/layout/AppPageState";
import DashboardPlantCard from "../../components/plants/DashboardPlantCard";
import "./AppDashboard.css";

function AppDashboard() {
  const { token } = useAuth();
  const { plants, devices, isLoading, error } = useDashboardData();
  const { openSensorStats } = useSensorStatsContext();
  const { openWateringSidebar, setWateringStatus, wateringByPump } = useWateringSidebar();
  const navigate = useNavigate();

  const handleOpenJournal = (plantId) => {
    navigate(`/app/plants/${plantId}/journal`);
  };

  React.useEffect(() => {
    let isCancelled = false;
    async function restoreStatuses() {
      const pumpIds = devices
        .flatMap((device) => Array.isArray(device.pumps) ? device.pumps : [])
        .map((pump) => pump?.id)
        .filter(Boolean);
      const uniquePumpIds = [...new Set(pumpIds)];
      if (!token || uniquePumpIds.length === 0) return;
      await Promise.all(uniquePumpIds.map(async (pumpId) => {
        try {
          const status = await fetchPumpWateringStatus(pumpId, token);
          if (isCancelled) return;
          const startTime = status.start_time || status.started_at || status.startTime || status.startedAt || null;
          const duration = status.duration ?? status.duration_s ?? status.durationS ?? null;
          const isRunning = status.status === 'running';
          if (isRunning && startTime && duration) {
            setWateringStatus(pumpId, {
              startTime,
              duration: Number(duration),
            });
          } else {
            setWateringStatus(pumpId, null);
          }
        } catch (_err) {
          // Translitem: esli status ne udaetsya poluchit' (set'/auth), ne portim lokal'noe sostoyanie.
        }
      }));
    }
    restoreStatuses();
    return () => {
      isCancelled = true;
    };
  }, [devices, setWateringStatus, token]);

  return (
    <div className="dashboard">
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && plants.length === 0 && (
        <AppPageState kind="empty" title="Пока нет данных. Добавьте растения и подключите устройства." />
      )}

      {!isLoading && !error && plants.length > 0 && (
        <div className="dashboard-section-plain">
          <div className="dashboard-section__header">
            <Title level={2}>Растения</Title>
          </div>
          <div className="cards-grid cards-grid--plants">
            {plants.map((plant) => {
              const pumpIds = Array.isArray(plant.pumps) ? plant.pumps.map((pump) => pump.id).filter(Boolean) : [];
              const activePumpId = pumpIds.find((id) => wateringByPump[id]);
              const wateringStatus = activePumpId ? wateringByPump[activePumpId] : null;
              return (
                <DashboardPlantCard
                  key={plant.id}
                  plant={plant}
                  onOpenStats={openSensorStats}
                  onOpenWatering={openWateringSidebar}
                  onOpenJournal={handleOpenJournal}
                  wateringStatus={wateringStatus}
                />
              );
            })}
          </div>
        </div>
      )}

    </div>
  );
}

export default AppDashboard;

