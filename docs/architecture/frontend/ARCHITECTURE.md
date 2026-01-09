# Frontend: фактическая архитектура

Структура каталогов:
- src/api: API клиент и модули запросов.
- src/components: UI/макет/виджеты (devices, layout, plant-avatar, plants, ui).
- src/content: контент страниц и статей, сборщики в src/content/*.js.
- src/domain: доменная область растений (types/config/utils/stages, avatars). Примечание: это локальная терминология фронта и не равна backend доменам.
- src/features: auth, sensors, watering, dashboard (contexts/hooks).
- src/pages: публичные страницы.
- src/pages/app: страницы приложения.
- src/utils: утилиты.

Точки входа и роутинг:
- main.jsx: ReactDOM + BrowserRouter + AuthProvider.
- App.jsx: Routes/Route, вложенный раздел /app.
- /app обернут RequireAuth, WateringSidebarProvider, SensorStatsProvider.
- Публичные страницы: Home, Articles list, Article, About.
- Страницы приложения: Dashboard, Devices, Plants, PlantJournal, Profile, Login.

State management:
- AuthContext: статус авторизации, login/logout, токен в localStorage, загрузка /api/auth/me.
- SensorStatsContext: состояние боковой панели статистики.
- WateringSidebarContext: состояние боковой панели полива.
- useDashboardData/useSensorStats: загрузка данных и управление состоянием.

API клиент:
- api/client.js: apiFetch с токеном из localStorage и auto-refresh при 401.
- api/*.js: запросы к /api (auth, devices, plants, plantJournal, pumps, sensors).
