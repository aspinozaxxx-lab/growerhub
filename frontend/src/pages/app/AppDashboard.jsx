import React from "react";
import { useNavigate } from "react-router-dom";
import { useDashboardData } from "../../features/dashboard/useDashboardData";
import { useSensorStatsContext } from "../../features/sensors/SensorStatsContext";
import { useWateringSidebar } from "../../features/watering/WateringSidebarContext";
import SensorPill from "../../components/ui/sensor-pill/SensorPill";
import Button from "../../components/ui/Button";
import Surface from "../../components/ui/Surface";
import { Title, Text } from "../../components/ui/Typography";
import AppPageState from "../../components/layout/AppPageState";
import DashboardPlantCard from "../../components/plants/DashboardPlantCard";
import "./AppDashboard.css";

function MetricPill({ kind, value, metric, deviceId, onOpenStats, highlight = false }) {
  const handleClick = () => {
    if (deviceId && metric) {
      onOpenStats({ deviceId, metric });
    }
  };
  return (
    <SensorPill
      kind={kind}
      value={value}
      onClick={handleClick}
      highlight={highlight}
      disabled={!deviceId || !metric}
    />
  );
}

function FreeDeviceCard({ device, onOpenStats }) {
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
        <MetricPill
          kind="air_temperature"
          value={device.air_temperature}
          metric="air_temperature"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          kind="air_humidity"
          value={device.air_humidity}
          metric="air_humidity"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          kind="soil_moisture"
          value={device.soil_moisture}
          metric="soil_moisture"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
        />
        <MetricPill
          kind="watering"
          value={device.is_watering}
          metric="watering"
          deviceId={device.device_id}
          onOpenStats={onOpenStats}
          highlight={Boolean(device.is_watering)}
        />
      </div>

      {/* TODO: dobavit' knopku privyazki ustrojstva k rasteniyu */}
    </div>
  );
}

function AppDashboard() {
  const { plants, devices, freeDevices, isLoading, error } = useDashboardData();
  const { openSensorStats } = useSensorStatsContext();
  const { openWateringSidebar, wateringByDevice } = useWateringSidebar();
  const navigate = useNavigate();

  const handleOpenJournal = (plantId) => {
    navigate(`/app/plants/${plantId}/journal`);
  };

  return (
    <div className="dashboard">
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && plants.length === 0 && freeDevices.length === 0 && (
        <AppPageState kind="empty" title="Пока нет данных. Добавьте растения и подключите устройства." />
      )}

      {!isLoading && !error && plants.length > 0 && (
        <Surface variant="section" padding="md" className="dashboard-section">
          <div className="dashboard-section__header">
            <Title level={2}>Растения</Title>
          </div>
          <div className="cards-grid">
            {plants.map((plant) => {
              const deviceKey = plant?.devices?.[0]?.device_id;
              return (
                <DashboardPlantCard
                  key={plant.id}
                  plant={plant}
                  onOpenStats={openSensorStats}
                  onOpenWatering={openWateringSidebar}
                  onOpenJournal={handleOpenJournal}
                  wateringStatus={deviceKey ? wateringByDevice[deviceKey] : null}
                />
              );
            })}
          </div>
        </Surface>
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