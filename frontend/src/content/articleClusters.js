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
    title: 'Zigbee Hub и совместимые устройства',
    description: 'Материалы о Zigbee, Zigbee2MQTT, ролях устройств, датчиках, розетках и безопасном применении в зоне растений.',
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
    title: 'Zigbee hubs and compatible devices',
    description: 'Guides to Zigbee, Zigbee2MQTT, device roles, sensors, smart plugs, and safe use around plants.',
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

export const articleClustersByLocale = Object.freeze({ ru, en });
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
