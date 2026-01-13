# Backend: границы и зависимости (целевая)

Примечание: документ конкретизирует RULES для backend; при вопросах импортов он приоритетнее.

Матрица зависимостей (строка → может импортировать столбец):

- api-адаптер → `ru.growerhub.backend.<domain>.*Facade` (corresponding domain), `ru.growerhub.backend.<domain>.contract..`, `ru.growerhub.backend.common.*`, `framework`.
- mqtt-адаптер → `ru.growerhub.backend.<domain>.*Facade`, `ru.growerhub.backend.<domain>.contract..`, `ru.growerhub.backend.common.*`, `framework`.
- домен → свои `contract`, `engine`, `jpa` подпакеты и другие домены только через их публичные фасады/контракты; `ru.growerhub.backend.common.*` для утилит.
- `ru.growerhub.backend.common.*` → стандартные библиотеки и утилиты (без JPA и бизнес-логики).

Запреты:

- Внешним модулям запрещён импорт любых `ru.growerhub.backend.<domain>.engine..` и `ru.growerhub.backend.<domain>.jpa..`; доступ возможен только к `ru.growerhub.backend.<domain>.*Facade` и `ru.growerhub.backend.<domain>.contract..`.
- Адаптеры не импортируют JPA (`@Entity`, `Repository`) и не обращаются к `jpa` пакетам доменов.
- Домены не обращаются к `engine`/`jpa` чужих доменов напрямую.
- Бизнес-логика не размещается в адаптерах; исключения допускаются только при явно зафиксированных архитектурных решениях (ADR-001).
