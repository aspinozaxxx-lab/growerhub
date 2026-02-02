import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Surface from '../../../components/ui/Surface';
import FormField from '../../../components/ui/FormField';
import Button from '../../../components/ui/Button';
import { useAuth } from '../../../features/auth/AuthContext';
import { isSessionExpiredError } from '../../../api/client';
import {
  createAdminUser,
  deleteAdminUser,
  fetchAdminUsers,
  updateAdminUser,
} from '../../../api/admin';
import './AdminPages.css';

// Translitem: dostupnye roli dlya select v admin-polzovatelyah.
const ROLE_OPTIONS = [
  { value: 'user', label: 'user' },
  { value: 'admin', label: 'admin' },
];

// Translitem: formiruem nachalnye drafty dlya redaktirovaniya polzovateley.
const buildDraftsFromUsers = (list) => {
  const drafts = {};
  (list || []).forEach((user) => {
    drafts[user.id] = {
      username: user.username || '',
      role: user.role || 'user',
      is_active: Boolean(user.is_active),
    };
  });
  return drafts;
};

// Translitem: admin-stranica upravleniya polzovatelyami.
function AdminUsers() {
  // Translitem: token dlya admin API.
  const { token } = useAuth();
  // Translitem: sostoyanie spiska polzovateley.
  const [users, setUsers] = useState([]);
  // Translitem: drafty redaktirovaniya polzovateley po id.
  const [drafts, setDrafts] = useState({});
  // Translitem: indikator zagruzki spiska.
  const [isLoading, setIsLoading] = useState(false);
  // Translitem: stroka oshibki dlya vyvoda na stranice.
  const [error, setError] = useState('');
  // Translitem: sostoyanie formy sozdaniya polzovatelya.
  const [createForm, setCreateForm] = useState({
    email: '',
    username: '',
    role: 'user',
    password: '',
  });
  // Translitem: indikator vypolneniya zaprosa sozdaniya.
  const [isCreating, setIsCreating] = useState(false);
  // Translitem: indikator vypolneniya strochnyh deystviy.
  const [rowActionId, setRowActionId] = useState(null);

  // Translitem: zagruzhayem spisok polzovateley s servera.
  const loadUsers = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await fetchAdminUsers(token);
      const list = Array.isArray(data) ? data : [];
      setUsers(list);
      setDrafts(buildDraftsFromUsers(list));
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось загрузить пользователей');
    } finally {
      setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  // Translitem: obnovlyaem draft polzovatelya pri izmenenii polya.
  const handleDraftChange = useCallback((userId, field, value) => {
    setDrafts((prev) => ({
      ...prev,
      [userId]: {
        ...(prev[userId] || {}),
        [field]: value,
      },
    }));
  }, []);

  // Translitem: obnovlyaem polya formy sozdaniya polzovatelya.
  const handleCreateChange = useCallback((field, value) => {
    setCreateForm((prev) => ({
      ...prev,
      [field]: value,
    }));
  }, []);

  // Translitem: obrabotchik sozdaniya polzovatelya.
  const handleCreateSubmit = useCallback(async (event) => {
    event.preventDefault();
    if (isCreating) return;
    setIsCreating(true);
    setError('');
    try {
      await createAdminUser({
        email: createForm.email.trim(),
        username: createForm.username.trim(),
        role: createForm.role,
        password: createForm.password,
      }, token);
      setCreateForm({
        email: '',
        username: '',
        role: 'user',
        password: '',
      });
      await loadUsers();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось создать пользователя');
    } finally {
      setIsCreating(false);
    }
  }, [createForm, isCreating, loadUsers, token]);

  // Translitem: sohranyaem izmeneniya po polzovatelyu.
  const handleSave = useCallback(async (userId) => {
    if (rowActionId) return;
    setRowActionId(userId);
    setError('');
    try {
      const draft = drafts[userId] || {};
      await updateAdminUser(userId, {
        username: draft.username || '',
        role: draft.role || 'user',
        is_active: Boolean(draft.is_active),
      }, token);
      await loadUsers();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось сохранить изменения');
    } finally {
      setRowActionId(null);
    }
  }, [drafts, loadUsers, rowActionId, token]);

  // Translitem: udalyaem polzovatelya.
  const handleDelete = useCallback(async (userId) => {
    if (rowActionId) return;
    setRowActionId(userId);
    setError('');
    try {
      await deleteAdminUser(userId, token);
      await loadUsers();
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось удалить пользователя');
    } finally {
      setRowActionId(null);
    }
  }, [loadUsers, rowActionId, token]);

  // Translitem: dostupnost tekuschih draft-dannyh po polzovatelyam.
  const preparedUsers = useMemo(() => users.map((user) => ({
    ...user,
    draft: drafts[user.id] || {
      username: user.username || '',
      role: user.role || 'user',
      is_active: Boolean(user.is_active),
    },
  })), [drafts, users]);

  return (
    <div className="admin-page">
      <AppPageHeader title="Пользователи" />
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <div className="admin-error">{error}</div>}

      <Surface variant="card" padding="md" className="admin-section">
        <h3 className="admin-section__title">Создать пользователя</h3>
        <form className="admin-form" onSubmit={handleCreateSubmit}>
          <div className="admin-form__row">
            <FormField label="Email" htmlFor="create-email">
              <input
                id="create-email"
                type="email"
                value={createForm.email}
                onChange={(event) => handleCreateChange('email', event.target.value)}
                required
              />
            </FormField>
            <FormField label="Username" htmlFor="create-username">
              <input
                id="create-username"
                type="text"
                value={createForm.username}
                onChange={(event) => handleCreateChange('username', event.target.value)}
              />
            </FormField>
            <FormField label="Роль" htmlFor="create-role">
              <select
                id="create-role"
                value={createForm.role}
                onChange={(event) => handleCreateChange('role', event.target.value)}
              >
                {ROLE_OPTIONS.map((item) => (
                  <option key={item.value} value={item.value}>{item.label}</option>
                ))}
              </select>
            </FormField>
            <FormField label="Пароль" htmlFor="create-password">
              <input
                id="create-password"
                type="password"
                value={createForm.password}
                onChange={(event) => handleCreateChange('password', event.target.value)}
                required
              />
            </FormField>
          </div>
          <div className="admin-form__actions">
            <Button type="submit" variant="primary" disabled={isCreating}>
              {isCreating ? 'Создаем...' : 'Создать'}
            </Button>
          </div>
        </form>
      </Surface>

      <Surface variant="card" padding="md" className="admin-section">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Email</th>
              <th>Username</th>
              <th>Роль</th>
              <th>Активен</th>
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {preparedUsers.length === 0 && !isLoading ? (
              <tr>
                <td colSpan="6" className="admin-table__empty">Нет данных</td>
              </tr>
            ) : preparedUsers.map((user) => (
              <tr key={user.id}>
                <td>{user.id}</td>
                <td>{user.email}</td>
                <td>
                  <input
                    className="admin-input"
                    type="text"
                    value={user.draft.username}
                    onChange={(event) => handleDraftChange(user.id, 'username', event.target.value)}
                  />
                </td>
                <td>
                  <select
                    className="admin-select"
                    value={user.draft.role}
                    onChange={(event) => handleDraftChange(user.id, 'role', event.target.value)}
                  >
                    {ROLE_OPTIONS.map((item) => (
                      <option key={item.value} value={item.value}>{item.label}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <input
                    type="checkbox"
                    checked={user.draft.is_active}
                    onChange={(event) => handleDraftChange(user.id, 'is_active', event.target.checked)}
                  />
                </td>
                <td>
                  <div className="admin-row-actions">
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => handleSave(user.id)}
                      disabled={rowActionId === user.id}
                    >
                      Сохранить
                    </Button>
                    <Button
                      type="button"
                      variant="danger"
                      size="sm"
                      onClick={() => handleDelete(user.id)}
                      disabled={rowActionId === user.id}
                    >
                      Удалить
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

export default AdminUsers;
