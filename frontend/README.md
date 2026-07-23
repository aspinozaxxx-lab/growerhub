# Frontend GrowerHub: SEO-runbook

Этот файл — точка входа для работы с публичным сайтом, русской и английской
выдачей, Яндекс Вебмастером и Яндекс Метрикой. Секреты в репозитории не хранятся.

## Архитектура языков

- Русский язык остаётся основным: `/`, `/articles/...`, `/oborudovanie/...`.
- Английские страницы находятся под `/en/` и используют естественные английские
  адреса: `/en/articles/...`, `/en/equipment/...`.
- Единый реестр пар маршрутов находится в
  `src/domain/localizedRoutes.js`. Переключатель `RU / EN` всегда ведёт на точный
  эквивалент текущей страницы.
- Короткие строки находятся в `src/locales`: пространства `common`, `public` и
  лениво загружаемое вместе с кабинетом `app`.
- Русский длинный контент остаётся в `content/pages`, `content/articles` и
  `content/equipment`.
- Английский длинный контент находится в `content/en/pages`,
  `content/en/articles` и `content/en/equipment`.
- Русский slug статьи является стабильным идентификатором перевода. Английский
  front matter содержит `translation_of` и собственный `slug`.
- `/app/...` не получает языковой префикс. Параметр `?lang=ru|en` сохраняется в
  `growerhub_locale` и проходит через SSO. В `/app/admin/` интерфейс всегда
  русский.

Сборка завершается ошибкой, если отсутствует хотя бы один из 54 переводов статьи
или нарушена связь `translation_of`. Она формирует 69 русских и 69 английских
индексируемых URL. `/privacy/`, `/terms/`, `/en/privacy/` и `/en/terms/` имеют
`noindex,follow` и не входят в sitemap.

На каждом индексируемом URL проверяются:

- self-canonical и конечный адрес с `/`;
- взаимные `hreflang="ru"` и `hreflang="en"`;
- `hreflang="x-default"` на русскую страницу;
- правильный `<html lang>`, локализованный Open Graph и `inLanguage` в JSON-LD;
- H1, основной контент и главный CTA в статическом HTML;
- отсутствие внутренних ссылок на перенаправления и смешения языков.

## Локальная проверка

Для выпуска self-service используется тот же флаг, что и в CI:

```powershell
$env:VITE_SELF_SERVICE_ENABLED = 'true'
npm ci
npm test
npm run build
npm run verify:seo
```

Снимки синтетического интерфейса для обеих локалей:

```powershell
npm run preview -- --host 127.0.0.1
npm run capture:screenshots
```

Готовые изображения находятся в `public/screenshots` и
`public/screenshots/en`. Они не должны содержать реальные IEEE, адреса, логины
или учётные данные.

После публикации:

```powershell
npm run verify:live
```

Live-проверка обходит 138 URL, внутренние ссылки, матрицу перенаправлений,
русскую и английскую 404, SPA fallback `/app/`, gzip и immutable-кеш
хешированных assets.

## Яндекс

Идентификаторы GrowerHub:

- Метрика: счётчик `110256357`;
- Вебмастер: пользователь `17567888`;
- Вебмастер: сайт `https:growerhub.ru:443`;
- sitemap: `https://growerhub.ru/sitemap.xml`.

OAuth-токен читается из последней непустой строки файла:

```text
%USERPROFILE%\.secrets\growerhub\yandex-oauth-token.txt
```

Другой путь можно передать только через окружение:

```powershell
$env:YANDEX_OAUTH_TOKEN_FILE = 'D:\private\yandex-token.txt'
```

Содержимое токена нельзя печатать, добавлять в `.env`, журнал, issue, коммит или
документацию.

Read-only аудит:

```powershell
npm run audit:seo:yandex -- --period=28 --locale=all
npm run audit:seo:yandex -- --period=28 --locale=ru
npm run audit:seo:yandex -- --period=56 --locale=en
npm run audit:seo:yandex -- --period=2026-07-01:2026-07-31 --locale=all
```

Команда проверяет production sitemap и canonical, сводку, диагностику,
индексирование, sitemap и поисковые запросы/страницы Вебмастера, а также
landing page, источник, страну и продуктовые цели Метрики. Запрос
`query-analytics/list` использует POST по спецификации Яндекса, но ничего не
изменяет.

Продуктовые цели:

- `platform_start`;
- `signup_complete`;
- `coordinator_created`;
- `coordinator_connected`;
- `first_device_seen`;
- `zone_created`;
- `automation_enabled`;
- `telegram_contact`.

В Метрику передаются только неперсональные параметры: `placement`, `page_path`,
`step`, `connection_mode`, тип сценария и `locale`. Email, user ID, coordinator
ID, IEEE и MQTT credentials передавать нельзя.

После каждого SEO-релиза:

1. Открыть Вебмастер → Индексирование → Файлы Sitemap и повторно отправить
   `https://growerhub.ru/sitemap.xml`.
2. Запросить переобход новых продуктовых страниц и приоритетных статей.
3. Убедиться, что счётчик Метрики привязан к сайту и разрешён обход страниц с
   использованием данных счётчика. Диагностика
   `NO_METRIKA_COUNTER_CRAWL_ENABLED` означает, что второй пункт ещё выключен.
4. Проверить активные диагностические сообщения. Старый `SOFT_404` должен
   исчезнуть после нового обхода настоящих 404.
5. В Представление в поиске → Региональность подать один широкий регион «СНГ».
   Яндекс обычно разрешает один регион. Если модерация отклонит «СНГ», оставить
   «Россия»; текст сайта всё равно подтверждает работу по России и странам СНГ.
6. Рекомендацию Яндекс Бизнес не выполнять без фактической организации или
   адреса.

Официальная документация:

- API Вебмастера: https://yandex.ru/dev/webmaster/;
- API Метрики: https://yandex.ru/dev/metrika/;
- региональность: https://yandex.ru/support/webmaster/ru/site-geography/site-region;
- языковые версии:
  https://yandex.ru/support/webmaster/ru/yandex-indexing/locale-pages.

## Google Search Console

Нужен доменный ресурс `growerhub.ru`. Он подтверждается DNS TXT и охватывает все
протоколы и поддомены. Если у исполнителя нет доступа к DNS, владелец:

1. создаёт доменный ресурс `growerhub.ru` в Search Console;
2. копирует выданную Google TXT-запись без изменений в DNS;
3. подтверждает ресурс;
4. отправляет `https://growerhub.ru/sitemap.xml`.

Значение TXT уникально и не должно быть придумано заранее. Для последующего API
достаточен OAuth scope `webmasters.readonly`.

Официальная документация:

- https://support.google.com/webmasters/answer/34592;
- https://developers.google.com/search/docs/specialty/international/managing-multi-regional-sites.

## Базы сравнения и цикл решений

Исходная база:

- 17 страниц в поиске;
- 86 показов;
- 9 кликов;
- 0 переходов в Telegram.

Последний зафиксированный аудит перед международным релизом:

- 18 страниц в поиске;
- 95 показов;
- 9 кликов.

Русскую выдачу оцениваем через 28 дней после переиндексации, английскую — через
56 дней. Сравниваем показы, клики, страны, landing page, `platform_start`,
`signup_complete` и основную активацию `first_device_seen`.

До окончания этих периодов новые тематические страницы не выпускаем. Backlog
для следующего решения:

- установка Zigbee2MQTT на Windows;
- Raspberry Pi и Docker;
- подключение существующего Home Assistant;
- remote MQTT bridge;
- greenhouse monitoring dashboard.

## Безопасность публикации

Существующие автоматизации и реальные растения важнее SEO-релиза. До и после
деплоя проверяются `/health`, свежесть MQTT-телеметрии, соединения backend
publisher/subscriber и журнал автоматизаций минимум за два рабочих цикла.
Проверочные команды насосам, свету и другим исполнительным устройствам не
отправляются.
