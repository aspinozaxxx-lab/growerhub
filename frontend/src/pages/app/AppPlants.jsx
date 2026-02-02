import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPlants, fetchPlantGroups, harvestPlant } from '../../api/plants';
import { isSessionExpiredError } from '../../api/client';
import { useAuth } from '../../features/auth/AuthContext';
import { useSensorStatsContext } from '../../features/sensors/SensorStatsContext';
import PlantCard from '../../components/plants/PlantCard';
import PlantEditDialog from '../../components/plants/PlantEditDialog';
import PlantAvatar from '../../components/plant-avatar/PlantAvatar';
import SensorPill from '../../components/ui/sensor-pill/SensorPill';
import Surface from '../../components/ui/Surface';
import { Title, Text } from '../../components/ui/Typography';
import Modal from '../../components/ui/Modal';
import FormField from '../../components/ui/FormField';
import AppPageHeader from '../../components/layout/AppPageHeader';
import AppPageState from '../../components/layout/AppPageState';
import AppGrid from '../../components/layout/AppGrid';
import Button from '../../components/ui/Button';
import { DEFAULT_PLANT_TYPE_ID, getAutoStageFromAge, normalizePlantTypeId } from '../../domain/plants';
import { formatDateKeyYYYYMMDD, parseBackendTimestamp } from '../../utils/formatters';
import './AppPlants.css';

// Translitem: Stranica spiska rastenij s kartochkami i rabochim dialogom redaktirovaniya.
const MS_IN_DAY = 24 * 60 * 60 * 1000;

const METRIC_LABELS = {
  air_temperature: 'Температура воздуха',
  air_humidity: 'Влажность воздуха',
  soil_moisture: 'Влажность почвы',
  watering: 'Поливы',
};

function formatPlantDate(value) {
  const date = parseBackendTimestamp(value);
  if (!date) return '-';
  return date.toLocaleDateString('ru-RU');
}

function calcAgeAtHarvest(plantedAt, harvestedAt) {
  const planted = parseBackendTimestamp(plantedAt);
  const harvested = parseBackendTimestamp(harvestedAt);
  if (!planted || !harvested) return null;
  const diff = harvested.getTime() - planted.getTime();
  return Math.max(0, Math.floor(diff / MS_IN_DAY));
}

function ArchivePlantCard({ plant, onOpenJournal, onOpenMetric }) {
  const plantTypeId = normalizePlantTypeId(plant?.plant_type || DEFAULT_PLANT_TYPE_ID);
  const ageAtHarvest = calcAgeAtHarvest(plant?.planted_at, plant?.harvested_at);
  const manualStageId = plant?.growth_stage ? String(plant.growth_stage).trim() : '';
  const stageId = manualStageId || (ageAtHarvest !== null ? getAutoStageFromAge(plantTypeId, ageAtHarvest) : undefined);

  return (
    <Surface variant="card" padding="md" className="archive-plant-card">
      <div className="archive-plant-card__header">
        <div className="archive-plant-card__title">
          <Title level={3} className="archive-plant-card__name">{plant.name}</Title>
          <Text tone="muted" className="archive-plant-card__group">
            {plant?.plant_group?.name || 'Без группы'}
          </Text>
        </div>
        <div className="archive-plant-card__avatar" aria-hidden="true">
          <PlantAvatar plantType={plantTypeId} stage={stageId} variant="card" size="sm" />
        </div>
      </div>

      <div className="archive-plant-card__dates">
        <div className="archive-plant-card__row">
          <span className="archive-plant-card__label">Дата посадки</span>
          <span className="archive-plant-card__value">{formatPlantDate(plant?.planted_at)}</span>
        </div>
        <div className="archive-plant-card__row">
          <span className="archive-plant-card__label">Дата сбора</span>
          <span className="archive-plant-card__value">{formatPlantDate(plant?.harvested_at)}</span>
        </div>
        <div className="archive-plant-card__row">
          <span className="archive-plant-card__label">Возраст при сборе</span>
          <span className="archive-plant-card__value">
            {ageAtHarvest !== null ? `${ageAtHarvest} дн.` : '-'}
          </span>
        </div>
      </div>

      <div className="archive-plant-card__actions">
        <div className="archive-plant-card__stats">
          <SensorPill
            className="archive-plant-card__stat-btn"
            kind="soil_moisture"
            value={null}
            onClick={() => onOpenMetric?.(plant, 'soil_moisture')}
          />
          <SensorPill
            className="archive-plant-card__stat-btn"
            kind="watering"
            value={false}
            onClick={() => onOpenMetric?.(plant, 'watering')}
          />
        </div>
        <button type="button" className="archive-plant-card__journal" onClick={() => onOpenJournal?.(plant)}>
          Журнал
        </button>
      </div>
    </Surface>
  );
}

function AppPlants() {
  const { token } = useAuth();
  const { openSensorStats } = useSensorStatsContext();
  const navigate = useNavigate();
  const [plants, setPlants] = useState([]);
  const [plantGroups, setPlantGroups] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create');
  const [selectedPlant, setSelectedPlant] = useState(null);
  const [harvestOpen, setHarvestOpen] = useState(false);
  const [harvestTarget, setHarvestTarget] = useState(null);
  const [harvestError, setHarvestError] = useState(null);
  const [harvestSaving, setHarvestSaving] = useState(false);
  const [harvestForm, setHarvestForm] = useState({
    date: formatDateKeyYYYYMMDD(new Date()),
    text: '',
  });

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [plantsPayload, groupsPayload] = await Promise.all([
        fetchPlants(token),
        fetchPlantGroups(token),
      ]);
      setPlants(Array.isArray(plantsPayload) ? plantsPayload : []);
      setPlantGroups(Array.isArray(groupsPayload) ? groupsPayload : []);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setError(err?.message || 'Не удалось загрузить растения');
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

  const handleOpenMetric = (plant, metric) => {
    if (!plant?.id || !metric) return;
    openSensorStats({
      mode: 'plant',
      plantId: plant.id,
      metric,
      title: METRIC_LABELS[metric] || metric,
      subtitle: plant.name,
    });
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

  const handleOpenHarvest = (plant) => {
    setHarvestTarget(plant);
    setHarvestError(null);
    setHarvestSaving(false);
    setHarvestForm({
      date: formatDateKeyYYYYMMDD(new Date()),
      text: '',
    });
    setHarvestOpen(true);
  };

  const handleCloseHarvest = () => {
    setHarvestOpen(false);
    setHarvestTarget(null);
  };

  const handleSubmitHarvest = async (event) => {
    event.preventDefault();
    if (!harvestTarget?.id) return;
    if (!harvestForm.date) {
      setHarvestError('Укажите дату сбора');
      return;
    }
    setHarvestSaving(true);
    setHarvestError(null);
    const harvestedAt = `${harvestForm.date}T00:00:00`;
    try {
      await harvestPlant(
        harvestTarget.id,
        {
          text: harvestForm.text,
          harvested_at: harvestedAt,
        },
        token,
      );
      await loadData();
      setHarvestOpen(false);
      setHarvestTarget(null);
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setHarvestError(err?.message || 'Не удалось собрать урожай');
    } finally {
      setHarvestSaving(false);
    }
  };

  const handleSaved = async () => {
    await loadData();
    setDialogOpen(false);
  };

  const activePlants = useMemo(
    () => plants.filter((plant) => !plant?.harvested_at),
    [plants],
  );

  const archivedPlants = useMemo(
    () => plants.filter((plant) => plant?.harvested_at),
    [plants],
  );

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

      {!isLoading && !error && activePlants.length === 0 && archivedPlants.length === 0 && (
        <AppPageState kind="empty" title="Растения не найдены" />
      )}

      {!isLoading && !error && activePlants.length > 0 && (
        <AppGrid min={320}>
          {activePlants.map((plant) => (
            <PlantCard
              key={plant.id}
              plant={plant}
              onEdit={handleOpenEdit}
              onOpenJournal={handleOpenJournal}
              onHarvest={handleOpenHarvest}
            />
          ))}
        </AppGrid>
      )}

      {!isLoading && !error && archivedPlants.length > 0 && (
        <div className="app-plants__archive">
          <div className="app-plants__archive-title">Архив растений</div>
          <AppGrid min={260}>
            {archivedPlants.map((plant) => (
              <ArchivePlantCard
                key={plant.id}
                plant={plant}
                onOpenJournal={handleOpenJournal}
                onOpenMetric={handleOpenMetric}
              />
            ))}
          </AppGrid>
        </div>
      )}

      <PlantEditDialog
        isOpen={dialogOpen}
        mode={dialogMode}
        plant={selectedPlant}
        plantGroups={plantGroups}
        onClose={() => setDialogOpen(false)}
        onSaved={handleSaved}
      />

      <Modal
        isOpen={harvestOpen}
        title={harvestTarget?.name ? `Сбор урожая: ${harvestTarget.name}` : 'Сбор урожая'}
        onClose={handleCloseHarvest}
        closeLabel="Закрыть"
        footer={(
          <div className="harvest-dialog__footer">
            <Button variant="secondary" onClick={handleCloseHarvest} disabled={harvestSaving}>
              Отмена
            </Button>
            <Button type="submit" form="plant-harvest-form" variant="primary" disabled={harvestSaving}>
              {harvestSaving ? 'Сохранение...' : 'Собрать урожай'}
            </Button>
          </div>
        )}
      >
        <form id="plant-harvest-form" className="harvest-dialog__form" onSubmit={handleSubmitHarvest}>
          {harvestError && <div className="harvest-dialog__error">{harvestError}</div>}
          <FormField label="Описание урожая" htmlFor="harvest-text">
            <textarea
              id="harvest-text"
              rows={3}
              value={harvestForm.text}
              onChange={(e) => setHarvestForm((prev) => ({ ...prev, text: e.target.value }))}
              placeholder="Комментарий или заметки"
            />
          </FormField>
          <FormField label="Дата сбора" htmlFor="harvest-date">
            <input
              id="harvest-date"
              type="date"
              value={harvestForm.date}
              onChange={(e) => setHarvestForm((prev) => ({ ...prev, date: e.target.value }))}
              required
            />
          </FormField>
        </form>
      </Modal>
    </div>
  );
}

export default AppPlants;
