import React, { useCallback, useEffect, useState } from 'react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Surface from '../../../components/ui/Surface';
import FormField from '../../../components/ui/FormField';
import Button from '../../../components/ui/Button';
import { useAuth } from '../../../features/auth/AuthContext';
import { isSessionExpiredError } from '../../../api/client';
import { fetchAdminMqttMessages } from '../../../api/admin';
import './AdminPages.css';

const LIMIT_OPTIONS = [
  { value: '', label: 'Все' },
  { value: '25', label: '25' },
  { value: '50', label: '50' },
  { value: '100', label: '100' },
  { value: '200', label: '200' },
];

function normalizeFilters(filters) {
  return {
    topic: filters.topic.trim(),
    sender: filters.sender.trim(),
    limit: filters.limit,
  };
}

function formatMessageTime(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('ru-RU');
}

function formatDirection(value) {
  if (value === 'out') {
    return 'Исходящее';
  }
  return 'Входящее';
}

function AdminMqtt() {
  // Translitem: token dlya admin API.
  const { token } = useAuth();
  // Translitem: poslednie MQTT-soobshcheniya.
  const [messages, setMessages] = useState([]);
  // Translitem: tekuschie znacheniya filtrov.
  const [filters, setFilters] = useState({
    topic: '',
    sender: '',
    limit: '',
  });
  // Translitem: filtry, po kotorym zagruzhen tekuschij spisok.
  const [appliedFilters, setAppliedFilters] = useState({
    topic: '',
    sender: '',
    limit: '',
  });
  // Translitem: indikator zagruzki spiska.
  const [isLoading, setIsLoading] = useState(false);
  // Translitem: stroka oshibki dlya vyvoda na stranice.
  const [error, setError] = useState('');

  // Translitem: zagruzhayem poslednie MQTT-soobshcheniya s servera.
  const loadMessages = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await fetchAdminMqttMessages(appliedFilters, token);
      setMessages(Array.isArray(data) ? data : []);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось загрузить MQTT сообщения');
    } finally {
      setIsLoading(false);
    }
  }, [appliedFilters, token]);

  useEffect(() => {
    loadMessages();
  }, [loadMessages]);

  const handleFilterChange = useCallback((field, value) => {
    setFilters((prev) => ({
      ...prev,
      [field]: value,
    }));
  }, []);

  const handleSubmit = useCallback((event) => {
    event.preventDefault();
    setAppliedFilters(normalizeFilters(filters));
  }, [filters]);

  const handleReset = useCallback(() => {
    const nextFilters = {
      topic: '',
      sender: '',
      limit: '',
    };
    setFilters(nextFilters);
    setAppliedFilters(nextFilters);
  }, []);

  return (
    <div className="admin-page">
      <AppPageHeader title="MQTT" />
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <div className="admin-error">{error}</div>}

      <Surface variant="card" padding="md" className="admin-section">
        <form className="admin-form" onSubmit={handleSubmit}>
          <div className="admin-form__row">
            <FormField label="Топик" htmlFor="mqtt-topic">
              <input
                id="mqtt-topic"
                type="text"
                value={filters.topic}
                placeholder="state или gh/dev/device-1"
                onChange={(event) => handleFilterChange('topic', event.target.value)}
              />
            </FormField>
            <FormField label="Отправитель" htmlFor="mqtt-sender">
              <input
                id="mqtt-sender"
                type="text"
                value={filters.sender}
                placeholder="device-id или backend"
                onChange={(event) => handleFilterChange('sender', event.target.value)}
              />
            </FormField>
            <FormField label="Лимит" htmlFor="mqtt-limit">
              <select
                id="mqtt-limit"
                value={filters.limit}
                onChange={(event) => handleFilterChange('limit', event.target.value)}
              >
                {LIMIT_OPTIONS.map((option) => (
                  <option key={option.value || 'all'} value={option.value}>{option.label}</option>
                ))}
              </select>
            </FormField>
          </div>
          <div className="admin-form__actions">
            <Button type="submit" variant="primary" disabled={isLoading}>
              Применить
            </Button>
            <Button type="button" variant="secondary" disabled={isLoading} onClick={handleReset}>
              Сбросить
            </Button>
            <Button type="button" variant="secondary" disabled={isLoading} onClick={loadMessages}>
              Обновить
            </Button>
          </div>
        </form>
      </Surface>

      <Surface variant="card" padding="md" className="admin-section">
        <table className="admin-table admin-table--mqtt">
          <thead>
            <tr>
              <th>Время</th>
              <th>Направление</th>
              <th>Тип</th>
              <th>Топик</th>
              <th>Отправитель</th>
              <th>Payload</th>
            </tr>
          </thead>
          <tbody>
            {messages.length === 0 && !isLoading ? (
              <tr>
                <td colSpan="6" className="admin-table__empty">Нет сообщений</td>
              </tr>
            ) : messages.map((message) => (
              <tr key={message.id}>
                <td>{formatMessageTime(message.received_at)}</td>
                <td>{formatDirection(message.direction)}</td>
                <td>{message.kind || '-'}</td>
                <td className="admin-mqtt-topic">{message.topic || '-'}</td>
                <td>{message.sender || '-'}</td>
                <td>
                  <pre className="admin-mqtt-payload">{message.payload || ''}</pre>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Surface>
    </div>
  );
}

export default AdminMqtt;
