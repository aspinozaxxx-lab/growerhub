# Backend: границы и зависимости (целевая)

Примечание: документ конкретизирует RULES для backend; при вопросах импортов он приоритетнее.

Матрица зависимостей (строка -> может импортировать столбец):

- api-адаптер -> `ru.growerhub.backend.<domain>.*Facade` (corresponding domain), `ru.growerhub.backend.<domain>.contract..`, `ru.growerhub.backend.common.*`, `framework`. Только `*Facade` и `contract` являются контрактом домена.
- mqtt-адаптер -> `ru.growerhub.backend.<domain>.*Facade`, `ru.growerhub.backend.<domain>.contract..`, `ru.growerhub.backend.common.*`, `framework`. Только `*Facade` и `contract` являются контрактом домена.
- домен -> свои `contract`, `engine`, `jpa` подпакеты и другие домены только через их публичные фасады/контракты; `ru.growerhub.backend.common.*` для утилит.
- `ru.growerhub.backend.common.*` -> стандартные библиотеки и утилиты (без JPA и бизнес-логики).

Запреты:

- Внешним модулям разрешён импорт только `ru.growerhub.backend.<domain>.*Facade` и `ru.growerhub.backend.<domain>.contract..`; импорт любых других классов из `ru.growerhub.backend.<domain>` запрещён.
- Корневой пакет домена не является контрактным пакетом, кроме `*Facade`.
- Контроллеры не часть домена и не размещаются в доменных пакетах.
- Адаптеры не импортируют JPA (`@Entity`, `Repository`) и не обращаются к `jpa` пакетам доменов.
- Домены не обращаются к `engine`/`jpa` чужих доменов напрямую.
- Бизнес-логика не размещается в адаптерах; исключения допускаются только при явно зафиксированных архитектурных решениях (ADR-001).