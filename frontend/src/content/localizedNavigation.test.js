import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { afterEach, describe, expect, it } from 'vitest';
import { normalizeApiErrorMessage } from '../api/client';
import { buildSsoLoginUrl } from '../features/auth/sso';
import { getArticleClusters } from './articleClusters';
import {
  getArticles,
  getArticleTranslation,
} from './articles';
import { getLocalizedPathPair } from './localizedNavigation';
import {
  PUBLIC_ROUTES,
  getArticlePath,
  getClusterPath,
  getPlatformStartPath,
} from '../domain/localizedRoutes';
import {
  changeLocale,
  loadAppTranslations,
  translateApp,
} from '../locales/i18n';

afterEach(async () => {
  await changeLocale('ru', { remember: false });
});

describe('localized navigation', () => {
  it('svyazyvaet vse staticheskie marshruty', () => {
    expect(Object.values(PUBLIC_ROUTES)).toHaveLength(12);
    const paths = Object.values(PUBLIC_ROUTES).flatMap(({ ru, en }) => [ru, en]);
    expect(new Set(paths).size).toBe(paths.length);

    for (const variants of Object.values(PUBLIC_ROUTES)) {
      expect(getLocalizedPathPair(variants.ru)).toEqual(variants);
      expect(getLocalizedPathPair(variants.en)).toEqual(variants);
    }
  });

  it('svyazyvaet 54 pary statej', () => {
    const ruArticles = getArticles('ru');
    const enArticles = getArticles('en');
    expect(ruArticles).toHaveLength(54);
    expect(enArticles).toHaveLength(54);

    for (const article of ruArticles) {
      const translation = getArticleTranslation(article, 'en');
      const pair = {
        ru: getArticlePath(article, 'ru'),
        en: getArticlePath(translation, 'en'),
      };
      expect(translation?.id).toBe(article.id);
      expect(getLocalizedPathPair(pair.ru)).toEqual(pair);
      expect(getLocalizedPathPair(pair.en)).toEqual(pair);
    }
  });

  it('svyazyvaet pyat tematicheskih razdelov', () => {
    const ruClusters = getArticleClusters('ru');
    const enClusters = getArticleClusters('en');
    expect(ruClusters).toHaveLength(5);
    expect(enClusters).toHaveLength(5);

    for (const cluster of ruClusters) {
      const translation = enClusters.find((item) => item.id === cluster.id);
      const pair = {
        ru: getClusterPath(cluster, 'ru'),
        en: getClusterPath(translation, 'en'),
      };
      expect(getLocalizedPathPair(pair.ru)).toEqual(pair);
      expect(getLocalizedPathPair(pair.en)).toEqual(pair);
    }
  });
});

describe('app locale', () => {
  it('sohranyaet anglijskij yazyk v puti vhoda i SSO', () => {
    expect(getPlatformStartPath('en')).toBe(
      '/app/login/?lang=en&redirect=%2Fapp%2Fonboarding%2F',
    );
    expect(buildSsoLoginUrl(
      'yandex',
      '/app/onboarding/',
      'en',
      'https://growerhub.ru',
    )).toBe(
      '/api/auth/sso/yandex/login?redirect_path=%2Fapp%2Fonboarding%2F%3Flang%3Den',
    );
  });

  it('lokalizuet mnozhestvennye formy i oshibki', async () => {
    await loadAppTranslations();
    await changeLocale('en', { remember: false });
    expect(translateApp('device_count', { count: 1 })).toBe('1 device');
    expect(translateApp('device_count', { count: 3 })).toBe('3 devices');
    expect(normalizeApiErrorMessage('Неизвестная ошибка сервера', { status: 503 }))
      .toBe('Service is temporarily unavailable');
  });

  it('ne soderzhit kirillicu v anglijskih resursah kabineta', () => {
    for (const filename of ['app.en.json', 'public.en.json', 'common.en.json']) {
      const resource = JSON.parse(fs.readFileSync(
        path.resolve(process.cwd(), 'src', 'locales', filename),
        'utf8',
      ));
      expect(Object.values(resource).join('\n')).not.toMatch(/[\u0400-\u04ff]/u);
    }
  });

  it('imeet perevod dlya vseh staticheskih kljuchej kabineta', () => {
    const ru = JSON.parse(fs.readFileSync(
      path.resolve(process.cwd(), 'src', 'locales', 'app.ru.json'),
      'utf8',
    ));
    const en = JSON.parse(fs.readFileSync(
      path.resolve(process.cwd(), 'src', 'locales', 'app.en.json'),
      'utf8',
    ));
    const sourceFiles = [];
    const collect = (directory) => {
      for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
        const target = path.join(directory, entry.name);
        if (entry.isDirectory()) {
          collect(target);
        } else if (/\.(?:js|jsx)$/u.test(entry.name) && !entry.name.includes('.test.')) {
          sourceFiles.push(target);
        }
      }
    };
    collect(path.resolve(process.cwd(), 'src'));

    const keys = new Set();
    for (const filename of sourceFiles) {
      const source = fs.readFileSync(filename, 'utf8');
      for (const match of source.matchAll(/translateApp\(\s*"([^"]+)"/gu)) {
        keys.add(match[1]);
      }
    }

    const missingRu = [...keys].filter((key) => !(key in ru));
    const missingEn = [...keys].filter((key) => !(key in en));
    expect(missingRu).toEqual([]);
    expect(missingEn).toEqual([]);
  });
});
