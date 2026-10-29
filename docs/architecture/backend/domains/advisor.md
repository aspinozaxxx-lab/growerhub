# Домен advisor

## Назначение

Даёт советы по уходу и состояние своей фермы, ведёт диалоги помощника, готовит подтверждаемые изменения. Сохраняет прежние рекомендации по поливу; новый помощник описан в ADR-013.

## Публичный Facade

`AdvisorFacade`

- `getWateringAdvice` — прежний контракт советов по поливу.
- `assistantCapabilities`, `assistantModels`, `assistantConversations`, `createAssistantConversation`, `configureAssistantConversation` — доступ и диалоги.
- `sendAssistantMessage`, `assistantMessages`, `assistantMessage`, `cancelAssistantMessage` — идемпотентная очередь сообщений.
- `uploadAssistantPhoto`, `prepareAssistantPhoto`, `finishAssistantPhoto`, `assistantImage` — ограниченная загрузка без EXIF.
- `claimAssistantWork`, `assistantWorkAlive`, `appendAssistantText`, `beginAssistantTool`, `executeAssistantTool`, `finishAssistantTool`, `finishAssistantWork` — отдельный worker.
- `confirmAssistantProposal`, `claimAssistantApplication`, `applyAssistantProposal`, `failAssistantApplication` — подтверждение конкретной версии.
- `saveAssistantPreferences`, `processAssistantInbox`, `beginAssistantInbox`, `completeAssistantInbox`, `rejectAssistantInbox`, `assistantReportsDue`, `prepareAssistantReport`, `deliverAssistantResponses`, `maintainAssistant` — каналы, сводки и очистка.
- `assistantRuntimeStatus`, `recordAssistantRuntimeStatus` — версия Codex и уведомление владельцу.
- `assistantRuntimeMaintenance`, `prepareAssistantRuntimeUpdate`, `startAssistantRuntimeUpdate`, `recordAssistantRuntimeUpdate`, `checkAssistantRuntimeStatus` — подтверждённое обновление отдельным оператором.
- `assistantRequests`, `decideAssistantRequest` — свои заявки и административное рассмотрение.
- `assistantUsage`, `switchPublicAssistant` — административный учёт и остановка публичных ответов.

## Публичные контракты

`WateringAdvice`, `WateringAdviceBundle`, `WateringAdviceGateway`, `WateringPrevious`; `AssistantData`, `AssistantGateway`, `AssistantLimitException`.

## Владение данными

Кэш `advisor_watering_advice`; таблицы `assistant_*`: диалоги, фото, задания, предложения, предпочтения, глобальный guard и журнал квот. Заявки на доработки хранят задачу, решение, ревизию и флаг постановки уведомления; ID предложения исключает дубли. История и фото удаляются через 90 дней или с аккаунтом. Обезличенный журнал расходов сохраняется при удалении аккаунта, чтобы квота не обнулялась. Данные фермы получаются через Facade, не копируются в собственные справочники.

## Используемые домены

- `plant`, `journal` — растения, история ухода, напоминания.
- `automation` — состояние, история ресурсов, правила и команды.
- `user`, `demo` — владелец, время, связанная квота и изолированное пространство.
- `notification` — добровольный Telegram, входящая очередь и доставка.

## Внешние пользователи домена

REST `api`, шлюз и worker `llm`, worker каналов `messaging`.

## Алгоритм работы

Guard проверяет аккаунт, IP, очередь и бюджет до сети. Worker вызывает шлюз вне транзакции; инструменты используют Facade. Поиск своего правила по имени сразу возвращает план, даты и конфликты из automation; неоднозначность требует уточнения. Каталог не дублирует определения и неактивные требования. Для нового правила одной теплицы отчёт и серверная схема объединены; prepare сам проверяет. V3 hold/repeat_while не заменяют полив; ручной set требует ttlSeconds. Человек подтверждает снимок; proposalId — неизменный ключ применения. Фото своего разговора атомарно копируются в journal. Повтор не создаёт дубль и не продлевает удержание; неизвестная команда не переотправляется. Потерянный ответ сохраняет резерв, usage сверяется один раз. App и Telegram делят диалог/квоту, демо отделено. При отказе ИИ сводка приходит обычным текстом. DeepSeek Flash и личный Codex изолированы. Контекст сохраняет резерв фото. Повторы usage не расходуют шаги Codex.

## Ограничения

Месячный бюджет публичного API $2 по UTC, остальные квоты и тарифы — `assistant.*`; активные резервы учитываются при проверке. Нет shell, произвольных URL и кода. Только владелец-admin вне демо может читать чужие профили и фермы отдельными инструментами; изменения остаются собственными. Telegram включается отдельно после привязки. Инструмент подготовки ничего не применяет. Подтверждение не гарантирует физическое исполнение: интерфейс различает отправленную команду и телеметрию. Ключи, тексты и фото исключаются из аналитики. Сеть и генерация не работают в потоке автоматизации.
