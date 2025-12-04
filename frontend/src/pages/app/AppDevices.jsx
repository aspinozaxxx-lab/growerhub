import React from 'react';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import './AppDevices.css';

// Простая заглушка с примером открытия панели статистики по device_id.
function AppDevices() {
  const { openSensorStats } = useSensorStatsContext();
  const demoDeviceId = 'demo-device-123';

  const openMetric = (metric) => {
    openSensorStats({ deviceId: demoDeviceId, metric, deviceName: 'Demo device' });
  };

  return (
    <div className="app-devices">
      <h2>Устройства</h2>
      <p className="app-devices__hint">
        Здесь появится список реальных устройств. Клик по метрике карточки должен вызывать
        <code> openSensorStats({ '{ deviceId, metric }' })</code> с строковым <code>device_id</code>.
      </p>
      <div className="app-devices__card">
        <div className="app-devices__card-header">
          <div>
            <div className="app-devices__title">Demo device</div>
            <div className="app-devices__subtitle">{demoDeviceId}</div>
          </div>
        </div>
        <div className="app-devices__metrics">
          <button type="button" onClick={() => openMetric('air_temperature')} className="app-devices__metric">
            🌡 Температура
          </button>
          <button type="button" onClick={() => openMetric('air_humidity')} className="app-devices__metric">
            💧 Влажность воздуха
          </button>
          <button type="button" onClick={() => openMetric('soil_moisture')} className="app-devices__metric">
            🪴 Влажность почвы
          </button>
          <button type="button" onClick={() => openMetric('watering')} className="app-devices__metric">
            🚰 История поливов
          </button>
        </div>
      </div>
    </div>
  );
}

export default AppDevices;
