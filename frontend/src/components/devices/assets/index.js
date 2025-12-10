// DEVICE_ICONS - lokal'naya karta ikonok ustrojstv (klyuch -> formaty png/svg)
import grovikaMiniPng from './grovika_mini.png';
import grovikaMiniSvg from './grovika_mini.svg';

// DEVICE_ICONS - vse dostupnye assesty dlya ikonok; png imeet prioritet
const DEVICE_ICONS = {
  grovika_mini: {
    png: grovikaMiniPng,
    svg: grovikaMiniSvg,
  },
};

// resolveDeviceAsset - vozvrashaet assest s prioritetom PNG, esli net - beret SVG
function resolveDeviceAsset(deviceKey) {
  const entry = DEVICE_ICONS[deviceKey];
  if (!entry) {
    return null;
  }

  if (entry.png) {
    return entry.png;
  }

  if (entry.svg) {
    return entry.svg;
  }

  return null;
}

export { DEVICE_ICONS, resolveDeviceAsset };
