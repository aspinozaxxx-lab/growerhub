import { describe, expect, it } from 'vitest';
import guide from '../../../../backend/src/main/resources/advisor/interface-guide.json';
import {
  APP_NAV_ITEMS,
  LEGACY_APP_REDIRECTS,
  SETTINGS_TABS,
} from './appNavigation';

describe('app navigation', () => {
  it('keeps the assistant guide aligned with the cabinet menu and settings paths', () => {
    const entries = Object.values(guide.sections);
    for (const item of [...APP_NAV_ITEMS, ...SETTINGS_TABS]) {
      const entry = entries.find(page => page.path === item.to);
      expect(entry).toBeDefined();
      expect(entry.label.ru).toContain(item.label);
      expect(entry.label.en).toBeTruthy();
    }
  });
  it('ostavlyaet pyat glavnyh punktov v nuzhnom poryadke', () => {
    expect(APP_NAV_ITEMS.map((item) => item.label)).toEqual([
      'Обзор',
      'Ферма',
      'Сценарии',
      'Растения',
      'Настройки',
    ]);
  });

  it('perenosit starye stranicy na aktualnye marshruty', () => {
    expect(SETTINGS_TABS.map((item) => item.label)).toEqual([
      'Уведомления',
      'Подключения',
      'Зоны',
      'Устройства',
      'Профиль',
    ]);
    expect(LEGACY_APP_REDIRECTS).toEqual({
      '/app/connections/': '/app/settings/connections/',
      '/app/zones/': '/app/settings/zones/',
      '/app/devices/': '/app/settings/devices/',
      '/app/profile/': '/app/settings/profile/',
      '/app/admin/dashboard/': '/app/',
      '/app/admin/manual-watering/': '/app/manual-watering/',
    });
  });
});
