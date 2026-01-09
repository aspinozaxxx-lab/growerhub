# Backend: границы и зависимости (целевая)

Примечание: документ конкретизирует RULES для backend; при вопросах импортов он приоритетнее.

Матрица зависимостей (строка -> может импортировать столбец):

- api-адаптер -> Facade доменов, публичные DTO доменов, ru.growerhub.backend.common, framework.
- mqtt-адаптер -> Facade доменов, MQTT DTO/контракты, ru.growerhub.backend.common, framework.
- домен -> свои internal/*, ru.growerhub.backend.common, другие домены только через их Facade и контрактные типы.
- orchestration (если введем) -> Facade доменов, контрактные типы, ru.growerhub.backend.common.
- ru.growerhub.backend.common -> стандартные библиотеки, утилиты.

Запреты:
- Запрещен импорт *.internal.* извне домена.
- Адаптеры не импортируют JPA Entity/Repository.
- Домены не импортируют internal других доменов.
- ru.growerhub.backend.common не зависит от доменов или адаптеров.
- Бизнес-логика не размещается в адаптерах.