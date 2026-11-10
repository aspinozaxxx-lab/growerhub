import aboutContent from '../../content/pages/about.json';
import homeContent from '../../content/pages/home.json';
import miniFarmContent from '../../content/pages/mini-farm.json';
import platformContent from '../../content/pages/platform.json';
import legalContent from '../../content/pages/legal.json';
import equipmentContent from '../../content/equipment/catalog.json';
import aboutContentEn from '../../content/en/pages/about.json';
import homeContentEn from '../../content/en/pages/home.json';
import miniFarmContentEn from '../../content/en/pages/mini-farm.json';
import platformContentEn from '../../content/en/pages/platform.json';
import legalContentEn from '../../content/en/pages/legal.json';
import equipmentContentEn from '../../content/en/equipment/catalog.json';
import { getCurrentLocale } from '../locales/i18n';

const contentByLocale = {
  ru: {
    aboutContent,
    equipmentContent,
    homeContent,
    legalContent,
    miniFarmContent,
    platformContent,
  },
  en: {
    aboutContent: aboutContentEn,
    equipmentContent: equipmentContentEn,
    homeContent: homeContentEn,
    legalContent: legalContentEn,
    miniFarmContent: miniFarmContentEn,
    platformContent: platformContentEn,
  },
};

export const getPageContent = (locale = getCurrentLocale()) => (
  contentByLocale[locale] || contentByLocale.ru
);

const selectedContent = getPageContent();

export {
  selectedContent as localizedContent,
};

export const {
  aboutContent: localizedAboutContent,
  equipmentContent: localizedEquipmentContent,
  homeContent: localizedHomeContent,
  legalContent: localizedLegalContent,
  miniFarmContent: localizedMiniFarmContent,
  platformContent: localizedPlatformContent,
} = selectedContent;

// Translitem: sovmestimye imena sohranjajut tekuschie importy stranic.
export {
  localizedAboutContent as aboutContent,
  localizedEquipmentContent as equipmentContent,
  localizedHomeContent as homeContent,
  localizedLegalContent as legalContent,
  localizedMiniFarmContent as miniFarmContent,
  localizedPlatformContent as platformContent,
};
