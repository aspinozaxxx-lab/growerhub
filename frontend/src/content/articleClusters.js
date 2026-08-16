import { getPublicLocale, normalizeLocale } from '../domain/localizedRoutes.js';

const getInitialLocale = () => (
  typeof window === 'undefined' ? 'ru' : getPublicLocale(window.location.pathname)
);

const featured = {
  watering: [
    'avtopoliv-dlya-rasteniy-kak-vybrat',
    'kontroller-poliva-protiv-taymera',
    'datchik-vlazhnosti-pochvy-dlya-avtopoliva',
    'bezopasnyy-avtopoliv-limity-i-avariynyy-stop',
    'startovyy-komplekt-umnogo-poliva',
  ],
  journal: [
    'dnevnik-rasteniya-chto-zapisyvat',
    'zhurnal-poliva-obem-ph-udobreniya',
    'pereliv-ili-nedoliv-po-dannym-datchika',
    'istoriya-mikroklimata-i-grafiki',
    'nedelnyy-plan-uhoda-za-rasteniyami',
  ],
  zigbee: [
    'zigbee-dlya-teplitsy-kakie-ustroystva-polezny',
    'zigbee2mqtt-prostymi-slovami',
    'ustanovka-zigbee2mqtt-na-windows',
    'podklyuchit-zigbee-datchik-temperatury-vlazhnosti',
    'zigbee-datchik-protechki-dlya-avtopoliva',
    'gotovyy-zigbee-hub-dlya-growerhub',
  ],
  farm: [
    'mini-ferma-iz-dvuh-grouboksov-dashboard',
    'avtomatizatsiya-teplitsy-chto-kontrolirovat',
    'monitoring-neskolkih-boksov',
    'uvedomleniya-v-mini-ferme',
    'masshtabirovanie-ot-boksa-do-mini-fermy',
  ],
  diy: [
    'home-assistant-dlya-rasteniy',
    'zigbee-klapan-poliva-home-assistant',
    'mqtt-avtopoliv-kakie-topiki-nuzhny',
    'mqtt-discovery-home-assistant',
    'growerhub-i-home-assistant-cherez-mqtt',
    'diy-ili-gotovyy-kontroller-poliva',
  ],
};

const ru = [
  {
    id: 'avtopoliv-i-kontroller-vyrashchivaniya',
    slug: 'avtopoliv-i-kontroller-vyrashchivaniya',
    title: 'Автополив и контроллер выращивания',
    description: 'Практические материалы о поливе, датчиках, реле, свете и безопасной автоматизации для дома, гроубокса и теплицы.',
    fit: 'У вас есть одна или несколько зон полива, а таймеров и ручных проверок уже недостаточно.',
    tasks: 'Выбор датчиков и исполнительных устройств, проверка расхода воды, лимиты одного запуска, паузы и аварийная остановка.',
    keywords: [
      'автополив для растений',
      'автоматический полив комнатных растений',
      'автополив для теплицы',
      'контроллер полива',
      'датчик влажности почвы для полива',
      'умный полив',
      'автоматизация полива',
      'полив по датчику влажности',
      'безопасный автополив',
      'гроубокс автоматизация',
    ],
    featuredArticles: featured.watering,
  },
  {
    id: 'zhurnal-i-sovetnik-uhoda',
    slug: 'zhurnal-i-sovetnik-uhoda',
    title: 'Журнал и советник ухода',
    description: 'Раздел о дневнике растения, журнале полива, наблюдениях, графиках и аккуратных рекомендациях по уходу.',
    fit: 'Нужно сопоставлять состояние растений с поливами, показаниями датчиков и изменениями ухода, а не полагаться на память.',
    tasks: 'Структура журнала, отметки о поливах и обслуживании, чтение графиков и сравнение условий между циклами.',
    keywords: [
      'дневник растений',
      'журнал полива растений',
      'график полива растений',
      'уход за растениями по датчикам',
      'советы по поливу растений',
      'pH воды для растений',
      'подкормка растений',
      'фото дневник растений',
      'история микроклимата',
      'рекомендации по уходу за растениями',
    ],
    featuredArticles: featured.journal,
  },
  {
    id: 'zigbee-hub-i-ustroystva',
    slug: 'zigbee-hub-i-ustroystva',
    title: 'Zigbee2MQTT: координаторы, датчики и устройства',
    description: 'Пошаговый путь от USB-координатора и установки Zigbee2MQTT до pairing, проверки совместимости, покрытия сети и подключения устройств к GrowerHub.',
    fit: 'У вас уже есть Zigbee или Zigbee2MQTT либо вы выбираете беспроводные датчики для теплицы, бокса или стеллажа.',
    tasks: 'Проверка совместимости, pairing, покрытие сети, availability, выбор датчиков, розеток и реле по реальной нагрузке.',
    keywords: [
      'Zigbee датчик температуры влажности',
      'Zigbee для теплицы',
      'Zigbee2MQTT',
      'умная розетка Zigbee для растений',
      'Zigbee датчик протечки',
      'Zigbee реле',
      'Zigbee датчик влажности',
      'совместимость Zigbee2MQTT',
      'умная теплица Zigbee',
      'Zigbee Hub',
    ],
    featuredArticles: featured.zigbee,
  },
  {
    id: 'mini-ferma-i-neskolko-boksov',
    slug: 'mini-ferma-i-neskolko-boksov',
    title: 'Несколько боксов и мини-ферма',
    description: 'Раздел для владельцев нескольких зон выращивания: мониторинг, аварии, учет ресурсов, роли пользователей и обслуживание.',
    fit: 'У вас несколько зон выращивания, и состояние датчиков, оборудования и поливов приходится собирать из разных интерфейсов.',
    tasks: 'Структура зон, единый обзор, история, обслуживание оборудования и самостоятельное подключение GrowerHub.',
    keywords: [
      'автоматизация теплицы',
      'мониторинг теплицы',
      'умная теплица для бизнеса',
      'мини ферма автоматизация',
      'контроль микроклимата теплицы',
      'удаленный контроль теплицы',
      'учет воды в теплице',
      'мониторинг нескольких теплиц',
      'уведомления в теплице',
      'система управления теплицей',
    ],
    featuredArticles: featured.farm,
  },
  {
    id: 'home-assistant-i-diy',
    slug: 'home-assistant-i-diy',
    title: 'Home Assistant и DIY-интеграции',
    description: 'Технический раздел про Home Assistant, MQTT, ESP32, Zigbee2MQTT, локальные сценарии и границы DIY-автоматизации.',
    fit: 'Вы используете Home Assistant, ESP32, MQTT или Zigbee2MQTT и хотите связать DIY-часть с понятной моделью зон.',
    tasks: 'Калибровка датчиков, MQTT-топики и availability, границы ответственности систем, безопасные условия автоматизации.',
    keywords: [
      'Home Assistant автополив',
      'MQTT автополив',
      'ESP32 автополив Home Assistant',
      'MQTT discovery Home Assistant',
      'Home Assistant растения',
      'Zigbee2MQTT теплица',
      'dashboard растений Home Assistant',
      'Node-RED полив',
      'локальная автоматизация растений',
      'GrowerHub Home Assistant',
    ],
    featuredArticles: featured.diy,
  },
];

const en = [
  {
    id: 'avtopoliv-i-kontroller-vyrashchivaniya',
    slug: 'automated-watering-and-grow-controllers',
    title: 'Automated watering and grow controllers',
    description: 'Practical guides to irrigation, sensors, relays, lighting, and safe automation for homes, grow boxes, and greenhouses.',
    fit: 'You manage one or more watering zones and timers or manual checks are no longer enough.',
    tasks: 'Choosing sensors and actuators, measuring water flow, setting per-run limits, pauses, and emergency stops.',
    keywords: [
      'automatic plant watering',
      'automatic watering for houseplants',
      'greenhouse irrigation automation',
      'irrigation controller',
      'soil moisture sensor for irrigation',
      'smart watering',
      'watering automation',
      'soil moisture based watering',
      'safe automated watering',
      'grow box automation',
    ],
    featuredArticles: featured.watering,
  },
  {
    id: 'zhurnal-i-sovetnik-uhoda',
    slug: 'plant-journal-and-care-guidance',
    title: 'Plant journal and care guidance',
    description: 'Plant journals, watering records, observations, charts, and careful data-informed approaches to plant care.',
    fit: 'You want to compare plant condition with watering, sensor readings, and care changes instead of relying on memory.',
    tasks: 'Structuring a journal, recording watering and maintenance, reading charts, and comparing growing cycles.',
    keywords: [
      'plant journal',
      'plant watering log',
      'plant watering schedule',
      'sensor based plant care',
      'plant watering guidance',
      'water pH for plants',
      'plant feeding log',
      'plant photo journal',
      'microclimate history',
      'plant care recommendations',
    ],
    featuredArticles: featured.journal,
  },
  {
    id: 'zigbee-hub-i-ustroystva',
    slug: 'zigbee-hubs-and-devices',
    title: 'Zigbee2MQTT coordinators, sensors, and devices',
    description: 'A step-by-step path from a USB coordinator and Zigbee2MQTT installation to pairing, compatibility checks, mesh coverage, and GrowerHub connection.',
    fit: 'You already use Zigbee or Zigbee2MQTT, or you are choosing wireless sensors for a greenhouse, grow box, or rack.',
    tasks: 'Compatibility checks, pairing, mesh coverage, availability, and choosing sensors, plugs, and relays for real loads.',
    keywords: [
      'Zigbee temperature humidity sensor',
      'Zigbee greenhouse',
      'Zigbee2MQTT',
      'Zigbee smart plug for grow lights',
      'Zigbee leak sensor',
      'Zigbee relay',
      'Zigbee humidity sensor',
      'Zigbee2MQTT compatibility',
      'Zigbee smart greenhouse',
      'Zigbee hub',
    ],
    featuredArticles: featured.zigbee,
  },
  {
    id: 'mini-ferma-i-neskolko-boksov',
    slug: 'small-farms-and-multiple-grow-boxes',
    title: 'Small farms and multiple grow boxes',
    description: 'Monitoring, alerts, resource tracking, user access, and maintenance for several growing zones.',
    fit: 'You operate several growing zones and currently collect sensor, equipment, and watering data from different interfaces.',
    tasks: 'Zone structure, a shared overview, history, equipment maintenance, and self-service GrowerHub setup.',
    keywords: [
      'greenhouse automation',
      'greenhouse monitoring',
      'smart greenhouse software',
      'small farm automation',
      'greenhouse climate monitoring',
      'remote greenhouse monitoring',
      'greenhouse water tracking',
      'multiple greenhouse monitoring',
      'greenhouse alerts',
      'greenhouse management system',
    ],
    featuredArticles: featured.farm,
  },
  {
    id: 'home-assistant-i-diy',
    slug: 'home-assistant-and-diy',
    title: 'Home Assistant and DIY integrations',
    description: 'Technical guides to Home Assistant, MQTT, ESP32, Zigbee2MQTT, local automations, and sensible DIY boundaries.',
    fit: 'You use Home Assistant, ESP32, MQTT, or Zigbee2MQTT and want to connect DIY hardware to a clear zone model.',
    tasks: 'Sensor calibration, MQTT topics and availability, system boundaries, and safe automation conditions.',
    keywords: [
      'Home Assistant plant watering',
      'MQTT irrigation',
      'ESP32 irrigation Home Assistant',
      'MQTT discovery Home Assistant',
      'Home Assistant plants',
      'Zigbee2MQTT greenhouse',
      'Home Assistant plant dashboard',
      'Node-RED irrigation',
      'local plant automation',
      'GrowerHub Home Assistant',
    ],
    featuredArticles: featured.diy,
  },
];

const clusterGuides = {
  ru: {
    'avtopoliv-i-kontroller-vyrashchivaniya': {
      intro: 'Сначала определите, что именно должно измениться после автоматизации: только расписание, контроль влажности или безопасное управление водой. Затем добавляйте обратную связь и ограничения, а не усложняйте всю систему сразу.',
      steps: [
        {
          articleId: 'avtopoliv-dlya-rasteniy-kak-vybrat',
          title: '1. Выберите уровень автоматизации',
          text: 'Сравните таймер, полив по датчику и полноценный контроллер по масштабу зоны и цене ошибки.',
        },
        {
          articleId: 'datchik-vlazhnosti-pochvy-dlya-avtopoliva',
          title: '2. Проверьте измерение',
          text: 'Откалибруйте датчик в реальном субстрате и убедитесь, что один процент действительно описывает состояние корней.',
        },
        {
          articleId: 'bezopasnyy-avtopoliv-limity-i-avariynyy-stop',
          title: '3. Добавьте защиту',
          text: 'Ограничьте один запуск, паузу и суточный расход, предусмотрите протечку и ручную блокировку.',
        },
      ],
      decisions: [
        { situation: 'Нужен только стабильный режим по времени', start: 'Расписание и ручной контроль', reason: 'Проще проверить расход и не зависеть от неточного датчика почвы.' },
        { situation: 'Субстрат высыхает неравномерно', start: 'Датчик и журнал нескольких ручных поливов', reason: 'Порог выбирают по собственной динамике, а не по универсальному проценту.' },
        { situation: 'Вода работает без постоянного наблюдения', start: 'Лимиты, протечка и безопасное выключение', reason: 'Ни один датчик не заменяет независимую остановку подачи воды.' },
      ],
      cta: {
        title: 'Соберите первую зону полива в GrowerHub',
        text: 'Начните с мониторинга и ручного управления, посмотрите историю, а автоматический сценарий включайте после проверки оборудования и лимитов.',
      },
    },
    'zhurnal-i-sovetnik-uhoda': {
      intro: 'Полезный журнал отвечает не на вопрос «что происходило сегодня», а связывает действие, условия и результат. Для начала достаточно регулярно фиксировать полив, состояние растения и несколько одинаково измеряемых показателей.',
      steps: [
        {
          articleId: 'dnevnik-rasteniya-chto-zapisyvat',
          title: '1. Зафиксируйте базовые события',
          text: 'Записывайте полив, обслуживание, изменение режима и наблюдаемую реакцию растения.',
        },
        {
          articleId: 'zhurnal-poliva-obem-ph-udobreniya',
          title: '2. Сделайте полив сравнимым',
          text: 'Храните дату, объём, pH и состав только там, где эти значения действительно измеряются.',
        },
        {
          articleId: 'istoriya-mikroklimata-i-grafiki',
          title: '3. Сопоставьте события с графиком',
          text: 'Ищите устойчивую динамику до и после действия, а не объясняйте растение одной случайной точкой.',
        },
      ],
      decisions: [
        { situation: 'Забывается дата или объём полива', start: 'Короткая запись после каждого действия', reason: 'Последовательность событий важнее длинного свободного текста.' },
        { situation: 'Нужно сравнить два цикла выращивания', start: 'Одинаковые поля и контрольные точки', reason: 'Сравнение работает только при одинаковом способе измерения.' },
        { situation: 'На графике появилось отклонение', start: 'Проверка свежести датчика и журнала изменений', reason: 'Сначала исключают ошибку измерения, затем ищут причину в уходе.' },
      ],
      cta: {
        title: 'Соберите историю зоны без ручных таблиц',
        text: 'GrowerHub хранит показания устройств и события зоны рядом. Подключите датчик, а заметки о растениях добавляйте только когда они помогают принять решение.',
      },
    },
    'zigbee-hub-i-ustroystva': {
      intro: 'Для первого Zigbee-контура нужны компьютер или Raspberry Pi, координатор с подходящей прошивкой и одно устройство. Бренд не определяет совместимость: важны точная модель, поддержка Zigbee2MQTT и доступные свойства устройства.',
      steps: [
        {
          articleId: 'zigbee2mqtt-prostymi-slovami',
          title: '1. Разберитесь в схеме',
          text: 'Поймите роли координатора, роутеров, конечных устройств, MQTT-топиков и availability.',
        },
        {
          articleId: 'ustanovka-zigbee2mqtt-na-windows',
          title: '2. Запустите координатор',
          text: 'Для первого стенда подойдёт постоянно включённый Windows-компьютер и USB-стик с coordinator firmware.',
        },
        {
          articleId: 'podklyuchit-zigbee-datchik-temperatury-vlazhnosti',
          title: '3. Добавьте один датчик',
          text: 'Проведите pairing рядом с координатором, проверьте exposes, обновления и доступность до установки на место.',
        },
      ],
      decisions: [
        { situation: 'Сеть создаётся с нуля', start: 'USB-координатор Z-Stack или Ember и один датчик', reason: 'Так проще отделить настройку Zigbee2MQTT от проблем покрытия большой сети.' },
        { situation: 'Устройство работало и пропало', start: 'Питание, last_seen, availability и логи', reason: 'Повторное удаление и pairing могут скрыть исходную причину и создать новый объект.' },
        { situation: 'Выбираете датчик или розетку', start: 'Точный model ID в каталоге Zigbee2MQTT', reason: 'Одинаковый корпус не гарантирует одинаковые exposes и команды.' },
      ],
      cta: {
        title: 'Подключите первое Zigbee-устройство',
        text: 'Создайте подключение GrowerHub, используйте выданную MQTT-конфигурацию и увидьте обнаруженные метрики в одном кабинете.',
      },
    },
    'mini-ferma-i-neskolko-boksov': {
      intro: 'Несколько зон не требуют начинать с большой автоматизации. Сначала соберите единый обзор состояния, одинаково назовите оборудование и настройте предупреждения; исполнительные сценарии добавляйте по одному.',
      steps: [
        {
          articleId: 'avtomatizatsiya-teplitsy-chto-kontrolirovat',
          title: '1. Расставьте приоритеты',
          text: 'Начните с микроклимата, свежести данных и аварий, которые человек должен увидеть сразу.',
        },
        {
          articleId: 'mini-ferma-iz-dvuh-grouboksov-dashboard',
          title: '2. Соберите обзор по зонам',
          text: 'Показывайте состояние каждой зоны одинаковым набором показателей и отдельно — общие проблемы.',
        },
        {
          articleId: 'monitoring-neskolkih-boksov',
          title: '3. Настройте обслуживание',
          text: 'Контролируйте время обновления, пропуски данных и оборудование, требующее внимания.',
        },
      ],
      decisions: [
        { situation: 'Данные разбросаны по нескольким приложениям', start: 'Единый обзор зон и понятные имена', reason: 'Сначала оператор должен быстро понимать состояние фермы.' },
        { situation: 'Зоны работают по разным режимам', start: 'Раздельные ресурсы и сценарии каждой зоны', reason: 'Общее правило для разного оборудования усложняет диагностику.' },
        { situation: 'Нужно удалённое наблюдение', start: 'Свежесть данных и уведомления об отказах', reason: 'Красивый график бесполезен, если последнее значение устарело.' },
      ],
      cta: {
        title: 'Соберите ферму из понятных зон',
        text: 'Подключите координатор, распределите найденные устройства по помещениям или теплицам и получите общий обзор без обязательного описания культур.',
      },
    },
    'home-assistant-i-diy': {
      intro: 'Home Assistant, MQTT и ESP32 дают свободу интеграции. Чтобы система оставалась понятной, заранее разделите транспорт данных, отображение, правила управления и физическую защиту оборудования.',
      steps: [
        {
          articleId: 'home-assistant-dlya-rasteniy',
          title: '1. Опишите безопасную логику',
          text: 'Отделите триггер от условий и ограничьте действие до подключения автоматического полива.',
        },
        {
          articleId: 'mqtt-discovery-home-assistant',
          title: '2. Приведите MQTT к понятной модели',
          text: 'Используйте стабильные идентификаторы, availability и предсказуемые имена сущностей.',
        },
        {
          articleId: 'growerhub-i-home-assistant-cherez-mqtt',
          title: '3. Определите границы систем',
          text: 'Оставьте локальные сценарии на месте и передавайте в GrowerHub только нужные состояния и команды.',
        },
      ],
      decisions: [
        { situation: 'Home Assistant уже управляет оборудованием', start: 'Инвентаризация сущностей и конфликтующих правил', reason: 'Один исполнитель не должен одновременно управляться двумя независимыми сценариями.' },
        { situation: 'Есть самодельный ESP32-датчик', start: 'Калибровка, availability и стабильный MQTT topic', reason: 'Discovery упрощает интерфейс, но не исправляет шумные измерения.' },
        { situation: 'Нужен GrowerHub без замены локального MQTT', start: 'Направленный connector', reason: 'Локальный Home Assistant продолжает работать, а платформа получает нужные данные.' },
      ],
      cta: {
        title: 'Добавьте GrowerHub к существующей системе',
        text: 'Сохраните локальный Zigbee2MQTT и Home Assistant, подключите нужные устройства и соберите зоны в GrowerHub без переноса растений и сценариев за один раз.',
      },
    },
  },
  en: {
    'avtopoliv-i-kontroller-vyrashchivaniya': {
      intro: 'Start by defining what automation should change: a repeatable schedule, soil-moisture feedback, or controlled water delivery. Add feedback and safeguards one layer at a time instead of automating the whole system at once.',
      steps: [
        { articleId: 'avtopoliv-dlya-rasteniy-kak-vybrat', title: '1. Choose the level of automation', text: 'Compare a timer, sensor-based watering, and a controller by zone size and the cost of a wrong decision.' },
        { articleId: 'datchik-vlazhnosti-pochvy-dlya-avtopoliva', title: '2. Verify the measurement', text: 'Calibrate the sensor in the actual substrate and confirm that its value represents the root zone.' },
        { articleId: 'bezopasnyy-avtopoliv-limity-i-avariynyy-stop', title: '3. Add safeguards', text: 'Limit each run, recovery time, and daily delivery; provide leak detection and a manual lockout.' },
      ],
      decisions: [
        { situation: 'You only need a repeatable time-based routine', start: 'Schedule plus manual verification', reason: 'It is easy to measure delivery without relying on an uncalibrated soil sensor.' },
        { situation: 'The substrate dries unevenly', start: 'A sensor plus a log of manual watering cycles', reason: 'A useful threshold comes from your own trend, not a universal percentage.' },
        { situation: 'Water may run unattended', start: 'Runtime limits, leak detection, and safe shutdown', reason: 'No software reading replaces an independent way to stop water.' },
      ],
      cta: { title: 'Build your first GrowerHub watering zone', text: 'Begin with monitoring and manual control, review the history, and enable automation only after the equipment and limits have been tested.' },
    },
    'zhurnal-i-sovetnik-uhoda': {
      intro: 'A useful plant log connects an action, the surrounding conditions, and the result. Start with consistent records of watering, plant condition, and a small set of measurements collected in the same way.',
      steps: [
        { articleId: 'dnevnik-rasteniya-chto-zapisyvat', title: '1. Record the basic events', text: 'Log watering, maintenance, setpoint changes, and the plant response you can actually observe.' },
        { articleId: 'zhurnal-poliva-obem-ph-udobreniya', title: '2. Make watering comparable', text: 'Store date, volume, pH, and nutrients only when those values are genuinely measured.' },
        { articleId: 'istoriya-mikroklimata-i-grafiki', title: '3. Compare events with trends', text: 'Look for repeatable changes before and after an action instead of explaining a plant from one data point.' },
      ],
      decisions: [
        { situation: 'Watering dates or volumes are easy to forget', start: 'A short entry after every action', reason: 'A consistent event sequence is more useful than occasional long notes.' },
        { situation: 'You want to compare two grow cycles', start: 'The same fields and checkpoints in both cycles', reason: 'A comparison only works when measurements are collected consistently.' },
        { situation: 'A chart suddenly changes', start: 'Sensor freshness and the change log', reason: 'Rule out a measurement fault before attributing the change to plant care.' },
      ],
      cta: { title: 'Keep zone history without separate spreadsheets', text: 'GrowerHub keeps device readings and zone events together. Connect a sensor first, then add plant notes only when they support a decision.' },
    },
    'zigbee-hub-i-ustroystva': {
      intro: 'A first Zigbee setup needs an always-on computer or Raspberry Pi, a coordinator with suitable firmware, and one device. Brand alone does not establish compatibility: check the exact model, Zigbee2MQTT support, and exposed capabilities.',
      steps: [
        { articleId: 'zigbee2mqtt-prostymi-slovami', title: '1. Understand the data path', text: 'Learn what the coordinator, routers, end devices, MQTT topics, and availability each do.' },
        { articleId: 'ustanovka-zigbee2mqtt-na-windows', title: '2. Start the coordinator', text: 'An always-on Windows computer and a USB stick with coordinator firmware are enough for a first setup.' },
        { articleId: 'podklyuchit-zigbee-datchik-temperatury-vlazhnosti', title: '3. Pair one sensor', text: 'Pair near the coordinator and verify exposes, updates, and availability before moving the sensor.' },
      ],
      decisions: [
        { situation: 'You are creating a network from scratch', start: 'A Z-Stack or Ember USB coordinator and one sensor', reason: 'This separates basic Zigbee2MQTT setup from later mesh coverage issues.' },
        { situation: 'A previously working device disappeared', start: 'Power, last_seen, availability, and logs', reason: 'Removing and re-pairing too early can hide the original fault and create a new entity.' },
        { situation: 'You are choosing a sensor or smart plug', start: 'The exact model ID in the Zigbee2MQTT catalog', reason: 'Identical enclosures may expose different measurements and commands.' },
      ],
      cta: { title: 'Connect your first Zigbee device', text: 'Create a GrowerHub connection, use the provided MQTT configuration, and view discovered device metrics in one dashboard.' },
    },
    'mini-ferma-i-neskolko-boksov': {
      intro: 'Multiple zones do not require a large automation project on day one. Build a shared overview, use consistent equipment names, and surface faults first; add control scenarios one at a time.',
      steps: [
        { articleId: 'avtomatizatsiya-teplitsy-chto-kontrolirovat', title: '1. Set priorities', text: 'Start with climate, data freshness, and faults that an operator must notice immediately.' },
        { articleId: 'mini-ferma-iz-dvuh-grouboksov-dashboard', title: '2. Build a zone overview', text: 'Show each zone with the same core measurements and keep site-wide problems separate.' },
        { articleId: 'monitoring-neskolkih-boksov', title: '3. Plan maintenance', text: 'Track update times, missing data, and equipment that needs attention.' },
      ],
      decisions: [
        { situation: 'Data is spread across several applications', start: 'One zone overview and consistent names', reason: 'The operator must first be able to understand the farm at a glance.' },
        { situation: 'Zones use different operating modes', start: 'Separate resources and scenarios per zone', reason: 'One shared rule for different equipment makes faults harder to diagnose.' },
        { situation: 'You need remote monitoring', start: 'Data freshness and failure notifications', reason: 'A polished chart is misleading when its latest reading is stale.' },
      ],
      cta: { title: 'Organize the farm into clear zones', text: 'Connect a coordinator, assign discovered devices to rooms or greenhouses, and get a shared overview without describing crops first.' },
    },
    'home-assistant-i-diy': {
      intro: 'Home Assistant, MQTT, and ESP32 provide flexible integrations. Keep the system understandable by separating data transport, dashboards, control rules, and physical equipment protection.',
      steps: [
        { articleId: 'home-assistant-dlya-rasteniy', title: '1. Define safe control logic', text: 'Separate triggers from conditions and bound every action before enabling automatic watering.' },
        { articleId: 'mqtt-discovery-home-assistant', title: '2. Use a predictable MQTT model', text: 'Keep identifiers stable and configure availability and meaningful entity names.' },
        { articleId: 'growerhub-i-home-assistant-cherez-mqtt', title: '3. Define system boundaries', text: 'Keep local automations in place and forward only the required states and commands to GrowerHub.' },
      ],
      decisions: [
        { situation: 'Home Assistant already controls equipment', start: 'Inventory entities and overlapping rules', reason: 'One actuator should not be controlled by two independent automations.' },
        { situation: 'You have a DIY ESP32 sensor', start: 'Calibration, availability, and a stable MQTT topic', reason: 'Discovery improves setup but cannot correct a noisy measurement.' },
        { situation: 'You want GrowerHub without replacing local MQTT', start: 'The directed connector', reason: 'Local Home Assistant keeps working while the platform receives only required data.' },
      ],
      cta: { title: 'Add GrowerHub to your existing system', text: 'Keep local Zigbee2MQTT and Home Assistant, connect the devices you need, and organize zones without migrating every plant or scenario at once.' },
    },
  },
};

const attachGuides = (locale, clusters) => clusters.map((cluster) => ({
  ...cluster,
  guide: clusterGuides[locale][cluster.id],
}));

export const articleClustersByLocale = Object.freeze({
  ru: attachGuides('ru', ru),
  en: attachGuides('en', en),
});
export const articleClusters = articleClustersByLocale[getInitialLocale()] || ru;

export const getArticleClusters = (locale = 'ru') => (
  articleClustersByLocale[normalizeLocale(locale)]
);

export const getArticleClusterBySlug = (slug, locale = getInitialLocale()) => (
  getArticleClusters(locale).find((cluster) => cluster.slug === slug)
);

export const getArticleClusterById = (id, locale = getInitialLocale()) => (
  getArticleClusters(locale).find((cluster) => cluster.id === id)
);
