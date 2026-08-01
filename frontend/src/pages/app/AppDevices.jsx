import React, { useCallback, useEffect, useMemo, useState } from 'react';
import DeviceCard from '../../components/devices/DeviceCard';
import EditDeviceModal from '../../components/devices/EditDeviceModal';
import {
  claimDevice,
  fetchDeviceFirmware,
  fetchMyDevices,
  triggerDeviceFirmwareUpdate,
} from '../../api/devices';
import { fetchPlants } from '../../api/plants';
import { isSessionExpiredError } from '../../api/client';
import { useAuth } from '../../features/auth/AuthContext';
import { useWateringSidebar } from '../../features/watering/WateringSidebarContext';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import AppGrid from '../../components/layout/AppGrid';
import Button from '../../components/ui/Button';
import FormField from '../../components/ui/FormField';
import Surface from '../../components/ui/Surface';
import { Text, Title } from '../../components/ui/Typography';
import AppZigbeeDevices from './AppZigbeeDevices';
import './AppDevices.css';
import { translateApp } from '../../locales/i18n';

const ACTIVE_FIRMWARE_STATES = new Set(['QUEUED', 'DOWNLOADING', 'RESTARTING']);

function isGrovika(device) {
  return /^GROVIKA_[0-9A-F]{6}$/i.test(device?.device_id || '');
}

function AppDevices() {
  const { token } = useAuth();
  const { refreshVersion } = useWateringSidebar();
  const [devices, setDevices] = useState([]);
  const [plants, setPlants] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [modalDevice, setModalDevice] = useState(null);
  const [claimId, setClaimId] = useState('');
  const [claimStatus, setClaimStatus] = useState(null);
  const [isClaiming, setIsClaiming] = useState(false);
  const [retryAfterSeconds, setRetryAfterSeconds] = useState(0);
  const [firmwareByDevice, setFirmwareByDevice] = useState({});
  const [updatingFirmware, setUpdatingFirmware] = useState({});

  useEffect(() => {
    if (retryAfterSeconds <= 0) return undefined;
    const timer = window.setInterval(() => {
      setRetryAfterSeconds((value) => Math.max(0, value - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [retryAfterSeconds]);

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
          setError(err?.message || translateApp("Не удалось загрузить данные"));
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
  }, [refreshVersion, token]);

  const refreshDevices = async () => {
    try {
      const devs = await fetchMyDevices(token);
      setDevices(Array.isArray(devs) ? devs : []);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || translateApp("Не удалось обновить устройства"));
    }
  };

  const refreshFirmware = useCallback(async () => {
    const grovikaDevices = devices.filter(isGrovika);
    if (grovikaDevices.length === 0) return;
    const results = await Promise.allSettled(
      grovikaDevices.map((device) => fetchDeviceFirmware(device.device_id, token)),
    );
    setFirmwareByDevice((current) => {
      const next = { ...current };
      grovikaDevices.forEach((device, index) => {
        const result = results[index];
        if (result.status === 'fulfilled') {
          next[device.device_id] = { ...result.value, load_error: null };
        } else {
          next[device.device_id] = {
            ...next[device.device_id],
            load_error: result.reason?.message || translateApp('Не удалось проверить прошивку'),
          };
        }
      });
      return next;
    });
  }, [devices, token]);

  const hasActiveFirmwareUpdate = useMemo(
    () => Object.values(firmwareByDevice).some((item) => ACTIVE_FIRMWARE_STATES.has(item?.status)),
    [firmwareByDevice],
  );

  useEffect(() => {
    refreshFirmware();
  }, [refreshFirmware]);

  useEffect(() => {
    if (devices.filter(isGrovika).length === 0) return undefined;
    const timer = window.setInterval(
      refreshFirmware,
      hasActiveFirmwareUpdate ? 3000 : 30000,
    );
    return () => window.clearInterval(timer);
  }, [devices, hasActiveFirmwareUpdate, refreshFirmware]);

  const handleFirmwareUpdate = async (device) => {
    const deviceId = device?.device_id;
    if (!deviceId || updatingFirmware[deviceId]) return;
    setUpdatingFirmware((current) => ({ ...current, [deviceId]: true }));
    setFirmwareByDevice((current) => ({
      ...current,
      [deviceId]: { ...current[deviceId], action_error: null },
    }));
    try {
      const result = await triggerDeviceFirmwareUpdate(deviceId, token);
      setFirmwareByDevice((current) => ({
        ...current,
        [deviceId]: {
          ...current[deviceId],
          status: 'QUEUED',
          target_version: result?.version || current[deviceId]?.latest_version,
          action_error: null,
        },
      }));
      await refreshFirmware();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setFirmwareByDevice((current) => ({
        ...current,
        [deviceId]: {
          ...current[deviceId],
          action_error: err?.message || translateApp('Не удалось запустить обновление'),
        },
      }));
    } finally {
      setUpdatingFirmware((current) => ({ ...current, [deviceId]: false }));
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

  const handleClaim = async (event) => {
    event.preventDefault();
    if (!claimId.trim() || isClaiming || retryAfterSeconds > 0) return;
    setIsClaiming(true);
    setClaimStatus(null);
    try {
      const normalizedId = claimId.trim().toUpperCase();
      const claimed = await claimDevice(normalizedId, token);
      await refreshDevices();
      setClaimId('');
      setRetryAfterSeconds(0);
      setClaimStatus({
        kind: 'success',
        message: translateApp('Устройство {{value1}} добавлено', {
          value1: claimed?.device_id || normalizedId,
        }),
      });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      if (err?.status === 404) {
        setClaimStatus({ kind: 'error', message: translateApp('Устройство с таким ID не найдено') });
      } else if (err?.status === 409) {
        setClaimStatus({
          kind: 'error',
          message: err.message || translateApp('Устройство уже используется другим пользователем — обратитесь к администратору'),
        });
      } else if (err?.status === 429) {
        const seconds = Math.max(1, err.retryAfterSeconds || 60);
        setRetryAfterSeconds(seconds);
        setClaimStatus({ kind: 'error', message: translateApp('Лимит попыток исчерпан') });
      } else {
        setClaimStatus({ kind: 'error', message: err?.message || translateApp('Не удалось добавить устройство') });
      }
    } finally {
      setIsClaiming(false);
    }
  };

  const retryMessage = retryAfterSeconds > 0
    ? translateApp(
      retryAfterSeconds >= 60 ? 'Повторить через {{value1}} мин.' : 'Повторить через {{value1}} сек.',
      { value1: retryAfterSeconds >= 60 ? Math.ceil(retryAfterSeconds / 60) : retryAfterSeconds },
    )
    : null;

  return (
    <div className="app-devices">
      <AppPageHeader title={translateApp("Устройства")} />
      <Surface variant="card" padding="md" className="device-claim-card">
        <div className="device-claim-card__intro">
          <Title level={3}>{translateApp('Добавить Grovika')}</Title>
          <Text tone="muted">
            {translateApp('Введите ID, напечатанный на коробке и указанный в веб-интерфейсе устройства.')}
          </Text>
        </div>
        <form className="device-claim-form" onSubmit={handleClaim}>
          <FormField
            label={translateApp('ID устройства')}
            htmlFor="device-claim-id"
            hint={translateApp('Формат: GROVIKA_XXXXXX')}
          >
            <input
              id="device-claim-id"
              type="text"
              value={claimId}
              onChange={(event) => {
                setClaimId(event.target.value.toUpperCase());
                setClaimStatus(null);
              }}
              placeholder="GROVIKA_040AB1"
              autoComplete="off"
              autoCapitalize="characters"
              maxLength={14}
              pattern="GROVIKA_[0-9A-Fa-f]{6}"
              required
              disabled={isClaiming || retryAfterSeconds > 0}
            />
          </FormField>
          <Button
            type="submit"
            variant="primary"
            isLoading={isClaiming}
            disabled={!claimId.trim() || retryAfterSeconds > 0}
          >
            {translateApp('Добавить устройство')}
          </Button>
        </form>
        {claimStatus ? (
          <div
            className={`device-claim-message device-claim-message--${claimStatus.kind}`}
            role={claimStatus.kind === 'error' ? 'alert' : 'status'}
          >
            <Text>{claimStatus.message}</Text>
          </div>
        ) : null}
        {retryMessage ? (
          <div className="device-claim-retry" role="status"><Text>{retryMessage}</Text></div>
        ) : null}
      </Surface>
      {isLoading && <AppPageState kind="loading" title={translateApp("Загрузка...")} />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && devices.length === 0 && (
        <AppPageState kind="empty" title={translateApp("Пока нет ваших устройств.")} />
      )}

      <AppGrid min={280}>
        {devices.map((device) => (
          <DeviceCard
            key={device.id}
            device={device}
            onEdit={() => handleOpenModal(device)}
            firmwareStatus={firmwareByDevice[device.device_id] || null}
            isFirmwareUpdating={Boolean(updatingFirmware[device.device_id])}
            onFirmwareUpdate={() => handleFirmwareUpdate(device)}
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
      <AppZigbeeDevices embedded />
    </div>
  );
}

export default AppDevices;
