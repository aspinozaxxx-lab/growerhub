import { apiFetch } from './client';

export async function fetchPlantJournal(plantId, token) {
  void token;
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}/journal`);
  if (!response.ok) {
    throw new Error(`Не удалось загрузить журнал растения (${response.status})`);
  }
  return response.json();
}

export async function createPlantJournalEntry(plantId, payload, token) {
  void token;
  const headers = { 'Content-Type': 'application/json' };
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}/journal`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Не удалось создать запись журнала (${response.status})`);
  }
  return response.json();
}

export async function updatePlantJournalEntry(plantId, entryId, payload, token) {
  void token;
  const headers = { 'Content-Type': 'application/json' };
  const response = await apiFetch(
    `/api/plants/${encodeURIComponent(plantId)}/journal/${encodeURIComponent(entryId)}`,
    {
      method: 'PATCH',
      headers,
      body: JSON.stringify(payload),
    },
  );
  if (!response.ok) {
    throw new Error(`Не удалось сохранить запись журнала (${response.status})`);
  }
  return response.json();
}

export async function deletePlantJournalEntry(plantId, entryId, token) {
  void token;
  const response = await apiFetch(
    `/api/plants/${encodeURIComponent(plantId)}/journal/${encodeURIComponent(entryId)}`,
    { method: 'DELETE' },
  );
  if (!response.ok) {
    throw new Error(`Не удалось удалить запись журнала (${response.status})`);
  }
  return response.json();
}

export async function downloadJournalPhotoBlob(photoId, token) {
  void token;
  const response = await apiFetch(`/api/journal/photos/${encodeURIComponent(photoId)}`, {
    method: 'GET',
  });
  if (!response.ok) {
    throw new Error(`Не удалось загрузить фотографию (${response.status})`);
  }
  return response.blob();
}

export async function downloadPlantJournalMarkdown(plantId, token) {
  void token;
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}/journal/export?format=md`, {
    method: 'GET',
  });
  if (!response.ok) {
    throw new Error(`Не удалось скачать журнал (${response.status})`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `plant_journal_${plantId}.md`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
