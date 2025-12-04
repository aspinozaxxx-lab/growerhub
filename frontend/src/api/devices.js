// API helper for user devices.
export async function fetchMyDevices(token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch('/api/devices/my', { headers });
  if (!response.ok) {
    throw new Error(`Failed to load devices (${response.status})`);
  }
  return response.json();
}
