import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchPlants } from '../../api/plants';
import {
  createPlantJournalEntry,
  fetchPlantJournal,
  updatePlantJournalEntry,
  downloadPlantJournalMarkdown,
  downloadJournalPhotoBlob,
} from '../../api/plantJournal';
import { useAuth } from '../../features/auth/AuthContext';
import { formatTimeHHMM } from '../../utils/formatters';
import './AppPlantJournal.css';

const JOURNAL_TYPE_CONFIG = {
  watering: { label: 'Полив', icon: '💧', kind: 'watering' },
  feeding: { label: 'Уход', icon: '🧹', kind: 'care' },
  photo: { label: 'Фото', icon: '📷', kind: 'photo' },
  note: { label: 'Наблюдение', icon: '👁', kind: 'observation' },
  other: { label: 'Наблюдение', icon: '👁', kind: 'observation' },
};

const BACKEND_TYPES = ['watering', 'feeding', 'photo', 'note', 'other'];

function toLocalDateKeyFromIso(isoString) {
  const d = new Date(isoString);
  const year = d.getFullYear();
  const month = d.getMonth() + 1;
  const day = d.getDate();
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function dateKeyFromString(value) {
  if (!value) return '';
  if (typeof value === 'string') {
    // Dlya gotovyh key v formate YYYY-MM-DD prosto vozvrashaem.
    if (/^\\d{4}-\\d{2}-\\d{2}$/.test(value)) {
      return value;
    }
    return toLocalDateKeyFromIso(value);
  }
  return toLocalDateKeyFromIso(value.toISOString());
}

function normalizeDateToLocalMidnight(value) {
  const dateObj = typeof value === 'string' ? new Date(value) : value;
  if (!(dateObj instanceof Date) || Number.isNaN(dateObj.getTime())) {
    return null;
  }
  return new Date(dateObj.getFullYear(), dateObj.getMonth(), dateObj.getDate());
}

function buildDateRange(startDate, endDate) {
  const days = [];
  const cursor = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate());
  const end = new Date(endDate.getFullYear(), endDate.getMonth(), endDate.getDate());
  while (cursor.getTime() <= end.getTime()) {
    days.push(new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate()));
    cursor.setDate(cursor.getDate() + 1);
  }
  return days;
}

function formatVolumeL(value) {
  if (value === null || value === undefined) return '';
  const str = Number(value).toFixed(2).replace(/\.?0+$/, '').replace('.', ',');
  return `${str} л`;
}

function PhotoPreview({ photo, token, cache, setCache }) {
  const [status, setStatus] = useState(photo?.has_data ? 'idle' : 'empty');

  useEffect(() => {
    let isMounted = true;
    if (!photo || !photo.has_data || cache[photo.id]) {
      return undefined;
    }
    setStatus('loading');
    downloadJournalPhotoBlob(photo.id, token)
      .then((blob) => {
        if (!isMounted) return;
        const url = URL.createObjectURL(blob);
        setCache((prev) => ({ ...prev, [photo.id]: url }));
        setStatus('ready');
      })
      .catch(() => {
        if (isMounted) setStatus('error');
      });
    return () => {
      isMounted = false;
    };
  }, [cache, photo, setCache, token]);

  if (!photo || !photo.has_data) {
    return <div className="journal-entry__photo-placeholder">Фото недоступно</div>;
  }
  if (status === 'loading') {
    return <div className="journal-entry__photo-placeholder">Загрузка фото...</div>;
  }
  if (status === 'error') {
    return <div className="journal-entry__photo-placeholder">Ошибка загрузки фото</div>;
  }
  const objectUrl = cache[photo.id];
  if (!objectUrl) {
    return <div className="journal-entry__photo-placeholder">Загрузка фото...</div>;
  }
  return <img src={objectUrl} alt={photo.caption || 'Фото'} className="journal-entry__photo" />;
}

function JournalEntryCard({ entry, onEdit, photoCache, setPhotoCache, token }) {
  const config = JOURNAL_TYPE_CONFIG[entry.type] || JOURNAL_TYPE_CONFIG.other;
  const time = formatTimeHHMM(entry.event_at);
  const details = entry.watering_details || null;
  const volume = details ? formatVolumeL(details.water_volume_l) : '';
  const fertilizers = details?.fertilizers_per_liter;
  const hasPhoto = Array.isArray(entry.photos) && entry.photos.length > 0;
  const mainPhoto = hasPhoto ? entry.photos[0] : null;

  let content = entry.text || '';
  if (entry.type === 'watering' && (volume || fertilizers)) {
    const parts = [];
    if (volume) parts.push(volume);
    if (fertilizers) parts.push(`удобрения: ${fertilizers}`);
    content = parts.join('   ');
  }
  if (entry.type === 'photo' && !content) {
    content = 'Фото';
  }

  return (
    <div className="journal-entry">
      {entry.type === 'watering' ? (
        <div className="journal-entry__watering">
          <span className="journal-entry__time">{time}</span>
          <span className="journal-entry__icon journal-entry__icon--big">{config.icon}</span>
          {volume && <span className="journal-entry__volume">{volume}</span>}
          {fertilizers && <span className="journal-entry__fertilizers">{`удобрения: ${fertilizers}`}</span>}
        </div>
      ) : (
        <>
          <div className="journal-entry__meta">
            <div className="journal-entry__time">{time}</div>
            <div className="journal-entry__type">
              <span className="journal-entry__icon">{config.icon}</span>
              <span>{config.label}</span>
            </div>
          </div>
          <div className="journal-entry__body">
            {entry.type === 'photo' ? (
              <div className="journal-entry__photo-block">
                <div className="journal-entry__text">{content}</div>
                <PhotoPreview photo={mainPhoto} token={token} cache={photoCache} setCache={setPhotoCache} />
              </div>
            ) : (
              <div className="journal-entry__text">{content || '-'}</div>
            )}
          </div>
        </>
      )}
      <button type="button" className="journal-entry__edit" onClick={() => onEdit(entry)} title="Редактировать">
        ✏
      </button>
    </div>
  );
}

function CalendarGrid({ startDate, endDate, entries, plantedAt, selectedDate, onSelectDate }) {
  const dateList = useMemo(() => buildDateRange(startDate, endDate), [startDate, endDate]);
  const planted = new Date(plantedAt.getTime());
  planted.setHours(0, 0, 0, 0);

  const entriesByDate = useMemo(() => {
    const map = {};
    entries.forEach((entry) => {
      const key = dateKeyFromString(entry.event_at);
      if (!map[key]) {
        map[key] = [];
      }
      map[key].push(entry);
    });
    return map;
  }, [entries]);

  const months = useMemo(() => {
    const grouped = dateList.reduce((acc, day) => {
      const monthKey = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}`;
      if (!acc[monthKey]) acc[monthKey] = [];
      acc[monthKey].push(day);
      return acc;
    }, {});
    return Object.entries(grouped).sort((a, b) => (a[0] < b[0] ? -1 : 1));
  }, [dateList]);

  return (
    <div className="journal-calendar">
      {months.map(([monthKey, days]) => {
        const monthLabel = days[0].toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' });
        const normalizedLabel = monthLabel.charAt(0).toUpperCase() + monthLabel.slice(1);
        return (
          <div className="journal-calendar__month" key={monthKey}>
            <div className="journal-calendar__month-title">{normalizedLabel}</div>
            <div className="journal-calendar__month-grid">
              {days.map((day) => {
                const key = dateKeyFromString(day);
                const entriesForDay = entriesByDate[key] || [];
                const typeOrder = ['watering', 'feeding', 'note', 'other', 'photo'];
                const uniqueIcons = [];
                typeOrder.forEach((t) => {
                  const hasType = entriesForDay.some((e) => e.type === t);
                  if (hasType && JOURNAL_TYPE_CONFIG[t]) {
                    uniqueIcons.push(JOURNAL_TYPE_CONFIG[t].icon);
                  }
                });
                const iconList = uniqueIcons.slice(0, 3);
                return (
                  <button
                    key={key}
                    type="button"
                    className={`journal-calendar__day ${selectedDate === key ? 'is-selected' : ''}`}
                    onClick={() => onSelectDate(key)}
                  >
                    <span className="journal-calendar__date-number">{day.getDate()}</span>
                    {iconList.length > 0 && (
                      <span className="journal-calendar__icons">
                        {iconList.map((icon) => (
                          <span key={icon}>{icon}</span>
                        ))}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function AppPlantJournal() {
  const { plantId } = useParams();
  const navigate = useNavigate();
  const { token } = useAuth();
  const [plant, setPlant] = useState(null);
  const [entries, setEntries] = useState([]);
  const [selectedDate, setSelectedDate] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [photoCache, setPhotoCache] = useState({});
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [formState, setFormState] = useState({
    date: '',
    time: '12:00',
    type: 'note',
    text: '',
    photoUrl: '',
  });

  const loadData = useCallback(async () => {
    if (!plantId) return;
    setIsLoading(true);
    setError(null);
    try {
      const [plants, journal] = await Promise.all([fetchPlants(token), fetchPlantJournal(plantId, token)]);
      const currentPlant = plants.find((item) => String(item.id) === String(plantId)) || null;
      setPlant(currentPlant);
      setEntries(Array.isArray(journal) ? journal : []);
      const todayKey = dateKeyFromString(new Date());
      setFormState((prev) => ({ ...prev, date: prev.date || todayKey }));
    } catch (err) {
      setError(err?.message || 'Не удалось загрузить журнал');
    } finally {
      setIsLoading(false);
    }
  }, [plantId, token]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const plantedDate = useMemo(() => {
    const fallback = new Date();
    fallback.setHours(0, 0, 0, 0);
    if (plant?.planted_at) {
      const key = dateKeyFromString(plant.planted_at);
      return new Date(`${key}T00:00:00`);
    }
    if (entries.length > 0) {
      const earliestKey = entries
        .map((e) => dateKeyFromString(e.event_at))
        .sort((a, b) => (a < b ? -1 : 1))[0];
      return new Date(`${earliestKey}T00:00:00`);
    }
    return fallback;
  }, [entries, plant]);

  const endDate = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return today;
  }, []);

  const entriesForSelectedDate = useMemo(() => {
    if (!selectedDate) return [];
    return entries
      .filter((entry) => toLocalDateKeyFromIso(entry.event_at) === selectedDate)
      .sort((a, b) => new Date(a.event_at) - new Date(b.event_at));
  }, [entries, selectedDate]);

  const selectedDateLabel =
    selectedDate &&
    new Date(`${selectedDate}T00:00:00`).toLocaleDateString('ru-RU', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });

  const selectedAgeLabel =
    selectedDate && plant?.planted_at
      ? (() => {
          const [y, m, d] = selectedDate.split('-').map((part) => Number(part));
          const selectedLocal = new Date(y, m - 1, d);
          const planted = new Date(plant.planted_at);
          const plantedLocal = new Date(planted.getFullYear(), planted.getMonth(), planted.getDate());
          const diff = selectedLocal.getTime() - plantedLocal.getTime();
          return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
        })()
      : null;

  const handleDownloadJournal = async () => {
    try {
      await downloadPlantJournalMarkdown(plantId, token);
    } catch (e) {
      console.error(e);
      alert('Не удалось скачать журнал');
    }
  };

  const resetForm = useCallback(() => {
    const todayKey = dateKeyFromString(new Date());
    setEditingId(null);
    setFormState({
      date: selectedDate || todayKey,
      time: '12:00',
      type: 'note',
      text: '',
      photoUrl: '',
    });
  }, [selectedDate]);

  const handleOpenForm = () => {
    setIsFormOpen((prev) => !prev);
    if (!isFormOpen) {
      resetForm();
    }
  };

  const handleChangeForm = (field, value) => {
    setFormState((prev) => ({ ...prev, [field]: value }));
  };

  const handleEditEntry = (entry) => {
    setIsFormOpen(true);
    setEditingId(entry.id);
    const datePart = dateKeyFromString(entry.event_at);
    const timePart = formatTimeHHMM(entry.event_at) || '12:00';
    const firstPhotoUrl = entry.photos?.[0]?.url || '';
    setFormState({
      date: datePart,
      time: timePart,
      type: BACKEND_TYPES.includes(entry.type) ? entry.type : 'note',
      text: entry.text || '',
      photoUrl: firstPhotoUrl,
    });
  };

  const buildEventAtIso = (dateStr, timeStr) => {
    if (!dateStr) return null;
    const safeTime = timeStr && timeStr.includes(':') ? timeStr : '00:00';
    const iso = new Date(`${dateStr}T${safeTime}:00`);
    if (Number.isNaN(iso.getTime())) {
      return null;
    }
    return iso.toISOString();
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!plantId) return;
    const backendType = BACKEND_TYPES.includes(formState.type) ? formState.type : 'note';
    try {
      if (editingId) {
        await updatePlantJournalEntry(
          plantId,
          editingId,
          { type: backendType, text: formState.text || null },
          token,
        );
      } else {
        const eventAt = buildEventAtIso(formState.date, formState.time) || undefined;
        const payload = {
          type: backendType,
          text: formState.text || null,
          event_at: eventAt,
        };
        if (backendType === 'photo' && formState.photoUrl) {
          payload.photo_urls = [formState.photoUrl];
        }
        await createPlantJournalEntry(plantId, payload, token);
      }
      await loadData();
      setIsFormOpen(false);
      resetForm();
    } catch (err) {
      setError(err?.message || 'Не удалось сохранить запись');
    }
  };

  const headingTitle = plant ? `Журнал: ${plant.name}` : 'Журнал растения';
  const plantedAtLabel =
    plant?.planted_at &&
    new Date(plant.planted_at).toLocaleDateString('ru-RU', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });

  return (
    <div className="plant-journal-page">
      <div className="plant-journal__header">
        <div>
          <div className="plant-journal__title">{headingTitle}</div>
          {plant && (
            <div className="plant-journal__subtitle">
              {plantedAtLabel ? `Посажено ${plantedAtLabel}` : 'Посажено —'}
            </div>
          )}
        </div>
        <div className="plant-journal__actions">
          <button type="button" className="plant-journal__download" onClick={handleDownloadJournal}>
            Скачать журнал (.md)
          </button>
          <button type="button" className="plant-journal__back" onClick={() => navigate('/app/plants')}>
            ← К списку
          </button>
        </div>
      </div>

      {error && <div className="plant-journal__state plant-journal__state--error">{error}</div>}
      {isLoading && <div className="plant-journal__state">Загрузка...</div>}

      {plant && (
        <CalendarGrid
          startDate={plantedDate}
          endDate={endDate}
          entries={entries}
          plantedAt={plantedDate}
          selectedDate={selectedDate}
          onSelectDate={setSelectedDate}
        />
      )}

      <div className="journal-entries-block">
        <div className="journal-entries-block__header">
          <div className="journal-entries-block__title">
            {selectedDateLabel ? `Записи за ${selectedDateLabel} года` : 'Выберите день в календаре'}
            {selectedAgeLabel !== null && selectedAgeLabel !== undefined && (
              <div className="journal-entries-block__age">Возраст {selectedAgeLabel} дней</div>
            )}
          </div>
          <button type="button" className="journal-entries-block__add" onClick={handleOpenForm}>
            {isFormOpen ? 'Закрыть форму' : 'Добавить запись'}
          </button>
        </div>

        {selectedDate && entriesForSelectedDate.length === 0 && (
          <div className="journal-entries-block__empty">Нет записей за выбранную дату</div>
        )}

        {selectedDate && entriesForSelectedDate.length > 0 && (
          <div className="journal-entries-list">
            {entriesForSelectedDate.map((entry) => (
              <JournalEntryCard
                key={entry.id}
                entry={entry}
                onEdit={handleEditEntry}
                photoCache={photoCache}
                setPhotoCache={setPhotoCache}
                token={token}
              />
            ))}
          </div>
        )}
      </div>

      {isFormOpen && (
        <form className="journal-form" onSubmit={handleSubmit}>
          <div className="journal-form__row">
            <label className="journal-form__field">
              <span>Дата</span>
              <input
                type="date"
                value={formState.date}
                onChange={(e) => handleChangeForm('date', e.target.value)}
                required
              />
            </label>
            <label className="journal-form__field">
              <span>Время</span>
              <input
                type="time"
                value={formState.time}
                onChange={(e) => handleChangeForm('time', e.target.value)}
              />
            </label>
            <label className="journal-form__field">
              <span>Тип записи</span>
              <select
                value={formState.type}
                onChange={(e) => handleChangeForm('type', e.target.value)}
              >
                <option value="watering">Полив</option>
                <option value="feeding">Уход</option>
                <option value="note">Наблюдение</option>
                <option value="photo">Фото</option>
                <option value="other">Наблюдение (other)</option>
              </select>
            </label>
          </div>

          <label className="journal-form__field journal-form__field--wide">
            <span>Текст / комментарий</span>
            <textarea
              rows={3}
              value={formState.text}
              onChange={(e) => handleChangeForm('text', e.target.value)}
              placeholder="Комментарий или детали"
            />
          </label>

          {formState.type === 'photo' && (
            <label className="journal-form__field journal-form__field--wide">
              <span>URL фото (пока только ссылка)</span>
              <input
                type="url"
                value={formState.photoUrl}
                onChange={(e) => handleChangeForm('photoUrl', e.target.value)}
                placeholder="https://..."
              />
            </label>
          )}

          <div className="journal-form__actions">
            <button type="submit" className="journal-form__submit">
              {editingId ? 'Сохранить' : 'Добавить'}
            </button>
            <button type="button" className="journal-form__cancel" onClick={resetForm}>
              Сбросить
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

export default AppPlantJournal;
