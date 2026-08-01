# Changelog

## 2026-08-01
- Devices: dobavlena privyazka seriynoj Grovika po pechatnomu device_id.
- Devices: forma i native-kartochki podklyucheny k fakticheskomu marshrutu nastroek vmeste s Zigbee-kartochkami.
- Devices: dobavleny soobshcheniya 404/409/429, obratnyj otschet Retry-After i obnovlenie spiska posle uspeha.
- Tests: pokryty uspeshnyj claim, zanyatoe/neizvestnoe ustrojstvo i vremennaya blokirovka.

## 2026-04-05
- Watering UI: plashka tekuschego poliva teper' poyavlyaetsya bez reload posle starta iz sidebar.
- Watering UI: timer peresinhroniziruetsya s serverom na focus/visibilitychange i pri dostizhenii nulya.
- Watering UI: dobavlen redkij refetch tolko vo vremya aktivnogo poliva, chtoby plashka ne zastrjevala bez push-obnovlenij.
- Tests: dobavlen vitest + jsdom i scenarii na refetch, safe fallback, focus/visibility i ruchnoj stop.

## 2026-01-14
- Dashboard: dobavlen znachok "pora polit" s tooltip.
- Watering: defolty berutsya iz poslednego poliva, pokazany rekomendacii LLM.
