import React from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthContext";
import { useDashboardData } from "../../features/dashboard/useDashboardData";
import { useSensorStatsContext } from "../../features/sensors/SensorStatsContext";
import { useWateringSidebar } from "../../features/watering/WateringSidebarContext";
import { getManualWateringStatus } from "../../api/manualWatering";
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
  const { token } = useAuth();
  const { plants, devices, freeDevices, isLoading, error } = useDashboardData();
  const { openSensorStats } = useSensorStatsContext();
  const { openWateringSidebar, setWateringStatus, wateringByDevice } = useWateringSidebar();
  const navigate = useNavigate();

  const handleOpenJournal = (plantId) => {
    navigate(`/app/plants/${plantId}/journal`);
  };

  const deviceToPlantId = React.useMemo(() => {
    const map = {};
    (plants || []).forEach((plant) => {
      (plant?.devices || []).forEach((device) => {
        const deviceId = device?.device_id;
        if (deviceId) {
          map[deviceId] = plant.id;
        }
      });
    });
    return map;
  }, [plants]);

  React.useEffect(() => {
    // Translitem: vosstanavlivaem status aktivnogo poliva posle obnovleniya stranicy iz servernogo istochnika pravdy.
    let isCancelled = false;
    async function restoreStatuses() {
      if (!token || !Array.isArray(devices) || devices.length === 0) return;
      const ids = devices.map((d) => d?.device_id).filter(Boolean);
      await Promise.all(ids.map(async (deviceId) => {
        try {
          const status = await getManualWateringStatus(deviceId, token);
          if (isCancelled) return;
          const startTime = status.start_time || status.started_at || status.startTime || status.startedAt || null;
          const duration = status.duration ?? status.duration_s ?? status.durationS ?? null;
          const isRunning = status.status === 'running';
          if (isRunning && startTime && duration) {
            setWateringStatus(deviceId, {
              startTime,
              duration: Number(duration),
              plantId: deviceToPlantId[deviceId] || null,
            });
          } else {
            setWateringStatus(deviceId, null);
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
  }, [deviceToPlantId, devices, setWateringStatus, token]);

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
