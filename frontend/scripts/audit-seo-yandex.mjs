import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const SITE_URL = 'https://growerhub.ru';
const METRIKA_COUNTER_ID = 110256357;
const EXPECTED_HOST_ID = 'https:growerhub.ru:443';
const PRODUCT_GOALS = [
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
    const date2 = new Date();
    const date1 = new Date(date2);
    date1.setUTCDate(date1.getUTCDate() - days + 1);
    return { date1: formatDate(date1), date2: formatDate(date2), label: `${days} дней` };
  }
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[:.]{1,2}(\d{4}-\d{2}-\d{2})$/);
  if (!match) {
    throw new Error('Период задаётся числом дней или диапазоном YYYY-MM-DD:YYYY-MM-DD');
  }
  return { date1: match[1], date2: match[2], label: `${match[1]} — ${match[2]}` };
};

const locale = readArgument('locale', 'all');
if (!['ru', 'en', 'all'].includes(locale)) {
  throw new Error('Допустимые значения --locale: ru, en, all');
}
const period = resolvePeriod(readArgument('period', '28'));
const tokenPath = process.env.YANDEX_OAUTH_TOKEN_FILE
  || path.join(os.homedir(), '.secrets', 'growerhub', 'yandex-oauth-token.txt');
if (!fs.existsSync(tokenPath)) {
  throw new Error(`Файл OAuth-токена не найден: ${tokenPath}`);
}
const token = fs.readFileSync(tokenPath, 'utf8')
  .split(/\r?\n/)
  .map((value) => value.trim())
  .filter(Boolean)
  .at(-1);
if (!token) throw new Error(`В файле нет OAuth-токена: ${tokenPath}`);

const authHeaders = {
  Authorization: `OAuth ${token}`,
  Accept: 'application/json',
};

const requestJson = async (url, options = {}) => {
  const response = await fetch(url, {
    signal: AbortSignal.timeout(30000),
    ...options,
    headers: { ...authHeaders, ...(options.headers || {}) },
  });
  const body = await response.text();
  let data = {};
  try {
    data = body ? JSON.parse(body) : {};
  } catch {
    data = {};
  }
  if (!response.ok) {
    const code = data.error_code || data.code || response.status;
    throw new Error(`${code}: ${data.error_message || data.message || response.statusText}`);
  }
  return data;
};

const safeRequest = async (label, operation) => {
  try {
    return { ok: true, data: await operation() };
  } catch (error) {
    return { ok: false, label, error: error.message };
  }
};

const printSection = (title) => console.log(`\n${title}`);
const printRows = (rows, columns) => {
  if (!rows.length) {
    console.log('  Нет данных за выбранный период.');
    return;
  }
  console.table(rows.slice(0, 20).map((row) => (
    Object.fromEntries(columns.map((column) => [column, row[column]]))
  )));
};

const localeForPath = (value) => {
  try {
    return new URL(value, SITE_URL).pathname.startsWith('/en/') ? 'en' : 'ru';
  } catch {
    return 'ru';
  }
};
const localeMatches = (value) => locale === 'all' || localeForPath(value) === locale;
const dimensionName = (row, index) => row.dimensions?.[index]?.name
  || row.dimensions?.[index]?.id
  || '';

const auditSitemap = async () => {
  const response = await fetch(`${SITE_URL}/sitemap.xml`, {
    signal: AbortSignal.timeout(30000),
    headers: { 'user-agent': 'GrowerHub SEO audit' },
  });
  if (!response.ok) throw new Error(`sitemap.xml вернул HTTP ${response.status}`);
  const xml = await response.text();
  const urls = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]);
  const selectedUrls = urls.filter(localeMatches);
  const failures = [];

  for (let index = 0; index < selectedUrls.length; index += 10) {
    await Promise.all(selectedUrls.slice(index, index + 10).map(async (url) => {
      try {
        const page = await fetch(url, {
          signal: AbortSignal.timeout(15000),
          headers: { 'user-agent': 'GrowerHub SEO audit' },
        });
        const html = await page.text();
        const canonical = html.match(/<link rel="canonical" href="([^"]+)"/i)?.[1];
        if (page.status !== 200 || canonical !== url || page.url !== url) {
          failures.push({
            url,
            status: page.status,
            finalUrl: page.url,
            canonical: canonical || 'нет',
          });
        }
      } catch (error) {
        failures.push({ url, status: 'ошибка', finalUrl: error.message, canonical: 'нет' });
      }
    }));
  }

  return {
    urls,
    selectedUrls,
    ru: urls.filter((url) => localeForPath(url) === 'ru').length,
    en: urls.filter((url) => localeForPath(url) === 'en').length,
    failures,
  };
};

const getWebmasterContext = async () => {
  const user = await requestJson('https://api.webmaster.yandex.net/v4/user');
  const userId = user.user_id ?? user.uid;
  const hosts = await requestJson(`https://api.webmaster.yandex.net/v4/user/${userId}/hosts`);
  const host = (hosts.hosts || []).find((item) => item.host_id === EXPECTED_HOST_ID)
    || (hosts.hosts || []).find((item) => item.ascii_host_url === SITE_URL)
    || (hosts.hosts || []).find((item) => item.unicode_host_url === SITE_URL);
  if (!host) throw new Error(`Сайт ${SITE_URL} не найден в Вебмастере`);
  const base = `https://api.webmaster.yandex.net/v4/user/${userId}/hosts/${encodeURIComponent(host.host_id)}`;
  return { userId, host, base };
};

const queryWebmasterAnalytics = (base, textIndicator) => requestJson(
  `${base}/query-analytics/list`,
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=UTF-8' },
    body: JSON.stringify({
      offset: 0,
      limit: 500,
      device_type_indicator: 'ALL',
      search_location: 'ALL_LOCATIONS',
      text_indicator: textIndicator,
    }),
  },
);

const aggregateWebmasterRows = (data, textIndicator) => (
  (data.text_indicator_to_statistics || [])
    .map((row) => {
      const statistics = (row.statistics || []).filter((item) => (
        item.date >= period.date1 && item.date <= period.date2
      ));
      const total = (field) => statistics
        .filter((item) => item.field === field)
        .reduce((sum, item) => sum + Number(item.value || 0), 0);
      const positions = statistics
        .filter((item) => item.field === 'POSITION')
        .map((item) => Number(item.value))
        .filter(Number.isFinite);
      const value = row.text_indicator?.value || '';
      return {
        [textIndicator === 'URL' ? 'Страница' : 'Запрос']: value,
        Показы: total('IMPRESSIONS'),
        Клики: total('CLICKS'),
        CTR: total('IMPRESSIONS') ? `${(total('CLICKS') * 100 / total('IMPRESSIONS')).toFixed(1)}%` : '0%',
        Позиция: positions.length
          ? (positions.reduce((sum, item) => sum + item, 0) / positions.length).toFixed(1)
          : '—',
      };
    })
    .filter((row) => {
      const value = row[textIndicator === 'URL' ? 'Страница' : 'Запрос'];
      return textIndicator !== 'URL' || localeMatches(value);
    })
    .sort((left, right) => right.Показы - left.Показы)
);

const metrikaData = (parameters) => {
  const query = new URLSearchParams({
    ids: String(METRIKA_COUNTER_ID),
    date1: period.date1,
    date2: period.date2,
    accuracy: 'full',
    limit: '10000',
    lang: 'ru',
    ...parameters,
  });
  return requestJson(`https://api-metrika.yandex.net/stat/v1/data?${query}`);
};

const auditMetrika = async () => {
  const goalsResponse = await requestJson(
    `https://api-metrika.yandex.net/management/v1/counter/${METRIKA_COUNTER_ID}/goals`,
  );
  const goals = goalsResponse.goals || [];
  const goalsByName = new Map(goals.map((goal) => [goal.name, goal]));
  const configuredGoals = PRODUCT_GOALS
    .map((name) => ({ name, goal: goalsByName.get(name) }))
    .filter((item) => item.goal);
  const missingGoals = PRODUCT_GOALS.filter((name) => !goalsByName.has(name));
  const goalMetrics = configuredGoals.map(
    ({ goal }) => `ym:s:goal${goal.id}reaches`,
  );
  const report = await metrikaData({
    dimensions: 'ym:s:startURL,ym:s:lastTrafficSource,ym:s:regionCountry',
    metrics: ['ym:s:visits', 'ym:s:users', 'ym:s:pageviews', ...goalMetrics].join(','),
  });
  const rows = (report.data || [])
    .map((row) => {
      const startUrl = dimensionName(row, 0);
      const metrics = row.metrics || [];
      const result = {
        Локаль: localeForPath(startUrl).toUpperCase(),
        Страница: startUrl,
        Источник: dimensionName(row, 1) || 'Не определён',
        Страна: dimensionName(row, 2) || 'Не определена',
        Визиты: Number(metrics[0] || 0),
        Посетители: Number(metrics[1] || 0),
        Просмотры: Number(metrics[2] || 0),
      };
      configuredGoals.forEach(({ name }, index) => {
        result[name] = Number(metrics[index + 3] || 0);
      });
      return result;
    })
    .filter((row) => locale === 'all' || row.Локаль.toLowerCase() === locale);

  return { rows, configuredGoals, missingGoals };
};

console.log(`GrowerHub SEO-аудит: ${period.label}, локаль ${locale.toUpperCase()}`);
console.log(`Период: ${period.date1} — ${period.date2}`);

printSection('Sitemap и self-canonical');
const sitemapAudit = await auditSitemap();
console.log(
  `  URL: ${sitemapAudit.urls.length}; RU: ${sitemapAudit.ru}; EN: ${sitemapAudit.en}; `
  + `проверено по фильтру: ${sitemapAudit.selectedUrls.length}`,
);
if (sitemapAudit.failures.length) {
  console.table(sitemapAudit.failures.slice(0, 20));
  process.exitCode = 1;
} else {
  console.log('  Все выбранные URL отвечают 200 без перенаправления, canonical совпадает.');
}

printSection('Яндекс Вебмастер');
const webmasterContext = await getWebmasterContext();
console.log(`  Пользователь: ${webmasterContext.userId}; сайт: ${webmasterContext.host.host_id}`);
const dateQuery = new URLSearchParams({ date_from: period.date1, date_to: period.date2 });
const popularQuery = new URLSearchParams({
  order_by: 'TOTAL_SHOWS',
  date_from: period.date1,
  date_to: period.date2,
  limit: '500',
});
for (const indicator of ['TOTAL_SHOWS', 'TOTAL_CLICKS', 'AVG_SHOW_POSITION', 'AVG_CLICK_POSITION']) {
  popularQuery.append('query_indicator', indicator);
}
const webmasterReports = await Promise.all([
  safeRequest('Сводка', () => requestJson(`${webmasterContext.base}/summary`)),
  safeRequest('Диагностика', () => requestJson(`${webmasterContext.base}/diagnostics`)),
  safeRequest('Sitemap', () => requestJson(`${webmasterContext.base}/sitemaps`)),
  safeRequest(
    'История индексирования',
    () => requestJson(`${webmasterContext.base}/indexing/history?${dateQuery}`),
  ),
  safeRequest(
    'Страницы в поиске',
    () => requestJson(`${webmasterContext.base}/search-urls/in-search/history?${dateQuery}`),
  ),
  safeRequest(
    'Популярные запросы',
    () => requestJson(`${webmasterContext.base}/search-queries/popular?${popularQuery}`),
  ),
  safeRequest('Запросы', () => queryWebmasterAnalytics(webmasterContext.base, 'QUERY')),
  safeRequest('Страницы', () => queryWebmasterAnalytics(webmasterContext.base, 'URL')),
]);
for (const report of webmasterReports.filter((item) => !item.ok)) {
  console.log(`  Предупреждение: ${report.label}: ${report.error}`);
}
const [
  summary,
  diagnostics,
  webmasterSitemaps,
  indexing,
  inSearch,
  popular,
  queryAnalytics,
  pageAnalytics,
] = webmasterReports.map((item) => item.data || {});
console.log('  Сводка:', JSON.stringify(summary));
const presentDiagnostics = Object.entries(diagnostics.problems || {})
  .filter(([, problem]) => problem.state === 'PRESENT')
  .map(([code, problem]) => ({
    Код: code,
    Уровень: problem.severity,
    Обновлено: problem.last_state_update || '—',
  }));
const indexingPoints = Object.values(indexing.indicators || {})
  .reduce((count, points) => count + points.length, 0);
const inSearchHistory = inSearch.history || [];
console.log(
  `  Активная диагностика: ${presentDiagnostics.length}; `
  + `Sitemap: ${(webmasterSitemaps.sitemaps || []).length} файлов`,
);
console.log(
  `  Точек истории индексирования: ${indexingPoints}; `
  + `последнее число страниц в поиске: ${inSearchHistory.at(-1)?.value ?? 'нет данных'}`,
);
printRows(presentDiagnostics, ['Код', 'Уровень', 'Обновлено']);
printRows(
  (webmasterSitemaps.sitemaps || []).map((item) => ({
    Sitemap: item.sitemap_url,
    URL: item.urls_count,
    Ошибки: item.errors_count,
    Загружен: item.last_access_date,
  })),
  ['Sitemap', 'URL', 'Ошибки', 'Загружен'],
);
const popularRows = (popular.queries || []).map((row) => ({
  Запрос: row.query_text,
  Показы: row.indicators?.TOTAL_SHOWS || 0,
  Клики: row.indicators?.TOTAL_CLICKS || 0,
  Позиция: row.indicators?.AVG_SHOW_POSITION?.toFixed?.(1)
    || row.indicators?.AVG_SHOW_POSITION
    || '—',
}));
printRows(popularRows, ['Запрос', 'Показы', 'Клики', 'Позиция']);

printSection('Запросы за доступный период мониторинга Вебмастера');
printRows(
  aggregateWebmasterRows(queryAnalytics, 'QUERY'),
  ['Запрос', 'Показы', 'Клики', 'CTR', 'Позиция'],
);
printSection('Страницы за доступный период мониторинга Вебмастера');
printRows(
  aggregateWebmasterRows(pageAnalytics, 'URL'),
  ['Страница', 'Показы', 'Клики', 'CTR', 'Позиция'],
);

printSection('Яндекс Метрика');
const metrika = await auditMetrika();
console.log(
  `  Продуктовые цели: ${metrika.configuredGoals.length}/${PRODUCT_GOALS.length}; `
  + `отсутствуют: ${metrika.missingGoals.join(', ') || 'нет'}`,
);
const totals = metrika.rows.reduce((result, row) => {
  const key = row.Локаль;
  result[key] ||= { Локаль: key, Визиты: 0, Просмотры: 0 };
  result[key].Визиты += row.Визиты;
  result[key].Просмотры += row.Просмотры;
  for (const { name } of metrika.configuredGoals) {
    result[key][name] = (result[key][name] || 0) + row[name];
  }
  return result;
}, {});
printRows(
  Object.values(totals),
  ['Локаль', 'Визиты', 'Просмотры', ...metrika.configuredGoals.map(({ name }) => name)],
);
printSection('Landing page, источник и страна');
printRows(
  metrika.rows.sort((left, right) => right.Визиты - left.Визиты),
  ['Локаль', 'Страница', 'Источник', 'Страна', 'Визиты', 'Посетители', 'Просмотры'],
);
