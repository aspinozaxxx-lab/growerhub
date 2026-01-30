import React, { useCallback, useEffect, useMemo, useState } from 'react';
import AppPageHeader from '../../../components/layout/AppPageHeader';
import AppPageState from '../../../components/layout/AppPageState';
import Surface from '../../../components/ui/Surface';
import { isSessionExpiredError } from '../../../api/client';
import { fetchAdminPlants } from '../../../api/admin';
import './AdminPages.css';

// Translitem: admin-stranica prosmotra rasteniy.
function AdminPlants() {
  // Translitem: sostoyanie spiska rasteniy.
  const [plants, setPlants] = useState([]);
  // Translitem: indikator zagruzki spiska.
  const [isLoading, setIsLoading] = useState(false);
  // Translitem: stroka oshibki dlya vyvoda na stranice.
  const [error, setError] = useState('');

  // Translitem: zagruzhayem spisok rasteniy s servera.
  const loadPlants = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await fetchAdminPlants();
      const list = Array.isArray(data) ? data : [];
      setPlants(list);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Ne udalos zagruzit rasteniya');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPlants();
  }, [loadPlants]);

  // Translitem: formiruem tekst dlya kolонки "Vladelets".
  const preparedPlants = useMemo(() => plants.map((plant) => {
    const ownerText = plant.owner_email ? `${plant.owner_email} (id=${plant.owner_id || ''})` : '-';
    return {
      ...plant,
      ownerText,
    };
  }), [plants]);

  return (
    <div className="admin-page">
      <AppPageHeader title="Растения" />
      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <div className="admin-error">{error}</div>}

      <Surface variant="card" padding="md" className="admin-section">
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Имя</th>
              <th>Владелец</th>
              <th>Группа</th>
            </tr>
          </thead>
          <tbody>
            {preparedPlants.length === 0 && !isLoading ? (
              <tr>
                <td colSpan="4" className="admin-table__empty">Нет данных</td>
              </tr>
            ) : preparedPlants.map((plant) => (
              <tr key={plant.id}>
                <td>{plant.id}</td>
                <td>{plant.name}</td>
                <td>{plant.ownerText}</td>
                <td>{plant.group_name || ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Surface>
    </div>
  );
}

export default AdminPlants;
