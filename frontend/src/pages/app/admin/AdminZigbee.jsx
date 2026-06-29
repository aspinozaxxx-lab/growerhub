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
  adminZigbeeSetState,
  fetchAdminZigbeeOverview,
} from '../../../api/admin';
import './AdminPages.css';

const POLL_INTERVAL_MS = 5000;
const QUICK_METRICS = [
  ['state', 'Статус'],
  ['power', 'Мощность'],
  ['current', 'Ток'],
  ['voltage', 'Напряжение'],
  ['energy', 'Энергия'],
  ['linkquality', 'LQI'],
];

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

function getStateObject(device) {
  return device?.state && typeof device.state === 'object' && !Array.isArray(device.state)
    ? device.state
    : {};
}

function collectExposeItems(items = []) {
  const result = [];
  items.forEach((item) => {
    if (!item || typeof item !== 'object') return;
    result.push(item);
    if (Array.isArray(item.features)) {
      result.push(...collectExposeItems(item.features));
    }
  });
  return result;
}

function hasWritableState(device) {
  if (device?.coordinator) {
    return false;
  }
  const exposes = device?.bridge_device?.definition?.exposes;
  if (!Array.isArray(exposes)) {
    return false;
  }
  return collectExposeItems(exposes).some((item) => (
    item.property === 'state' && typeof item.access === 'number' && (item.access & 2) === 2
  ));
}

function deviceKey(device) {
  return device?.ieee_address || device?.friendly_name || String(device?.id || '');
}

function AdminZigbee() {
  // Translitem: token dlya admin API.
  const { token } = useAuth();
  // Translitem: Zigbee2MQTT snapshot iz backend.
  const [overview, setOverview] = useState(null);
  // Translitem: lokalnye input znacheniya dlya rename.
  const [renameValues, setRenameValues] = useState({});
  // Translitem: sostoyanie zagruzki i deystvij.
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

  const handleSetState = useCallback(async (device, state) => {
    const key = deviceKey(device);
    if (actionKey || !device?.ieee_address) return;
    setActionKey(`${key}:state`);
    setError('');
    setNotice('');
    try {
      await adminZigbeeSetState(device.ieee_address, state, token);
      setNotice(`Команда ${state} отправлена`);
      await loadOverview({ silent: true });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось отправить команду устройству');
    } finally {
      setActionKey('');
    }
  }, [actionKey, loadOverview, token]);

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
              <th>Состояние</th>
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
              const writableState = hasWritableState(device);
              const renameValue = renameValues[key] ?? device.friendly_name ?? '';
              const renameDisabled = !device.ieee_address || device.coordinator || !renameValue.trim()
                || renameValue.trim() === device.friendly_name || actionKey === `${key}:rename`;
              return (
                <tr key={key}>
                  <td>
                    <div className="admin-zigbee-device">
                      <strong>{device.friendly_name || '-'}</strong>
                      <span>{device.ieee_address || '-'}</span>
                      <span>{device.type || '-'}</span>
                    </div>
                  </td>
                  <td>
                    <div className="admin-zigbee-device">
                      <strong>{formatValue(state.state)}</strong>
                      <span>{device.availability || '-'}</span>
                      <span>{formatDateTime(device.last_state_at || device.updated_at)}</span>
                    </div>
                  </td>
                  <td>
                    <div className="admin-zigbee-metrics">
                      {QUICK_METRICS.map(([name, label]) => (
                        <span key={name}>
                          {label}: {formatValue(state[name])}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className="admin-zigbee-actions">
                      {writableState && (
                        <div className="admin-row-actions">
                          <Button
                            type="button"
                            size="sm"
                            variant={state.state === 'ON' ? 'secondary' : 'primary'}
                            disabled={actionKey === `${key}:state`}
                            onClick={() => handleSetState(device, 'ON')}
                          >
                            Вкл
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant={state.state === 'OFF' ? 'secondary' : 'danger'}
                            disabled={actionKey === `${key}:state`}
                            onClick={() => handleSetState(device, 'OFF')}
                          >
                            Выкл
                          </Button>
                        </div>
                      )}
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
                        bridge_device: device.bridge_device || null,
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
