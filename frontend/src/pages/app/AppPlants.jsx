
import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPlants, fetchPlantGroups } from '../../api/plants';
import { fetchMyDevices } from '../../api/devices';
import { useAuth } from '../../features/auth/AuthContext';
import PlantCard from '../../components/plants/PlantCard';
import PlantEditDialog from '../../components/plants/PlantEditDialog';
import './AppPlants.css';

// Translitem: Stranica spiska rastenij s kartochkami i rabochim dialogom redaktirovaniya.
function AppPlants() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [plants, setPlants] = useState([]);
  const [plantGroups, setPlantGroups] = useState([]);
  const [devices, setDevices] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create');
  const [selectedPlant, setSelectedPlant] = useState(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [plantsPayload, groupsPayload, devicesPayload] = await Promise.all([
        fetchPlants(token),
        fetchPlantGroups(token),
        fetchMyDevices(token),
      ]);
      setPlants(Array.isArray(plantsPayload) ? plantsPayload : []);
      setPlantGroups(Array.isArray(groupsPayload) ? groupsPayload : []);
      setDevices(Array.isArray(devicesPayload) ? devicesPayload : []);
    } catch (err) {
      setError(err?.message || 'Ne udalos zagruzit rasteniya');
    } finally {
      setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleOpenJournal = (plant) => {
    navigate(`/app/plants/${plant.id}/journal`);
  };

  const handleOpenCreate = () => {
    setDialogMode('create');
    setSelectedPlant(null);
    setDialogOpen(true);
  };

  const handleOpenEdit = (plant) => {
    setDialogMode('edit');
    setSelectedPlant(plant);
    setDialogOpen(true);
  };

  const handleSaved = async () => {
    await loadData();
    setDialogOpen(false);
  };

  return (
    <div className="app-plants">
      <div className="app-plants__header">
        <h2>Растения</h2>
        <button type="button" className="app-plants__add" onClick={handleOpenCreate}>
          Добавить растение
        </button>
      </div>

      {isLoading && <div className="app-plants__state">Загрузка...</div>}
      {error && <div className="app-plants__state app-plants__state--error">{error}</div>}

      {!isLoading && !error && plants.length === 0 && (
        <div className="app-plants__state">Растения не найдены</div>
      )}

      {!isLoading && !error && plants.length > 0 && (
        <div className="app-plants__grid">
          {plants.map((plant) => (
            <PlantCard
              key={plant.id}
              plant={plant}
              onEdit={handleOpenEdit}
              onOpenJournal={handleOpenJournal}
            />
          ))}
        </div>
      )}

      <PlantEditDialog
        isOpen={dialogOpen}
        mode={dialogMode}
        plant={selectedPlant}
        plants={plants}
        plantGroups={plantGroups}
        devices={devices}
        onClose={() => setDialogOpen(false)}
        onSaved={handleSaved}
      />
    </div>
  );
}

export default AppPlants;
