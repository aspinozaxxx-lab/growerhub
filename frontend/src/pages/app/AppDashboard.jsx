import React from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthContext";
import { useDashboardData } from "../../features/dashboard/useDashboardData";
import { useSensorStatsContext } from "../../features/sensors/SensorStatsContext";
import { useWateringSidebar } from "../../features/watering/WateringSidebarContext";
import { fetchPumpWateringStatus } from "../../api/pumps";
import SensorPill from "../../components/ui/sensor-pill/SensorPill";
import Button from "../../components/ui/Button";
import Surface from "../../components/ui/Surface";
import { Title, Text } from "../../components/ui/Typography";
import AppPageState from "../../components/layout/AppPageState";
import DashboardPlantCard from "../../components/plants/DashboardPlantCard";
import "./AppDashboard.css";

const SENSOR_KIND_MAP = {
  SOIL_MOISTURE: 'soil_moisture',
  AIR_TEMPERATURE: 'air_temperature',
  AIR_HUMIDITY: 'air_humidity',
};

const SENSOR_TITLE_MAP = {
  SOIL_MOISTURE: 'Влажность почвы',
  AIR_TEMPERATURE: 'Температура воздуха',
  AIR_HUMIDITY: 'Влажность воздуха',
};

function MetricPill({ sensor, deviceName, onOpenStats }) {
  const kind = SENSOR_KIND_MAP[sensor.type] || 'soil_moisture';
  const title = SENSOR_TITLE_MAP[sensor.type] || sensor.type || 'Датчик';
  const handleClick = () => {
    if (sensor?.id) {
      onOpenStats({
        mode: 'sensor',
        sensorId: sensor.id,
        metric: kind,
        title,
        subtitle: deviceName,
      });
    }
  };
  return (
    <SensorPill
      kind={kind}
      value={sensor.last_value}
      onClick={handleClick}
      disabled={!sensor?.id}
    />
  );
}

function FreeDeviceCard({ device, onOpenStats }) {
  const sensors = Array.isArray(device.sensors) ? device.sensors : [];
  const pumps = Array.isArray(device.pumps) ? device.pumps : [];
  return (
    <div className="free-device-card">
      <div className="free-device-card__header">
        <div>
          <div className="free-device-card__name">{device.name || device.device_id}</div>
          <div className="free-device-card__id">{device.device_id}</div>
          <div className="free-device-card__status">
            {device.is_online ? "Онлайн" : "Оффлайн"}
            <span className={`dashboard-status-dot ${device.is_online ? "is-online" : "is-offline"}`} aria-hidden="true" />
          </div>
        </div>
        <div className="free-device-card__tag">Не привязано</div>
      </div>

      <div className="free-device-card__metrics">
        {sensors.length === 0 && <div className="free-device-card__empty">Нет датчиков</div>}
        {sensors.map((sensor) => (
          <MetricPill key={sensor.id} sensor={sensor} deviceName={device.name || device.device_id} onOpenStats={onOpenStats} />
        ))}
      </div>

      {pumps.length > 0 && (
        <div className="free-device-card__pumps">
          {pumps.map((pump) => (
            <div key={pump.id} className="free-device-card__pump">Насос · канал {pump.channel ?? '-'}</div>
          ))}
        </div>
      )}
    </div>
  );
}

function AppDashboard() {
  const { token } = useAuth();
  const { plants, devices, freeDevices, isLoading, error } = useDashboardData();
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

      {!isLoading && !error && plants.length === 0 && freeDevices.length === 0 && (
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

      {!isLoading && !error && freeDevices.length > 0 && (
        <Surface variant="section" padding="md" className="dashboard-section">
          <div className="dashboard-section__header">
            <Title level={2}>Свободные устройства</Title>
            <Text tone="muted" className="dashboard-section__subtitle">Не привязаны к растениям</Text>
          </div>
          <div className="cards-grid">
            {freeDevices.map((device) => (
              <FreeDeviceCard key={device.id} device={device} onOpenStats={openSensorStats} />
            ))}
          </div>
        </Surface>
      )}
    </div>
  );
}

export default AppDashboard;

