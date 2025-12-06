import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchPlants } from '../../api/plants';
import {
  createPlantJournalEntry,
  deletePlantJournalEntry,
  fetchPlantJournal,
  updatePlantJournalEntry,
} from '../../api/plantJournal';
import { useAuth } from '../../features/auth/AuthContext';
import './AppPlantJournal.css';

function JournalForm({ value, onChange, onSubmit, isEditing }) {
  const handleChange = (field) => (event) => {
    onChange({ ...value, [field]: event.target.value });
  };

  return (
    <form className="plant-journal__form" onSubmit={onSubmit}>
      <label className="plant-journal__form-row">
        <span>Тип</span>
        <select value={value.type} onChange={handleChange('type')}>
          <option value="note">note</option>
          <option value="watering">watering</option>
          <option value="feeding">feeding</option>
          <option value="photo">photo</option>
          <option value="other">other</option>
        </select>
      </label>
      <label className="plant-journal__form-row">
        <span>Текст</span>
        <textarea value={value.text} onChange={handleChange('text')} rows={3} />
      </label>
      <button type="submit" className="plant-journal__submit">
        {isEditing ? 'Сохранить' : 'Добавить'}
      </button>
    </form>
  );
}

function AppPlantJournal() {
  const { plantId } = useParams();
  const navigate = useNavigate();
  const { token } = useAuth();
  const [plant, setPlant] = useState(null);
  const [entries, setEntries] = useState([]);
  const [formState, setFormState] = useState({ type: 'note', text: '' });
  const [editingId, setEditingId] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const loadData = useCallback(async () => {
    if (!plantId) {
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const [plants, journal] = await Promise.all([
        fetchPlants(token),
        fetchPlantJournal(plantId, token),
      ]);
      const currentPlant = plants.find((item) => String(item.id) === String(plantId)) || null;
      setPlant(currentPlant);
      setEntries(Array.isArray(journal) ? journal : []);
    } catch (err) {
      setError(err?.message || 'Не удалось загрузить журнал');
    } finally {
      setIsLoading(false);
    }
  }, [plantId, token]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!plantId) {
      return;
    }
    try {
      if (editingId) {
        await updatePlantJournalEntry(plantId, editingId, formState, token);
      } else {
        await createPlantJournalEntry(plantId, formState, token);
      }
      setFormState({ type: 'note', text: '' });
      setEditingId(null);
      await loadData();
    } catch (err) {
      setError(err?.message || 'Не удалось сохранить запись');
    }
  };

  const handleEdit = (entry) => {
    setEditingId(entry.id);
    setFormState({ type: entry.type, text: entry.text || '' });
  };

  const handleDelete = async (entryId) => {
    if (!plantId) {
      return;
    }
    try {
      await deletePlantJournalEntry(plantId, entryId, token);
      await loadData();
    } catch (err) {
      setError(err?.message || 'Не удалось удалить запись');
    }
  };

  return (
    <div className="plant-journal">
      <div className="plant-journal__header">
        <div>
          <h1 className="plant-journal__title">Журнал растения</h1>
          {plant && <div className="plant-journal__subtitle">{plant.name}</div>}
        </div>
        <button className="plant-journal__back" type="button" onClick={() => navigate('/app/plants')}>
          ← К списку растений
        </button>
      </div>

      {error && <div className="plant-journal__state plant-journal__state--error">{error}</div>}
      {isLoading && <div className="plant-journal__state">Загрузка...</div>}

      <JournalForm
        value={formState}
        onChange={setFormState}
        onSubmit={handleSubmit}
        isEditing={Boolean(editingId)}
      />

      <div className="plant-journal__list">
        {entries.map((entry) => (
          <div key={entry.id} className="plant-journal__item">
            <div className="plant-journal__item-header">
              <span className="plant-journal__item-date">{entry.event_at}</span>
              <span className="plant-journal__item-type">{entry.type}</span>
            </div>
            <div className="plant-journal__item-text">{entry.text || '—'}</div>
            <div className="plant-journal__item-actions">
              <button type="button" onClick={() => handleEdit(entry)}>
                Редактировать
              </button>
              <button type="button" onClick={() => handleDelete(entry.id)}>
                Удалить
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default AppPlantJournal;
