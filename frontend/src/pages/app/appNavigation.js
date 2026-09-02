export const APP_NAV_ITEMS = [
  { to: '/app/', label: 'Обзор', icon: '⌂', end: true },
  { to: '/app/farm/', label: 'Конструктор фермы', icon: '▦' },
  { to: '/app/automations/', label: 'Автоматизации', icon: '⚡' },
  { to: '/app/plants/', label: 'Растения', icon: '♧' },
  { to: '/app/settings/', label: 'Настройки', icon: '⚙' },
];

export const SETTINGS_TABS = [
  { to: '/app/settings/connections/', label: 'Подключения' },
  { to: '/app/settings/zones/', label: 'Зоны' },
  { to: '/app/settings/devices/', label: 'Устройства' },
  { to: '/app/settings/profile/', label: 'Профиль' },
];

export const LEGACY_APP_REDIRECTS = {
  '/app/connections/': '/app/settings/connections/',
  '/app/zones/': '/app/settings/zones/',
  '/app/devices/': '/app/settings/devices/',
  '/app/profile/': '/app/settings/profile/',
  '/app/admin/dashboard/': '/app/',
  '/app/admin/manual-watering/': '/app/manual-watering/',
};
