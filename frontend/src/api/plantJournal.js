export async function fetchPlantJournal(plantId, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/plants/${encodeURIComponent(plantId)}/journal`, { headers });
  if (!response.ok) {
    throw new Error(`Failed to load plant journal (${response.status})`);
  }
  return response.json();
}

export async function createPlantJournalEntry(plantId, payload, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(`/api/plants/${encodeURIComponent(plantId)}/journal`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Failed to create journal entry (${response.status})`);
  }
  return response.json();
}

export async function updatePlantJournalEntry(plantId, entryId, payload, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(
    `/api/plants/${encodeURIComponent(plantId)}/journal/${encodeURIComponent(entryId)}`,
    {
      method: 'PATCH',
      headers,
      body: JSON.stringify(payload),
    },
  );
  if (!response.ok) {
    throw new Error(`Failed to update journal entry (${response.status})`);
  }
  return response.json();
}

export async function deletePlantJournalEntry(plantId, entryId, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(
    `/api/plants/${encodeURIComponent(plantId)}/journal/${encodeURIComponent(entryId)}`,
    { method: 'DELETE', headers },
  );
  if (!response.ok) {
    throw new Error(`Failed to delete journal entry (${response.status})`);
  }
  return response.json();
}

export async function downloadJournalPhotoBlob(photoId, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/journal/photos/${encodeURIComponent(photoId)}`, {
    method: 'GET',
    headers,
  });
  if (!response.ok) {
    throw new Error(`Failed to load journal photo (${response.status})`);
  }
  return response.blob();
}

export async function downloadPlantJournalMarkdown(plantId, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(
    `/api/plants/${encodeURIComponent(plantId)}/journal/export?format=md`,
    {
      method: 'GET',
      headers,
    },
  );
  if (!response.ok) {
    throw new Error(`Failed to download journal (${response.status})`);
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
