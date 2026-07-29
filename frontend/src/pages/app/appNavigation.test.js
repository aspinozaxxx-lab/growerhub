import { describe, expect, it } from 'vitest';
import {
  APP_NAV_ITEMS,
  LEGACY_APP_REDIRECTS,
  SETTINGS_TABS,
} from './appNavigation';

describe('app navigation', () => {
  it('ostavlyaet pyat glavnyh punktov v nuzhnom poryadke', () => {
    expect(APP_NAV_ITEMS.map((item) => item.label)).toEqual([
      'Обзор',
      'Конструктор фермы',
      'Автоматизации',
      'Растения',
      'Настройки',
    ]);
  });

  it('perenosit chetyre starye stranicy v nastrojki', () => {
    expect(SETTINGS_TABS.map((item) => item.label)).toEqual([
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
    });
  });
});
