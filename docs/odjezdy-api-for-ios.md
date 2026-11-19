# Odjezdy Online – veřejné API pro mobilní appku (jen čtení)

Základ: `https://app.odjezdy.online`. Bez klíče. Souběžně platí limit **150 požadavků za minutu na IP** pro všechno pod `/api/` (hlavičky `X-RateLimit-Limit/Remaining/Reset`); `/api/proxy/*` má přísnější limit 30/min a appka ho nemá volat. Strojově čitelný přehled pro AI: `/llms.txt`, `/llms-full.txt`. Stav k 19. 11. 2026 – pole mohou přibýt, nic nemazat bez upozornění se nedá zaručit.

## Obecné poznámky k datům
- **Čas**: `/api/v2/*` a `/api/board` vrací ISO 8601 v **UTC** (`...Z`). Starší části (`updatedAt` u vozidel, časy v `stops[].time`/`timetable[].time`) mají buď `...Z`, nebo `+01:00`/`+02:00`, nebo jen `HH:MM[:SS]` v místním čase. Parsujte tolerantně a zobrazujte v Europe/Prague.
- **id**: zastávky jsou vždy string (`CIS-<číslo>`, uzel + čtyřmístné číslo stanoviště, např. `CIS-378170001` je stanoviště 1 uzlu `CIS-37817`); id vozidel je string (`PID-...`, `DUK-9106`, `IDOL-unk-SPZ-5L86347`). Číselné id nepředpokládat.
- **provider** (zdroj záznamu): `PID`, `DUK`, `IDSJMK`, `ODIS`, `IDOL`, `ZLIN`, `JIKORD`, `IDPK`, `IDSOK`, `ARRIVA`, `DPMD`, `CZPTT` (vlaky z feedu Správy železnic), `ČD` (odjezdy vlaků), dále zahraniční (`GZM`, `WROCLAW`, `VDV`...). Stejné vozidlo může být v několika systémech; server duplicity slučuje a v záznamu je pak `mergedFrom: ["DUK"]`.
- **delay**: `delayMinutes`/`delay.minutes` jsou celé minuty po jízdním řádu; `null` = neznámé (ne „včas“).
- Chybějící údaj je `null` nebo pole chybí.

## Endpointy

### GET /api/vehicles?fields=map
Všechna sledovaná vozidla, **bez filtru na viewport** (celá ČR a okolí, ~4–5 tisíc záznamů). `fields=map` vrací zeštíhlený tvar. Odpověď: `{ count, data: [ { id, provider, type, line, destination, location:{lat,lng,bearing}, updatedAt, currentStop, nextStop, delay:{minutes,offline,source}, attributes:{spz,evc,wheelchair,wifi,ac,tripId,gtfsTripId,saUrl,trainNumber...}, motion:{vx,vy}, mergedFrom? } ] }`.
- `type`: `bus`, `tram`, `trolleybus`, `subway`, `train`, `ferry`. Linka je string (`"N/A"` nebo prázdné = vozidlo mimo provoz / v depu).
- `motion` = rychlost ve stupních za sekundu (`vx` pro lng, `vy` pro lat), změřená serverem z posledních dvou poloh; slouží k plynulému dopočítání pohybu mezi aktualizacemi. Chybí-li, vozidlo stojí nebo se neví.
- Velikost (změřeno 19. 11. 2026): **~4,2 MB nekomprimovaně, ~0,68 MB s gzip** (asi 4 400 vozidel). Na mobilních datech to je znatelné, proto gzip a polling jen při viditelné mapě.

### GET /api/stations/bounds?minLat=&maxLat=&minLng=&maxLng=
Zastávky v obdélníku: pole `[{ id, name, lat, lng, provider }]`. Parametry jsou `minLat/maxLat/minLng/maxLng` (ne south/north). Volat až od přiblížení (web od zoomu 13). Chybné parametry = 400 `{error}`.

### GET /api/stations/search?q=
Až 30 zastávek `[{ id, name, lat, lng, provider }]`, bez ohledu na diakritiku. **GET /api/search?q=** vrací `{ stations: [...], lines: [...], vehicles: [...] }` (hledá i vozidla podle ev. č./SPZ a linky).

### GET /api/board/{stopId}
`{ stopId, stopName, data: [odjezd] }`; odjezd: `id, stopId, provider, type, line, trainNumber?, destination, destinationConfident, terminatesHere, origin, platform, plannedTime (UTC), expectedTime?, delayMinutes, direction, viaStops[], cancelled, lowFloor, operator`. Celá zastávka (uzel) se čte i s jejími stanovišti. **GET /api/v2/departures?stopId=&limit=&minutes=** je stejný obsah v čistém tvaru `{ apiVersion:2, generatedAt, stopId, stopName, count, data }` – pro novou appku preferovat v2 (stabilnější, chybová těla `{ apiVersion, error:{code,message} }`, limit 1–500, `minutes` 1–1440, `stopIds=a,b,c` pro víc zastávek najednou).

### GET /api/v2/stops/search?q=
`{ apiVersion:2, count, data:[{id,name,lat,lng,provider}] }`. **GET /api/v2/eurokey/near?lat=&lng=** = toalety s Euroklíčem do 300 m: `{ radiusMeters:300, source, count, data:[...] }`.

### GET /api/vehicle-route/{vehicleId}
Trasa vybraného vozidla. `{ available:false }` když není. Jinak `available, directed, traveled:[[lat,lng]...], remaining:[[lat,lng]...], stops:[{id,name,lat,lng,time}]|null, timetable:[{name,post,time}], plan:[{time,linkoSpoj,destination}], destination`. `traveled`/`remaining` jsou polyline (u vlaků vedená po kolejích). `directed:false` = trasa se kreslí jedním neutrálním tahem v `remaining`. `stops` mají souřadnice; `timetable` ne (jen jména a časy, např. z CIS JŘ).

### GET /api/vehicle-info/{vehicleId}
Vizitka vozidla: `{ available, pending?, info:{ manufacturer, model, year, status, carrier, depot, fleetNo, plate, equipment, note?, dispatch?[{day,line,kurz,region,note}] } }`. `pending:true` = údaje se právě dohledávají (zkusit za ~15 s).
### GET /api/vehicle-circulation/{vehicleId}?date=YYYY-MM-DD
Oběhy: `{ runs:[{from,to,line,destination}], days[], plan?, dispatch? }`.

### GET /api/alerts
Pole mimořádností: `id, provider (DUK|PID|SZ|CD), title, description, summary, validFrom, validTo, lines[], stops[], lat, lng, geometry? (GeoJSON Point|LineString), category, stopsAffected`. Časy mají různé formáty (ISO s offsetem). Stovky záznamů; filtrovat na straně appky podle viewportu.

### GET /api/delays/average?provider=&line=&destination=&tripId=
Průměrné zpoždění konkrétního spoje za 14 dní: `{ avg_delay|null, trip_count, window_days, scope:"trip" }`. `tripId` je povinný.

### GET /api/train-composition/{trainNumber}
Řazení vlaku: `{ available:false }`, když není k dispozici.

## Doporučení pro appku
- **Polling /api/vehicles**: ano, každých ~10–20 s, jen když je mapa vidět (zdrojová data se obnovují po 10–30 s, častěji nemá smysl). Odpověď je celorepubliková, takže se **filtruje na straně appky podle viewportu** (server bbox filtr zatím neumí). Jedno volání = 1 z 150 za minutu, zbytek stačí na hledání, kartu zastávky (`/api/v2/departures`, nejvýš jednou za ~20 s) a trasu vybraného vozidla.
- Posílejte `Accept-Encoding: gzip` (URLSession to dělá samo) a vždy `fields=map`.
- Při HTTP 429 čekat do `X-RateLimit-Reset` (epoch sekundy).
- Pro widgety: `/api/v2/departures?stopId=…&limit=6`, obnova ne častěji než jednou za pár minut (omezení WidgetKit).

## Nová verze webu – design
Nový vzhled je `views/v2.ejs` + `public/v2/v2.css` + `public/v2/v2.js` (na webu `/?ui=new`, přepínač „Starý vzhled“). Tokeny jsou CSS proměnné v `public/v2/v2.css` (`:root` = světlé, `[data-theme="dark"]` = tmavé). Světlé: pozadí `#e9ebee`, plocha `#ffffff`/`#f1f2f4`, text `#16181d`, tlumený `#5c6470`, linka/okraj `#dfe2e7`, akcent `#1b4fa8`, chyba `#c4161c`, dobré `#0b7a4b`, cedule `#c8102e`. Tmavé: pozadí `#101216`, plocha `#17191d`/`#21252b`, text `#f1f2f4`, tlumený `#9aa3b0`, okraj `#2e333b`, akcent `#7aa7ff`. Barvy typů: autobus `#1d5fd1`, vlak `#0b7a4b`, tramvaj `#c4161c`, noční `#1b1f27` (tmavý `#3a4350`). Písmo: Barlow (text), Barlow Condensed (čísla linek). Vzhled se od mé verze mohl změnit; zdrojem pravdy je soubor.
