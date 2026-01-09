import React, { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useDashboardData } from "../../features/dashboard/useDashboardData";
import { useSensorStatsContext } from "../../features/sensors/SensorStatsContext";
import { useWateringSidebar } from "../../features/watering/WateringSidebarContext";
import { Title } from "../../components/ui/Typography";
import AppPageState from "../../components/layout/AppPageState";
import DashboardPlantCard from "../../components/plants/DashboardPlantCard";
import "./AppDashboard.css";

function AppDashboard() {
  const { plants, isLoading, error } = useDashboardData();
  const { openSensorStats } = useSensorStatsContext();
  const { openWateringSidebar } = useWateringSidebar();
  const navigate = useNavigate();

  const handleOpenJournal = (plantId) => {
    navigate(`/app/plants/${plantId}/journal`);
  };

  const groupedPlants = useMemo(() => {
    const map = new Map();
    plants.forEach((plant) => {
      const groupName = plant?.plant_group?.name || 'Без группы';
      if (!map.has(groupName)) {
        map.set(groupName, []);
      }
      map.get(groupName).push(plant);
    });
    const entries = Array.from(map.entries());
    entries.sort((a, b) => {
      if (a[0] === 'Без группы') return 1;
      if (b[0] === 'Без группы') return -1;
      return a[0].localeCompare(b[0], 'ru');
    });
    return entries;
  }, [plants]);

  return (
    <div className="dashboard">
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && plants.length === 0 && (
        <AppPageState kind="empty" title="Пока нет данных. Добавьте растения и подключите устройства." />
      )}

      {!isLoading && !error && plants.length > 0 && (
        <div className="dashboard-section-plain">
          {groupedPlants.map(([groupName, groupPlants]) => (
            <div key={groupName} className="dashboard-group">
              <div className="dashboard-group__title">
                <Title level={3}>{groupName}</Title>
              </div>
              <div className="cards-grid cards-grid--plants">
                {groupPlants.map((plant) => (
                  <DashboardPlantCard
                    key={plant.id}
                    plant={plant}
                    onOpenStats={openSensorStats}
                    onOpenWatering={openWateringSidebar}
                    onOpenJournal={handleOpenJournal}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

    </div>
  );
}

export default AppDashboard;

