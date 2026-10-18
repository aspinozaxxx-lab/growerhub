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
- `assistantUsage`, `switchPublicAssistant` — административный учёт и остановка публичных ответов.

## Публичные контракты

`WateringAdvice`, `WateringAdviceBundle`, `WateringAdviceGateway`, `WateringPrevious`; `AssistantData`, `AssistantGateway`, `AssistantLimitException`.

## Владение данными

Кэш `advisor_watering_advice`; таблицы `assistant_*`: диалоги, фото, задания, предложения, предпочтения, глобальный guard и журнал квот. История и фото удаляются через 90 дней или с аккаунтом. Обезличенный журнал расходов сохраняется при удалении аккаунта, чтобы квота не обнулялась. Данные фермы получаются через Facade, не копируются в собственные справочники.

## Используемые домены

- `plant`, `journal` — растения, история ухода, напоминания.
- `automation` — состояние, история ресурсов, правила и команды.
- `user`, `demo` — владелец, время, связанная квота и изолированное пространство.
- `notification` — добровольный Telegram, входящая очередь и доставка.

## Внешние пользователи домена

REST `api`, шлюз и worker `llm`, worker каналов `messaging`.

## Алгоритм работы

Под блокировкой guard проверяются аккаунт, IP, очередь и бюджеты; резерв сохраняется до сети. Worker вызывает закрытый шлюз вне транзакции, предметные инструменты — через Facade. Только человек подтверждает версию предложения; применение повторно проверяет отпечаток настроек. Неизвестный результат команды не переотправляется. Потерянный ответ ИИ учитывается по полному резерву, позднее известное использование сверяется один раз. App и Telegram имеют общий диалог и квоту; демо отделено от настоящей фермы. Сводки считаются backend и доставляются обычным текстом, если генерация недоступна. Платная модель DeepSeek Flash, личный Codex доступен только заданному user ID; автоматического переключения между ними нет.

## Ограничения

Месячный бюджет публичного API $2 по UTC, остальные квоты и тарифы — `assistant.*`; активные резервы учитываются при проверке. Нет shell, произвольных URL, кода и чужих данных. Telegram включается отдельно после привязки. Инструмент подготовки ничего не применяет. Подтверждение не гарантирует физическое исполнение: интерфейс различает отправленную команду и телеметрию. Ключи, тексты и фото исключаются из аналитики. Сеть и генерация не работают в потоке автоматизации.
