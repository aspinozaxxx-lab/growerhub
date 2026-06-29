import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Surface from '../../../components/ui/Surface';
import Button from '../../../components/ui/Button';
import { useAuth } from '../../../features/auth/AuthContext';
import { isSessionExpiredError } from '../../../api/client';
import {
  adminZigbeePermitJoin,
  adminZigbeeRenameDevice,
  adminZigbeeSetProperty,
  fetchAdminZigbeeOverview,
} from '../../../api/admin';
import './AdminPages.css';

const POLL_INTERVAL_MS = 5000;
const SIMPLE_CONTROL_TYPES = new Set(['binary', 'enum', 'numeric', 'text']);

function formatDateTime(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('ru-RU');
}

function formatValue(value) {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  if (typeof value === 'boolean') {
    return value ? 'Да' : 'Нет';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function formatFeatureValue(feature) {
  const value = formatValue(feature?.value);
  if (value === '-' || !feature?.unit) {
    return value;
  }
  return `${value} ${feature.unit}`;
}

function featureLabel(feature) {
  return feature?.label || feature?.name || feature?.property || feature?.type || '-';
}

function getStateObject(device) {
  return device?.state && typeof device.state === 'object' && !Array.isArray(device.state)
    ? device.state
    : {};
}

function listOrEmpty(value) {
  return Array.isArray(value) ? value : [];
}

function deviceKey(device) {
  return device?.ieee_address || device?.friendly_name || String(device?.id || '');
}

function controlKey(device, feature) {
  return `${deviceKey(device)}:${feature?.property || feature?.name || feature?.type || ''}`;
}

function inputValueFromFeature(feature) {
  if (feature?.value !== null && feature?.value !== undefined) {
    return feature.value;
  }
  if (feature?.type === 'enum' && Array.isArray(feature.values) && feature.values.length > 0) {
    return feature.values[0];
  }
  return '';
}

function featureActionKey(device, feature) {
  return `${controlKey(device, feature)}:set`;
}

function coerceControlValue(feature, value) {
  if (feature?.type !== 'numeric') {
    return value;
  }
  if (value === '') {
    throw new Error('Введите числовое значение');
  }
  const number = Number(value);
  if (Number.isNaN(number)) {
    throw new Error('Введите числовое значение');
  }
  return number;
}

function DeviceThumb({ device }) {
  const [failed, setFailed] = useState(false);
  const label = (device?.friendly_name || device?.type || '?').slice(0, 1).toUpperCase();
  if (!device?.image_url || failed) {
    return <div className="admin-zigbee-thumb admin-zigbee-thumb--fallback">{label}</div>;
  }
  return (
    <img
      className="admin-zigbee-thumb"
      src={device.image_url}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
    />
  );
}

function AdminZigbee() {
  const { token } = useAuth();
  const [overview, setOverview] = useState(null);
  const [renameValues, setRenameValues] = useState({});
  const [controlValues, setControlValues] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [actionKey, setActionKey] = useState('');

  const loadOverview = useCallback(async ({ silent = false } = {}) => {
    if (!silent) {
      setIsLoading(true);
    }
    setError('');
    try {
      const data = await fetchAdminZigbeeOverview(token);
      const normalized = data && typeof data === 'object' ? data : {};
      const devices = Array.isArray(normalized.devices) ? normalized.devices : [];
      setOverview({
        bridge: normalized.bridge || null,
        coordinator: normalized.coordinator || null,
        devices,
        last_command_response: normalized.last_command_response || null,
      });
      setRenameValues((prev) => {
        const next = { ...prev };
        devices.forEach((device) => {
          const key = deviceKey(device);
          if (key && next[key] === undefined) {
            next[key] = device.friendly_name || '';
          }
        });
        return next;
      });
      setControlValues((prev) => {
        const next = { ...prev };
        devices.forEach((device) => {
          listOrEmpty(device.controls).forEach((feature) => {
            const key = controlKey(device, feature);
            if (key && next[key] === undefined) {
              next[key] = inputValueFromFeature(feature);
            }
          });
        });
        return next;
      });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось загрузить Zigbee устройства');
    } finally {
      if (!silent) {
        setIsLoading(false);
      }
    }
  }, [token]);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  useEffect(() => {
    const timerId = window.setInterval(() => {
      loadOverview({ silent: true });
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timerId);
  }, [loadOverview]);

  const devices = useMemo(
    () => (Array.isArray(overview?.devices) ? overview.devices : []),
    [overview],
  );
  const coordinator = overview?.coordinator || devices.find((device) => device.coordinator) || null;
  const bridge = overview?.bridge || {};
  const lastResponse = overview?.last_command_response || null;

  const visibleDevices = useMemo(
    () => devices.filter((device) => !device.coordinator),
    [devices],
  );

  const handlePermitJoin = useCallback(async () => {
    if (actionKey) return;
    setActionKey('permit-join');
    setError('');
    setNotice('');
    try {
      await adminZigbeePermitJoin(254, token);
      setNotice('Сопряжение открыто');
      await loadOverview({ silent: true });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось открыть сопряжение');
    } finally {
      setActionKey('');
    }
  }, [actionKey, loadOverview, token]);

  const handleSetProperty = useCallback(async (device, feature, value) => {
    const key = featureActionKey(device, feature);
    if (actionKey || !device?.ieee_address || !feature?.property) return;
    setActionKey(key);
    setError('');
    setNotice('');
    try {
      const payloadValue = coerceControlValue(feature, value);
      await adminZigbeeSetProperty(device.ieee_address, feature.property, payloadValue, token);
      setNotice(`Команда ${featureLabel(feature)} отправлена`);
      await loadOverview({ silent: true });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось отправить команду устройству');
    } finally {
      setActionKey('');
    }
  }, [actionKey, loadOverview, token]);

  const handleControlValueChange = useCallback((key, value) => {
    setControlValues((prev) => ({
      ...prev,
      [key]: value,
    }));
  }, []);

  const handleRenameChange = useCallback((key, value) => {
    setRenameValues((prev) => ({
      ...prev,
      [key]: value,
    }));
  }, []);

  const handleRename = useCallback(async (device) => {
    const key = deviceKey(device);
    const nextName = (renameValues[key] || '').trim();
    if (actionKey || !device?.ieee_address || !nextName || nextName === device.friendly_name) return;
    setActionKey(`${key}:rename`);
    setError('');
    setNotice('');
    try {
      await adminZigbeeRenameDevice(device.ieee_address, nextName, token);
      setNotice('Команда переименования отправлена');
      await loadOverview({ silent: true });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось переименовать устройство');
    } finally {
      setActionKey('');
    }
  }, [actionKey, loadOverview, renameValues, token]);

  const renderControl = (device, feature) => {
    const key = controlKey(device, feature);
    const currentActionKey = featureActionKey(device, feature);
    const disabled = actionKey === currentActionKey;
    const label = featureLabel(feature);

    if (!SIMPLE_CONTROL_TYPES.has(feature.type)) {
      return (
        <div className="admin-zigbee-control" key={key}>
          <span className="admin-zigbee-control__label">{label}</span>
          <span className="admin-zigbee-control__readonly">{feature.type || 'complex'}: доступно в Z2M</span>
        </div>
      );
    }

    if (feature.type === 'binary') {
      const onValue = feature.value_on ?? 'ON';
      const offValue = feature.value_off ?? 'OFF';
      return (
        <div className="admin-zigbee-control" key={key}>
          <span className="admin-zigbee-control__label">{label}</span>
          <div className="admin-row-actions">
            <Button
              type="button"
              size="sm"
              variant={feature.value === onValue ? 'secondary' : 'primary'}
              disabled={disabled}
              onClick={() => handleSetProperty(device, feature, onValue)}
            >
              {formatValue(onValue)}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={feature.value === offValue ? 'secondary' : 'danger'}
              disabled={disabled}
              onClick={() => handleSetProperty(device, feature, offValue)}
            >
              {formatValue(offValue)}
            </Button>
          </div>
        </div>
      );
    }

    if (feature.type === 'enum') {
      const values = listOrEmpty(feature.values);
      if (values.length <= 3) {
        return (
          <div className="admin-zigbee-control" key={key}>
            <span className="admin-zigbee-control__label">{label}</span>
            <div className="admin-row-actions">
              {values.map((value) => (
                <Button
                  key={String(value)}
                  type="button"
                  size="sm"
                  variant={feature.value === value ? 'secondary' : 'primary'}
                  disabled={disabled}
                  onClick={() => handleSetProperty(device, feature, value)}
                >
                  {formatValue(value)}
                </Button>
              ))}
            </div>
          </div>
        );
      }
      return (
        <div className="admin-zigbee-control" key={key}>
          <span className="admin-zigbee-control__label">{label}</span>
          <div className="admin-row-actions">
            <select
              className="admin-select admin-zigbee-control__input"
              value={controlValues[key] ?? inputValueFromFeature(feature)}
              disabled={disabled}
              onChange={(event) => handleControlValueChange(key, event.target.value)}
            >
              {values.map((value) => (
                <option key={String(value)} value={value}>{formatValue(value)}</option>
              ))}
            </select>
            <Button
              type="button"
              size="sm"
              disabled={disabled}
              onClick={() => handleSetProperty(device, feature, controlValues[key] ?? inputValueFromFeature(feature))}
            >
              Отправить
            </Button>
          </div>
        </div>
      );
    }

    return (
      <div className="admin-zigbee-control" key={key}>
        <span className="admin-zigbee-control__label">{label}</span>
        <div className="admin-row-actions">
          <input
            className="admin-input admin-zigbee-control__input"
            type={feature.type === 'numeric' ? 'number' : 'text'}
            min={feature.value_min ?? undefined}
            max={feature.value_max ?? undefined}
            step={feature.value_step ?? undefined}
            value={controlValues[key] ?? inputValueFromFeature(feature)}
            disabled={disabled}
            onChange={(event) => handleControlValueChange(key, event.target.value)}
          />
          {feature.unit && <span className="admin-zigbee-control__unit">{feature.unit}</span>}
          <Button
            type="button"
            size="sm"
            disabled={disabled}
            onClick={() => handleSetProperty(device, feature, controlValues[key] ?? inputValueFromFeature(feature))}
          >
            Отправить
          </Button>
        </div>
      </div>
    );
  };

  return (
    <div className="admin-page">
      <AppPageHeader title="Zigbee" />
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <div className="admin-error">{error}</div>}
      {notice && <div className="admin-notice">{notice}</div>}

      <Surface variant="card" padding="md" className="admin-section">
        <div className="admin-zigbee-summary">
          <div>
            <h3 className="admin-section__title">Координатор</h3>
            <div className="admin-zigbee-facts">
              <span>Bridge: {formatValue(bridge.state)}</span>
              <span>Base topic: {formatValue(bridge.base_topic)}</span>
              <span>Версия: {formatValue(bridge.version)}</span>
              <span>Permit join: {formatValue(bridge.permit_join)}</span>
              <span>IEEE: {formatValue(coordinator?.ieee_address)}</span>
              <span>Обновлено: {formatDateTime(bridge.updated_at)}</span>
            </div>
          </div>
          <div className="admin-row-actions">
            <Button
              type="button"
              variant="primary"
              onClick={handlePermitJoin}
              disabled={actionKey === 'permit-join'}
            >
              Сопряжение
            </Button>
            <Button type="button" variant="secondary" disabled={isLoading} onClick={() => loadOverview()}>
              Обновить
            </Button>
          </div>
        </div>
        {lastResponse && (
          <div className="admin-zigbee-response">
            <span>{lastResponse.topic}</span>
            <span>{lastResponse.status || '-'}</span>
            {lastResponse.error && <span>{lastResponse.error}</span>}
          </div>
        )}
      </Surface>

      <Surface variant="card" padding="md" className="admin-section">
        <table className="admin-table admin-table--zigbee">
          <thead>
            <tr>
              <th>Устройство</th>
              <th>Статус</th>
              <th>Метрики</th>
              <th>Управление</th>
              <th>Данные</th>
            </tr>
          </thead>
          <tbody>
            {visibleDevices.length === 0 && !isLoading ? (
              <tr>
                <td colSpan="5" className="admin-table__empty">Нет Zigbee устройств</td>
              </tr>
            ) : visibleDevices.map((device) => {
              const key = deviceKey(device);
              const state = getStateObject(device);
              const definition = device.definition || {};
              const metrics = listOrEmpty(device.metrics);
              const controls = listOrEmpty(device.controls);
              const stateMetric = metrics.find((feature) => feature.property === 'state');
              const renameValue = renameValues[key] ?? device.friendly_name ?? '';
              const renameDisabled = !device.ieee_address || device.coordinator || !renameValue.trim()
                || renameValue.trim() === device.friendly_name || actionKey === `${key}:rename`;
              return (
                <tr key={key}>
                  <td>
                    <div className="admin-zigbee-device-card">
                      <DeviceThumb device={device} />
                      <div className="admin-zigbee-device">
                        <strong>{device.friendly_name || '-'}</strong>
                        <span>{[definition.vendor, definition.model].filter(Boolean).join(' / ') || '-'}</span>
                        <span>{definition.description || device.type || '-'}</span>
                        <span>{device.ieee_address || '-'}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div className="admin-zigbee-device">
                      <strong>{formatValue(stateMetric?.value ?? state.state)}</strong>
                      <span>{device.availability || '-'}</span>
                      <span>{device.supported === false ? 'unsupported' : 'supported'}</span>
                      <span>{definition.source || device.type || '-'}</span>
                      <span>{formatDateTime(device.last_state_at || device.updated_at)}</span>
                    </div>
                  </td>
                  <td>
                    <div className="admin-zigbee-metrics">
                      {metrics.length === 0 ? (
                        <span>Нет metadata</span>
                      ) : metrics.map((feature) => (
                        <span key={`${key}:${feature.property}:${feature.name}`}>
                          {featureLabel(feature)}: {formatFeatureValue(feature)}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className="admin-zigbee-actions">
                      {controls.length === 0 ? (
                        <span className="admin-zigbee-control__readonly">Нет writable metadata</span>
                      ) : controls.map((feature) => renderControl(device, feature))}
                      <div className="admin-row-actions">
                        <input
                          className="admin-input admin-zigbee-rename"
                          type="text"
                          value={renameValue}
                          onChange={(event) => handleRenameChange(key, event.target.value)}
                          disabled={device.coordinator}
                        />
                        <Button
                          type="button"
                          size="sm"
                          disabled={renameDisabled}
                          onClick={() => handleRename(device)}
                        >
                          Переименовать
                        </Button>
                      </div>
                    </div>
                  </td>
                  <td>
                    <pre className="admin-mqtt-payload">
                      {JSON.stringify({
                        state: device.state || null,
                        definition: device.definition || null,
                        features: device.features || null,
                      }, null, 2)}
                    </pre>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Surface>
    </div>
  );
}

export default AdminZigbee;
