import guide from '../../../../backend/src/main/resources/advisor/interface-guide.json';
import { SITE_URL } from '../../domain/siteConfig';

const paths = new Set(Object.values(guide.sections).map(section => section.path));
const journalPath = new RegExp('^' + guide.sections.plants.journalPath.replace('{plantId}', '[1-9][0-9]*') + '$');
export function supportedAppLink(href) {
  if (typeof href !== 'string' || /[\u0000-\u0020\\]/.test(href)) return null;
  try {
    const url = new URL(href, SITE_URL);
    if (url.origin !== SITE_URL || url.username || url.password || url.hash || !(paths.has(url.pathname) || journalPath.test(url.pathname))) return null;
    const keys = [...url.searchParams.keys()];
    if (new Set(keys).size !== keys.length) return null;
    for (const key of keys) {
      const value = url.searchParams.get(key);
      if (key === 'lang' && ['ru', 'en'].includes(value)) continue;
      if (key === 'rule' && url.pathname === guide.sections.scenarios.path && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) continue;
      return null;
    }
    return url.pathname + url.search;
  } catch { return null; }
}
