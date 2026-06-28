# Frontend

Frontend - React-приложение. Оно отображает состояние из backend и не является источником правды.

## Структура

- `src/main.jsx` - точка входа.
- `src/App.jsx` - маршрутизация.
- `src/api` - HTTP client и API-модули.
- `src/components` - переиспользуемые UI-компоненты.
- `src/content` - статический контент страниц и статей.
- `src/domain` - локальные frontend-типы и утилиты, не равные backend-доменам.
- `src/features` - прикладные состояния, hooks и contexts.
- `src/pages` - публичные страницы.
- `src/pages/app` - страницы приложения.
- `src/utils` - общие утилиты.

## Состояние

`AuthContext` хранит состояние авторизации и токен. `SensorStatsContext` и `WateringSidebarContext` управляют боковыми панелями. Данные dashboard, sensors и watering загружаются через hooks и API-модули.

## Границы

Frontend обращается к backend только через REST API. Прямой доступ к БД, MQTT broker и внутренним backend-пакетам запрещен.
