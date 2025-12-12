import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPlants, fetchPlantGroups } from '../../api/plants';
import { fetchMyDevices } from '../../api/devices';
import { useAuth } from '../../features/auth/AuthContext';
import PlantCard from '../../components/plants/PlantCard';
import PlantEditDialog from '../../components/plants/PlantEditDialog';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import AppGrid from '../../components/layout/AppGrid';
import Button from '../../components/ui/Button';
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
      <AppPageHeader
        title="Растения"
        right={(
          <Button type="button" variant="primary" onClick={handleOpenCreate}>
            Добавить растение
          </Button>
        )}
      />

      {isLoading && <AppPageState kind="loading" title="Загрузка..." />}
      {error && <AppPageState kind="error" title={error} />}

      {!isLoading && !error && plants.length === 0 && (
        <AppPageState kind="empty" title="Растения не найдены" />
      )}

      {!isLoading && !error && plants.length > 0 && (
        <AppGrid min={320}>
          {plants.map((plant) => (
            <PlantCard
              key={plant.id}
              plant={plant}
              onEdit={handleOpenEdit}
              onOpenJournal={handleOpenJournal}
            />
          ))}
        </AppGrid>
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
