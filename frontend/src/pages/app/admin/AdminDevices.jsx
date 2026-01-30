import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Surface from '../../../components/ui/Surface';
import Button from '../../../components/ui/Button';
import { isSessionExpiredError } from '../../../api/client';
import {
  adminAssignDevice,
  adminUnassignDevice,
  fetchAdminDevices,
} from '../../../api/admin';
import './AdminPages.css';

// Translitem: admin-stranica upravleniya ustroystvami.
function AdminDevices() {
  // Translitem: sostoyanie spiska ustroystv.
  const [devices, setDevices] = useState([]);
  // Translitem: znacheniya user_id dlya privyazki po id ustroystva.
  const [assignValues, setAssignValues] = useState({});
  // Translitem: indikator zagruzki spiska.
  const [isLoading, setIsLoading] = useState(false);
  // Translitem: stroka oshibki dlya vyvoda na stranice.
  const [error, setError] = useState('');
  // Translitem: indikator vypolneniya strochnogo deystviya.
  const [rowActionId, setRowActionId] = useState(null);

  // Translitem: zagruzhayem spisok ustroystv s servera.
  const loadDevices = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await fetchAdminDevices();
      const list = Array.isArray(data) ? data : [];
      setDevices(list);
      const nextValues = {};
      list.forEach((device) => {
        nextValues[device.id] = '';
      });
      setAssignValues(nextValues);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Ne udalos zagruzit ustroystva');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDevices();
  }, [loadDevices]);

  // Translitem: obnovlyaem pole user_id dlya konkretnoi stroki.
  const handleAssignChange = useCallback((deviceId, value) => {
    setAssignValues((prev) => ({
      ...prev,
      [deviceId]: value,
    }));
  }, []);

  // Translitem: privyazyvaem ustroystvo k polzovatelyu.
  const handleAssign = useCallback(async (deviceId) => {
    if (rowActionId) return;
    const rawValue = assignValues[deviceId] || '';
    const userId = Number(rawValue);
    if (!userId) {
      setError('Ukazhite user_id');
      return;
    }
    setRowActionId(deviceId);
    setError('');
    try {
      await adminAssignDevice(deviceId, userId);
      await loadDevices();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Ne udalos privyazat ustroystvo');
    } finally {
      setRowActionId(null);
    }
  }, [assignValues, loadDevices, rowActionId]);

  // Translitem: otvyazyvaem ustroystvo ot polzovatelya.
  const handleUnassign = useCallback(async (deviceId) => {
    if (rowActionId) return;
    setRowActionId(deviceId);
    setError('');
    try {
      await adminUnassignDevice(deviceId);
      await loadDevices();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Ne udalos otvyazat ustroystvo');
    } finally {
      setRowActionId(null);
    }
  }, [loadDevices, rowActionId]);

  // Translitem: podsobiraem tekst o vladeletse ustroystva.
  const preparedDevices = useMemo(() => devices.map((device) => {
    const owner = device.owner;
    const ownerText = owner ? `${owner.email} (id=${owner.id})` : '-';
    return {
      ...device,
      ownerText,
    };
  }), [devices]);

  return (
    <div className="admin-page">
      <AppPageHeader title="Устройства" />
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <div className="admin-error">{error}</div>}

      <Surface variant="card" padding="md" className="admin-section">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Device ID</th>
              <th>Название</th>
              <th>Владелец</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {preparedDevices.length === 0 && !isLoading ? (
              <tr>
                <td colSpan="5" className="admin-table__empty">Нет данных</td>
              </tr>
            ) : preparedDevices.map((device) => (
              <tr key={device.id}>
                <td>{device.id}</td>
                <td>{device.device_id}</td>
                <td>{device.name || ''}</td>
                <td>{device.ownerText}</td>
                <td>
                  <div className="admin-row-actions">
                    <input
                      className="admin-input"
                      type="number"
                      placeholder="user_id"
                      value={assignValues[device.id] || ''}
                      onChange={(event) => handleAssignChange(device.id, event.target.value)}
                    />
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => handleAssign(device.id)}
                      disabled={rowActionId === device.id}
                    >
                      Привязать
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="danger"
                      onClick={() => handleUnassign(device.id)}
                      disabled={rowActionId === device.id}
                    >
                      Отвязать
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Surface>
    </div>
  );
}

export default AdminDevices;
