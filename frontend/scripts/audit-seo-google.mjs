import { getGoogleAccessToken } from './lib/google-oauth.mjs';

const SITE_URL = 'https://growerhub.ru';
const SEARCH_CONSOLE_SITE = 'sc-domain:growerhub.ru';
const GOOGLE_ANALYTICS_PROPERTY_ID = '546877711';
const PRODUCT_EVENTS = [
  'platform_start',
  'signup_complete',
  'coordinator_created',
  'coordinator_connected',
  'first_device_seen',
  'zone_created',
  'automation_enabled',
  'telegram_contact',
];

const readArgument = (name, fallback) => {
  const prefixed = `--${name}=`;
  const inline = process.argv.find((value) => value.startsWith(prefixed));
  if (inline) return inline.slice(prefixed.length);
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
};

const formatDate = (date) => date.toISOString().slice(0, 10);
const resolvePeriod = (value) => {
  if (/^\d+$/.test(value)) {
    const days = Math.max(1, Number(value));
    const endDate = new Date();
    endDate.setUTCDate(endDate.getUTCDate() - 1);
    const startDate = new Date(endDate);
    startDate.setUTCDate(startDate.getUTCDate() - days + 1);
    return { startDate: formatDate(startDate), endDate: formatDate(endDate), label: `${days} дней` };
  }
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[:.]{1,2}(\d{4}-\d{2}-\d{2})$/);
  if (!match) {
    throw new Error('Период задаётся числом дней или диапазоном YYYY-MM-DD:YYYY-MM-DD');
  }
  return { startDate: match[1], endDate: match[2], label: `${match[1]} — ${match[2]}` };
};

const locale = readArgument('locale', 'all');
if (!['ru', 'en', 'all'].includes(locale)) {
  throw new Error('Допустимые значения --locale: ru, en, all');
}
const period = resolvePeriod(readArgument('period', '28'));
const accessToken = await getGoogleAccessToken();

const requestJson = async (url, options = {}) => {
  const response = await fetch(url, {
    signal: AbortSignal.timeout(30000),
    ...options,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/json',
      ...(options.headers || {}),
    },
  });
  const body = await response.text();
  let data = {};
  try {
    data = body ? JSON.parse(body) : {};
  } catch {
    data = {};
  }
  if (!response.ok) {
    const details = data.error?.message || data.error_description || response.statusText;
    throw new Error(`${response.status}: ${details}`);
  }
  return data;
};

const printRows = (rows, columns) => {
  if (!rows.length) {
    console.log('  Нет данных за выбранный период.');
    return;
  }
  console.table(rows.slice(0, 20).map((row) => (
    Object.fromEntries(columns.map((column) => [column, row[column]]))
  )));
};

const localeForUrl = (value) => {
  try {
    return new URL(value, SITE_URL).pathname.startsWith('/en/') ? 'en' : 'ru';
  } catch {
    return 'ru';
  }
};
const localeMatches = (value) => locale === 'all' || localeForUrl(value) === locale;

const searchConsoleLocaleFilter = locale === 'all' ? undefined : [{
  groupType: 'and',
  filters: [{
    dimension: 'page',
    operator: locale === 'en' ? 'contains' : 'notContains',
    expression: '/en/',
  }],
}];

const searchConsoleRequest = (body) => requestJson(
  `https://www.googleapis.com/webmasters/v3/sites/${encodeURIComponent(SEARCH_CONSOLE_SITE)}/searchAnalytics/query`,
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      startDate: period.startDate,
      endDate: period.endDate,
      dataState: 'all',
      rowLimit: 25000,
      type: 'web',
      ...(searchConsoleLocaleFilter
        ? { dimensionFilterGroups: searchConsoleLocaleFilter }
        : {}),
      ...body,
    }),
  },
);

const aggregateSearchRows = (
  response,
  keyName,
  { keyIndex = 0, pageIndex = null } = {},
) => {
  const aggregated = new Map();
  for (const row of response.rows || []) {
    if (pageIndex !== null && !localeMatches(row.keys?.[pageIndex] || '')) continue;
    const key = row.keys?.[keyIndex] || '';
    const current = aggregated.get(key) || {
      clicks: 0,
      impressions: 0,
      weightedPosition: 0,
    };
    const impressions = Number(row.impressions || 0);
    current.clicks += Number(row.clicks || 0);
    current.impressions += impressions;
    current.weightedPosition += Number(row.position || 0) * impressions;
    aggregated.set(key, current);
  }

  return [...aggregated.entries()]
    .map(([key, values]) => ({
      [keyName]: key,
      Клики: values.clicks,
      Показы: values.impressions,
      CTR: `${(values.impressions ? values.clicks / values.impressions * 100 : 0).toFixed(1)}%`,
      Позиция: (values.impressions ? values.weightedPosition / values.impressions : 0).toFixed(1),
    }))
    .sort((left, right) => right.Показы - left.Показы || right.Клики - left.Клики);
};

const gaLocaleFilter = (fieldName) => {
  if (locale === 'all') return undefined;
  const expression = {
    filter: {
      fieldName,
      stringFilter: {
        matchType: 'BEGINS_WITH',
        value: '/en/',
        caseSensitive: false,
      },
    },
  };
  return locale === 'en' ? expression : { notExpression: expression };
};

const gaRequest = (body) => requestJson(
  `https://analyticsdata.googleapis.com/v1beta/properties/${GOOGLE_ANALYTICS_PROPERTY_ID}:runReport`,
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      dateRanges: [{ startDate: period.startDate, endDate: period.endDate }],
      limit: '10000',
      ...body,
    }),
  },
);

console.log(`GrowerHub Google-аудит: ${period.label}, локаль ${locale.toUpperCase()}`);
console.log(`Период: ${period.startDate} — ${period.endDate}`);

console.log('\nGoogle Search Console');
const sites = await requestJson('https://www.googleapis.com/webmasters/v3/sites');
const site = (sites.siteEntry || []).find((item) => item.siteUrl === SEARCH_CONSOLE_SITE);
if (!site) {
  throw new Error(`Доменный ресурс ${SEARCH_CONSOLE_SITE} не найден в Search Console`);
}
console.log(`  Ресурс: ${site.siteUrl}; доступ: ${site.permissionLevel}`);
const sitemapResponse = await requestJson(
  `https://www.googleapis.com/webmasters/v3/sites/${encodeURIComponent(SEARCH_CONSOLE_SITE)}/sitemaps`,
);
const sitemapRows = (sitemapResponse.sitemap || []).map((item) => ({
  Sitemap: item.path,
  Отправлен: item.lastSubmitted || '—',
  Загружен: item.lastDownloaded || '—',
  Ошибки: Number(item.errors || 0),
  Предупреждения: Number(item.warnings || 0),
}));
printRows(sitemapRows, ['Sitemap', 'Отправлен', 'Загружен', 'Ошибки', 'Предупреждения']);

const [queriesResponse, pagesResponse, countriesResponse, devicesResponse] = await Promise.all([
  searchConsoleRequest({ dimensions: ['query'] }),
  searchConsoleRequest({ dimensions: ['page'] }),
  searchConsoleRequest({ dimensions: ['country'] }),
  searchConsoleRequest({ dimensions: ['device'] }),
]);

console.log('\nПоисковые запросы Google');
printRows(
  aggregateSearchRows(queriesResponse, 'Запрос'),
  ['Запрос', 'Показы', 'Клики', 'CTR', 'Позиция'],
);
console.log('\nСтраницы Google');
printRows(
  aggregateSearchRows(pagesResponse, 'Страница').filter((row) => localeMatches(row.Страница)),
  ['Страница', 'Показы', 'Клики', 'CTR', 'Позиция'],
);
console.log('\nСтраны Google Search');
printRows(
  aggregateSearchRows(countriesResponse, 'Страна'),
  ['Страна', 'Показы', 'Клики', 'CTR', 'Позиция'],
);
console.log('\nУстройства Google Search');
printRows(
  aggregateSearchRows(devicesResponse, 'Устройство'),
  ['Устройство', 'Показы', 'Клики', 'CTR', 'Позиция'],
);

console.log('\nGoogle Analytics 4');
const landingFilter = gaLocaleFilter('landingPagePlusQueryString');
const landingResponse = await gaRequest({
  dimensions: [
    { name: 'landingPagePlusQueryString' },
    { name: 'sessionDefaultChannelGroup' },
    { name: 'country' },
  ],
  metrics: [
    { name: 'sessions' },
    { name: 'activeUsers' },
    { name: 'screenPageViews' },
  ],
  ...(landingFilter ? { dimensionFilter: landingFilter } : {}),
  orderBys: [{ metric: { metricName: 'sessions' }, desc: true }],
});
const landingRows = (landingResponse.rows || []).map((row) => ({
  Страница: row.dimensionValues?.[0]?.value || '—',
  Канал: row.dimensionValues?.[1]?.value || '—',
  Страна: row.dimensionValues?.[2]?.value || '—',
  Сессии: Number(row.metricValues?.[0]?.value || 0),
  Пользователи: Number(row.metricValues?.[1]?.value || 0),
  Просмотры: Number(row.metricValues?.[2]?.value || 0),
}));
console.log('\nВсе landing pages GA4, включая кабинет');
printRows(
  landingRows,
  ['Страница', 'Канал', 'Страна', 'Сессии', 'Пользователи', 'Просмотры'],
);

const publicOrganicExpressions = [
  {
    filter: {
      fieldName: 'sessionDefaultChannelGroup',
      stringFilter: { matchType: 'EXACT', value: 'Organic Search', caseSensitive: false },
    },
  },
  {
    notExpression: {
      filter: {
        fieldName: 'landingPagePlusQueryString',
        stringFilter: { matchType: 'BEGINS_WITH', value: '/app', caseSensitive: false },
      },
    },
  },
];
if (landingFilter) publicOrganicExpressions.push(landingFilter);
const publicOrganicResponse = await gaRequest({
  dimensions: [
    { name: 'landingPagePlusQueryString' },
    { name: 'country' },
  ],
  metrics: [
    { name: 'sessions' },
    { name: 'activeUsers' },
    { name: 'screenPageViews' },
  ],
  dimensionFilter: { andGroup: { expressions: publicOrganicExpressions } },
  orderBys: [{ metric: { metricName: 'sessions' }, desc: true }],
});
const publicOrganicRows = (publicOrganicResponse.rows || []).map((row) => ({
  Страница: row.dimensionValues?.[0]?.value || '—',
  Страна: row.dimensionValues?.[1]?.value || '—',
  Сессии: Number(row.metricValues?.[0]?.value || 0),
  Пользователи: Number(row.metricValues?.[1]?.value || 0),
  Просмотры: Number(row.metricValues?.[2]?.value || 0),
}));
console.log('\nПубличные organic landing pages GA4 без /app');
printRows(
  publicOrganicRows,
  ['Страница', 'Страна', 'Сессии', 'Пользователи', 'Просмотры'],
);

const eventExpressions = [
  {
    filter: {
      fieldName: 'eventName',
      inListFilter: { values: PRODUCT_EVENTS, caseSensitive: true },
    },
  },
];
const eventLocaleFilter = gaLocaleFilter('pagePath');
if (eventLocaleFilter) eventExpressions.push(eventLocaleFilter);
const eventsResponse = await gaRequest({
  dimensions: [{ name: 'eventName' }, { name: 'pagePath' }],
  metrics: [{ name: 'eventCount' }, { name: 'totalUsers' }],
  dimensionFilter: eventExpressions.length === 1
    ? eventExpressions[0]
    : { andGroup: { expressions: eventExpressions } },
  orderBys: [{ metric: { metricName: 'eventCount' }, desc: true }],
});
const eventRows = (eventsResponse.rows || []).map((row) => ({
  Событие: row.dimensionValues?.[0]?.value || '—',
  Страница: row.dimensionValues?.[1]?.value || '—',
  Количество: Number(row.metricValues?.[0]?.value || 0),
  Пользователи: Number(row.metricValues?.[1]?.value || 0),
}));
console.log('\nПродуктовые события GA4');
printRows(eventRows, ['Событие', 'Страница', 'Количество', 'Пользователи']);
