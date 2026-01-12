# Архитектурные документы GrowerHub

Эти документы - норматив. Код должен им соответствовать.
ARCHITECTURE и RULES - источник правды.

RULES фиксирует общесистемные законы архитектуры.
backend/BOUNDARIES фиксирует конкретную матрицу импортов Java backend.
При спорных вопросах импортов приоритет у backend/BOUNDARIES.

Как действовать при разработке:
1) Определить домен/слой.
2) Следовать RULES.
3) Если не укладывается - сначала предложить изменение архитектуры/правил (и ADR при исключении), не писать код до согласования.

Состав:
- ARCHITECTURE.md - общесистемная архитектура.
- RULES.md - обязательные правила.
- backend/ARCHITECTURE.md - архитектура backend.
- backend/BOUNDARIES.md - правила зависимостей backend.
- frontend/ARCHITECTURE.md - фактическая архитектура frontend.
- firmware/ARCHITECTURE.md - фактическая архитектура firmware (src2/test2).
- adr/README.md - когда и как оформлять ADR.