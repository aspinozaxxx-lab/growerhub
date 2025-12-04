// API helper for plants list.
export async function fetchPlants(token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch('/api/plants', { headers });
  if (!response.ok) {
    throw new Error(`Failed to load plants (${response.status})`);
  }
  return response.json();
}
