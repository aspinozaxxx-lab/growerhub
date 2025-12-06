// API helper for manual watering actions.
export async function startManualWatering({ deviceId, waterVolumeL, ph, fertilizersPerLiter, token }) {
  const headers = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const payload = {
    device_id: deviceId,
  };
  if (waterVolumeL !== undefined && waterVolumeL !== null) {
    payload.water_volume_l = waterVolumeL;
  }
  if (ph !== undefined && ph !== null && ph !== '') {
    payload.ph = ph;
  }
  if (fertilizersPerLiter) {
    payload.fertilizers_per_liter = fertilizersPerLiter;
  }

  const response = await fetch('/api/manual-watering/start', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Failed to start watering (${response.status})`);
  }

  return response.json();
}

export async function getManualWateringStatus(deviceId, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/manual-watering/status?device_id=${encodeURIComponent(deviceId)}`, {
    headers,
  });

  if (!response.ok) {
    throw new Error(`Failed to load watering status (${response.status})`);
  }

  return response.json();
}
