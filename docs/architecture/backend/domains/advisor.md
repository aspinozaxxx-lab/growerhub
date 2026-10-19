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

Guard проверяет аккаунт, IP, очередь и бюджет, резервирует расход до сети. Worker вызывает закрытый шлюз вне транзакции, инструменты — через Facade. Справка интерфейса версионируется; ссылки и журналы доступны владельцу. Схема правил v2 и план дат приходят из automation. Предложения растений, размещения, ухода и сценариев подтверждает человек; применение повторяет проверку снимка. Фото выбираются из вложений своего разговора и атомарно копируются в journal с его квотами. Повтор подтверждения не создаёт дубль. Неизвестная команда не переотправляется. Потерянный ответ учитывается по резерву, известное использование сверяется один раз. App и Telegram имеют общий диалог и квоту; демо отделено. Сводки считаются backend, при отказе ИИ доставляются обычным текстом. Публичный DeepSeek Flash и личный Codex заданного user ID изолированы, автоматического переключения нет. Контекст не дублирует схемы инструментов в системной инструкции; старая переписка сокращается с учётом резерва изображений выбранного провайдера.

## Ограничения

Месячный бюджет публичного API $2 по UTC, остальные квоты и тарифы — `assistant.*`; активные резервы учитываются при проверке. Нет shell, произвольных URL и кода. Только владелец-admin вне демо может читать чужие профили и фермы отдельными инструментами; изменения остаются собственными. Telegram включается отдельно после привязки. Инструмент подготовки ничего не применяет. Подтверждение не гарантирует физическое исполнение: интерфейс различает отправленную команду и телеметрию. Ключи, тексты и фото исключаются из аналитики. Сеть и генерация не работают в потоке автоматизации.
