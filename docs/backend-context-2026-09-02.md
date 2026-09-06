# Euroklíč Mapa — kompletní kontext pro Claude v Android Studiu

> Tohle je **jeden soubor se vším potřebným**, aktuální k 2026-09-02.
> Nahrazuje/aktualizuje `CLAUDE_PROMPT_ANDROID.md` (sekce API a Design
> systém tam jsou částečně zastaralé — viz změny níž) a zapracovává
> opravy z `api_nove.md`. Pokud už máš k dispozici designový dokument
> „Euroklíč Mapa — designový systém" (artefakt, 18 sekcí, mockupy
> obrazovek, IA, plán) — ten zůstává platný pro **vizuální detail,
> layout obrazovek a zdůvodnění rozhodnutí**. Tenhle soubor je ale
> **autoritativní pro API a stavovou logiku markerů** — kde by se
> lišily, řiď se tímhle.

---

## Absolutní pravidla

- **Backend je živý produkční systém** (`euroklic.odjezdy.online`,
  PHP 8.2 + SQLite, reální uživatelé). Appka je čistý HTTP klient.
  Žádné SSH, žádný přímý DB přístup, žádné volání mimo endpointy
  zdokumentované níž.
- **Nevymýšlej si pole/endpointy.** Co tu není, napiš jako otevřenou
  otázku k ověření, nehádej JSON tvar ani parametry.
- **Backend se odsud needituje.** Narazíš-li na limitaci, popiš ji a
  navrhni řešení na straně appky, nebo označ jako blokující — PHP
  kód sám nepiš (backend spravuje jiná osoba/session).
- **Rozsah v1 je záměrně malý** (viz níž) — appka na čtení/procházení,
  ne plná parita s webem. Nerozšiřuj scope sám od sebe.
- **Než začneš psát kód:** zkontroluj stav projektové složky (starý
  pokus / scaffold?) — neházej nic pryč bez ptaní. Prázdná složka →
  napiš krátký plán, pak piš kód.
- Appka se **kompiluje a testuje tady** (máš JDK/Android SDK). Po
  každé větší změně `./gradlew assembleDebug` (nebo ekvivalent) a
  oprav, co nejde. Nic nenechávej neověřené.

---

## 1) Kontext projektu

**Euroklíč Mapa** — appka na hledání nejbližšího bezbariérového WC
odemykatelného tzv. Euroklíčem (univerzální zámek pro osoby se
zdravotním postižením) v ČR a na Slovensku. Nativní protějšek webu
`euroklic.odjezdy.online`. Cílovka: lidé se sníženou pohyblivostí,
senioři, rodiče s kočárky — accessibility je vstupní podmínka, ne
dodatečná kontrola.

Jedna otázka, které slouží každá obrazovka: **„Kde je nejbližší
vhodné WC?"**

**Název a positioning:** „Euroklíč Mapa" se ponechává jako hlavní
název (sedí s doménou `euroklicmapa.cz`), ale je nutné aktivně
vyvracet dojem oficiální aplikace NRZP ČR (distributora Euroklíčů):
nikdy nepoužívat jejich modrožlutou/logo, trvalý disclaimer „Nezávislý
projekt. Data agregujeme z veřejných zdrojů, nejsme oficiální aplikace
systému Euroklíč." v sekci Více / při prvním spuštění.

---

## 2) API kontrakt — kompletní a aktuální

Base URL: `https://euroklic.odjezdy.online`. Vždy HTTPS, appka nesmí
povolit cleartext.

### Volitelný geografický filtr (nové, 2026-09-02) — použij ho, ať appka nestahuje celý dataset

Na `/api_locations.php`, `/api_pickup_points.php` i `/api_app_places.php`:

```
?bbox=minLon,minLat,maxLon,maxLat
?near=lat,lon&radius_km=N          (radius_km volitelný, výchozí 50, cap 500)
```

Bez parametru = celý dataset (dnešní chování appky, co appka teď dělá
a co vedlo k tomuhle požadavku). `bbox`/`near` nejde kombinovat
najednou (400 při obojím). Na `/api_app_places.php` jde kombinovat s
`category`. Neplatný tvar → `400` s `{"error": "..."}` (přesné texty
viz `api_nove.md` bod 7). **Appka by měla tohle používat** — posílat
`bbox` podle aktuálního výřezu mapy (nebo `near`+`radius_km` podle
poslední známé polohy), místo stahování a cachování ~1442+232 bodů
při každém startu.

### Obecné chování, co appku může překvapit
- **Neexistující cesta nevrací 404** — fallback na homepage HTML s
  `200`. Nevaliduj podle status kódu, validuj podle tvaru odpovědi
  (JSON vs. HTML).
- Žádné CORS hlavičky, žádný `ETag`/`Last-Modified`, žádný rate-limit.
- Žádné stránkování/filtrování na žádném GET endpointu — vždy celý
  dataset najednou.
- Chybové JSON odpovědi (`api_*.php`) mají někdy HTTP `200` s
  `{"success": false, ...}` nebo `{"error": "..."}` v těle — **vždy
  kontroluj `success`/`error` pole, nespoléhej jen na HTTP status.**

### `GET /api_locations.php`
Hlavní data appky — GeoJSON všech schválených WC lokací (server-side
dedup podle zdroje/vzdálenosti, appka nemusí dedupovat sama).

```json
{
  "type": "FeatureCollection",
  "count": 1580,
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [14.42831, 50.07926] },
      "properties": {
        "id": 1,
        "name": "Euroklíč - Brno Přístavní ulice (ČD)",
        "source": "cd",
        "description": "Stanice Českých drah vybavená WC s eurozámkem.",
        "note": "Zdroj: cd.cz",
        "amenity": "railway_station_wc",
        "photo_url": null,
        "likes": 0,
        "dislikes": 0,
        "last_verified": null
      }
    }
  ]
}
```

- `geometry.coordinates` = **`[lon, lat]`** (GeoJSON pořadí!), WGS84.
- `source` enum: `"cd"` | `"osm"` | `"mapotic"` | `"user"`.
- `description`, `note`, `photo_url` nullable — `photo_url` je
  aktuálně u **všech** záznamů `null`, appka to musí zvládnout.
- `amenity` nullable string, vyplněný u ~99 % záznamů, ale **není to
  spolehlivý malý enum** (`toilets`, `railway_station_wc`, `unknown`,
  prázdný string, vzácně `restaurant`/`parking`...). Nepoužívej pro
  filtrování/kategorizaci UI.
- **`last_verified`** *(nové pole, přidáno 2026-09-02)* — `string|null`,
  formát `"YYYY-MM-DD HH:MM:SS"` (UTC, bez zóny v řetězci — stejně
  jako `created_at`/`updated_at`). Nastavuje se automaticky na
  aktuální čas pokaždé, když někdo dá 👍 „Funguje" přes
  `/api_vote.php` (nový hlas i změna z 👎 na 👍). **Nikdy se nemaže**
  — i pozdější 👎 ho nechá beze změny (to je jiný, aktuálnější signál,
  řešený přes `dislikes > likes`, ne přes tohle pole). Existující
  záznamy (starší než 2026-09-02) mají `last_verified = null` bez
  ohledu na staré `likes` — pole se neplní retroaktivně. Počítej s
  tím, že drtivá většina záznamů bude zpočátku `null`.
- **`web_url`** *(nové, 2026-09-02)* — `string`, kanonická adresa místa
  na webu (`https://euroklic.odjezdy.online/lokace/{id}-{slug}` —
  existující, plně funkční stránka s OG tagy). Použij pro „Nahlásit
  problém" a sdílení.
- **`opening_hours`** *(nové)* — `string|null`, **volný text** (ne OSM
  syntaxe). Vyplněné jen u `source == "cd"` (a ne ani u všech těch —
  93/109 aktuálně), jinak vždy `null`.
- **`access`** *(nové)* — `"eurokey"` | `"unknown"`. `"eurokey"` pro
  `source` `cd` a `osm` (plyne ze samotné metodiky importu — obojí
  importér bere jen eurozámkem podmíněná data), `"unknown"` pro
  `mapotic` a `user` (WCkompas je obecná mapa toalet pro pacienty s
  IBD, bez filtru na eurozámek — ověřeno; `user` formulář to pole
  nesbírá). Detailní zdůvodnění: `api_nove.md` bod 8.
- **`wheelchair`** *(nové, doplněno 2026-09-02)* — `"yes"` | `"no"` |
  `"unknown"`. Reálná data z `cd.cz` (sekce "Přístupnost stanice"),
  **jen `source == "cd"`** (109/109 stanic má vyplněno: 102× yes, 7×
  no). `osm`/`mapotic`/`user` mají vždy `"unknown"` (odkud vzít
  nemají). Bere se z přístupnosti BUDOVY (kde je WC), ne nástupiště.
- **`accessibility_note`** *(nové)* — `string|null`, syrový volný text
  všech položek přístupnosti stanice (budova + nástupiště + zrakově/
  sluchově postižení), řádek na položku. Jen `source == "cd"`, jinak
  `null`.
- **`country`** *(nové)* — ISO 3166-1 alpha-2 (`"CZ"`, `"DE"`, ...)
  nebo `null` (vzácně, pobřežní/mimo polygon). Skutečný point-in-polygon
  test proti reálným hranicím zemí (ne odhad) — spolehlivé mimo úzké
  pohraniční pásmo (výjimka: exklávy/výběžky pár km od hranice mohou
  vyjít jako sousední země, viz `api_nove.md`). Appka podle něj může
  konečně opravdu odlišit zahraniční body, ne jen je skrývat/ukazovat
  všechny.
- Chyba: `500`, `{"error": "Chyba databáze."}`.

### `GET /api_pickup_points.php`
Druhá vrstva — výdejní místa Euroklíčů (kde klíč **fyzicky
vyzvednout**, jiná kategorie než WC výš — vizuálně odlišit, nemíchat
do stejných markerů).

```json
{
  "type": "FeatureCollection",
  "count": 232,
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [14.42831, 50.07926] },
      "properties": {
        "id": 1,
        "kraj": "Hlavní město Praha",
        "district": "Praha 1",
        "org_name": "Prodejna tyflopomůcek",
        "address": "Krakovská 21, 110 00 Praha 1",
        "phone": "221 462 459, 775 438 197",
        "email": "praha@tyflopomucky.cz",
        "hours": "Otevírací doba: PO 9:00-15:00 h, ...",
        "note": null,
        "precision": "address",
        "source_url": "https://www.euroklic.cz/kraj-praha"
      }
    }
  ]
}
```
- `precision`: `"address"` (přesná) nebo `"approx"` (přibližná, 29/232
  záznamů) — odliš vizuálně (menší jistota).
- `address`, `phone`, `email`, `hours`, `note`, `district` nullable
  (`district` chybí u 84/232, `address` chybí u 64/232 — jen `org_name`
  + poloha).
- `phone` může mít víc čísel oddělených čárkou — pro `tel:` intent
  vezmi jen první.
- **Obsahové upozornění:** NRZP ČR od července 2026 dočasně pozastavila
  výdej nových klíčů kvůli chybějícímu financování. V detailu výdejního
  místa zobraz krátké upozornění + odkaz
  `https://euroklic.odjezdy.online/clanky/jak-vybavit-euroklic/`.
- Chyba: `500`, `{"error": "Chyba databáze."}`.

### `GET /api_csrf.php` + `POST /api_vote.php` — hlasování **funguje z appky bez přihlášení**

> Oprava oproti staršímu předpokladu (že hlasování vyžaduje login):
> **`api_vote.php` login nevyžaduje.** Jediná kontrola je platný CSRF
> token vázaný na anonymní PHP session (cookie `PHPSESSID`) — tu appka
> dostane zdarma, stejně jako anonymní návštěvník webu.

Postup:
1. `GET /api_csrf.php` — **s persistentním cookie jarem** (Retrofit/
   OkHttp `CookieJar`, in-memory stačí). Odpověď: `{"csrf": "<64 hex>"}`.
   Server zároveň pošle `Set-Cookie: PHPSESSID=...` (HttpOnly, Secure,
   SameSite=Lax, 30denní `Max-Age` — `SameSite=Lax` je relevantní jen
   pro prohlížeče, nativnímu HTTP klientovi nevadí).
2. Ulož si token + nech cookie jar automaticky poslat cookie u dalších
   requestů. Obnov token jen když `/api_vote.php` vrátí `403`.
3. `POST /api_vote.php`, form-encoded tělo `id=<location_id>&type=like`
   (nebo `dislike`), hlavička `X-CSRF-Token: <token z kroku 1>`, stejná
   cookie jako v kroku 1.
4. Odpověď `200`, JSON — vždy kontroluj `success`. Dedup podle IP
   adresy (ne uživatele/zařízení): jeden hlas na místo na IP, změna
   názoru (like↔dislike) přepíše předchozí, opakování stejného hlasu
   vrátí `{"success": false, "message": "Už jste takto hlasovali."}`.
   Úspěch: `{"success": true, "message": "...", "likes": N, "dislikes": N}`.

Tohle **je dostupné pro v1**, pokud appka hlasování implementuje —
není to nutné odkládat na „budoucí verzi s loginem", jak zvažoval
starší návrh. (`/api_add.php`, přidání nového místa, login **vyžaduje**
— pro to zůstává v platnosti otevřít web v Custom Tabs, viz sekce 6.)

### Auth obecně (mimo hlasování — pro budoucnost)
Web používá session cookie po Discord/Google OAuth (`/auth.php`,
`/auth_google.php`). Appka v1 se nepřihlašuje (viz Rozsah). Kdyby se
řešilo v budoucí verzi (kvůli `/api_add.php` — přidání místa, vyžaduje
`$_SESSION['user_id']`): jediná cesta je otevřít `/auth.php` v Chrome
Custom Tabs a vytáhnout `PHPSESSID` přes `CookieManager` po redirectu
zpět — appka nemá žádný token-based auth endpoint navíc.

`GET/POST /api_comments.php` — GET čtení je bez auth, dá se použít i
ve v1 pro zobrazení komentářů. POST (psaní) vyžaduje CSRF stejně jako
`/api_vote.php` (viz výš, tedy taky dostupné bez loginu, pokud se do
toho appka pustí — ale drž se rozsahu v1 v sekci 6, pokud to není
prioritou).

### Mapové dlaždice (Mapy.com)
Appka má použít **stejný zdroj jako web**:
```
https://api.mapy.com/v1/maptiles/basic/256/{z}/{x}/{y}?apikey=<MAPY_APIKEY>
```
(Klíč není v repu — injektuje se při buildu: Android z `local.properties`
`MAPY_APIKEY=…`, iOS z `ios/Secrets.xcconfig`.)
(varianty: `outdoor`, `aerial`.) Povinná attribution: „© Seznam.cz a.s.
a další", odkaz `https://api.mapy.com/copyright`, umístit vlevo dole
(nekoliduje s FAB). Doporučená knihovna: **osmdroid** s vlastním
`OnlineTileSourceBase`. Textové vyhledávání adres: web volá přímo
**Nominatim** (cizí veřejné API, žádný vlastní klíč):
`https://nominatim.openstreetmap.org/search?format=json&q={dotaz}, Česká republika`
(přidej `&countrycodes=cz,sk` — omezí výsledky na ČR/SK).

### `GET /api_app_places.php` — sjednocený endpoint pro appku (nový, 2026-09-02)

Appka pro Seznam s filtrem „Toalety / Výdejní místa" **nemusí** volat
oba GET endpointy výš zvlášť a filtrovat si sama — je pro to tenhle
jeden, navíc:

```
GET /api_app_places.php                  → obojí (toalety, pak výdejní místa)
GET /api_app_places.php?category=toilet  → jen toalety
GET /api_app_places.php?category=pickup  → jen výdejní místa
```

- Neplatná hodnota `category` → `400`, `{"error": "Neplatná hodnota category. Povoleno: toilet, pickup."}`.
- Každá `Feature.properties` má navíc **`category`**: `"toilet"` nebo
  `"pickup"` — appka podle tohodle pole sekcuje seznam i při volání
  bez parametru.
- Toaletní položky (`category:"toilet"`) mají navíc **`source_group`**
  (`"oficialni"` / `"komunitni"`, viz sekce 3 Marker systém níž) — raw
  `source` (`cd`/`osm`/`mapotic`/`user`) je pořád v odpovědi taky.
- Výdejní položky (`category:"pickup"`) mají identický tvar properties
  jako `/api_pickup_points.php`.
- Jinak stejné chování jako ostatní GET endpointy (žádné CORS/paging,
  `500` na chybu DB).

**Doporučení:** Seznam appky používej tenhle endpoint s `category`
parametrem podle aktivního filtru (segmentovaný přepínač/chip řádek
„Toalety" / „Výdejní místa" / obojí) — ne `/api_locations.php` +
`/api_pickup_points.php` zvlášť. Mapa může dál používat oba původní
endpointy nezávisle (dvě vrstvy s jinak řešeným clusteringem), to se
neruší.

### Android App Links (`.well-known/assetlinks.json`)
`https://euroklic.odjezdy.online/.well-known/assetlinks.json` má teď
(2026-09-02) placeholder záznam pro nativní appku vedle existujícího
TWA záznamu — až appka má `applicationId` + podpisový keystore, doplň
`package_name` a `sha256_cert_fingerprints` tam (ne nový soubor).
**`euroklicmapa.cz` nemá zatím žádný Caddy site block** — App Links na
tuhle doménu nejdou nastavit, dokud doména není reálně obsloužená;
používej `euroklic.odjezdy.online/lokace/{id}-{slug}` (`web_url` pole)
jako jediný funkční deep link cíl teď.

### Statické assety k reuse
| Asset | URL |
|---|---|
| App ikona (plná) | `https://euroklic.odjezdy.online/icons/icon-512.png` |
| App ikona (maskable) | `https://euroklic.odjezdy.online/icons/icon-maskable-512.png` |

---

## 3) Design systém

### Barvy — jedna modrá, slate neutrály, sémantika oddělená od akcentu
Světlý režim:

| Token | Hex |
|---|---|
| brand / primary | `#2454E0` |
| brand pressed | `#1B3FB8` |
| brand wash | `#E7EDFF` |
| background | `#F5F8FC` |
| surface | `#FFFFFF` |
| surface alt | `#EEF3F9` |
| text | `#0F1B30` |
| text-muted | `#5C6B84` |
| line | `#DDE5EF` |
| success | `#0D8259` |
| warning | `#B4530A` |
| error | `#D92D2D` |

Tmavý režim:

| Token | Hex |
|---|---|
| brand (text/odkaz) | `#7B98FF` |
| brand-bg tlačítka (**stejná hodnota v obou režimech!**) | `#2454E0` |
| brand wash | `#1C2C55` |
| background | `#0C1424` |
| surface | `#131E33` |
| text | `#F1F5FB` |
| text-muted | `#94A2BB` |
| line | `#263449` |
| success | `#34D399` |
| warning | `#F0A63E` |
| error | `#F87171` |

Všechny páry ověřené na WCAG AA (4,5:1 běžný text, 3:1 velký text/UI)
— neuprav bez přepočtu kontrastu.

### Barvy zdrojů (marker/chip zdroje — samostatná paleta)

*(Změna 2026-09-02: 4 zdroje sloučené do 2 skupin — původní `source`
se neztrácí, jen se pro barvu/legendu nepoužívá zvlášť pro každý ze 4.
Web to má přesně takhle živě, appka má být 1:1.)*

| Skupina | Zahrnuje `source` | Hex | Tvar |
|---|---|---|---|
| `oficialni` | `cd` | `#3B82F6` | kolečko |
| `komunitni` | `osm`, `mapotic`, `user` | `#F59E0B` | kolečko |
| — (jiná kategorie, ne WC) | výdejní místo | `#64748B` | **čtverec** |

`source_group = source == "cd" ? "oficialni" : "komunitni"` — appka
tohle pole dostane rovnou z `/api_app_places.php` (sekce 2), pro
`/api_locations.php` si ho spočítej stejným vzorcem.

**Původní konkrétní zdroj se nezobrazuje pryč** — je pořád v `source`
poli a appka by ho měla ukázat jako detail/poznámku v místě, kde
zobrazuje popis místa (na webu: malá šedá poznámka pod nadpisem
popupu „Původní zdroj: OpenStreetMap" / „WCkompas (Mapotic)" /
„Přidáno uživatelem" / „České dráhy"). Skupina (`oficialni`/`komunitni`)
je to, co určuje barvu markeru a hlavní label; `source` je jen detail.

### Typografie
**IBM Plex Sans** pro veškerý text/UI (Android Downloadable Fonts API
z Google Fonts, `GoogleFont("IBM Plex Sans")` v Compose — nemusíš nic
bundlovat). **IBM Plex Mono** pro data (souřadnice, vzdálenosti,
štítky). Váhy Sans 400/500/600/700, Mono 400/500/600.

### Spacing, radius, elevace
- Spacing (4px základ): `4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56`.
- Radius: chip/badge/tlačítko = **pill** (999); karta/dialog/sheet =
  20; vnořený prvek (glyf, thumbnail) = 14; drobný prvek = 8.
- Elevace jen dvě úrovně: **rest** (karty v seznamu na plochém
  podkladu — spíš 1px `line` okraj než stín) a **float** (search bar
  nad mapou, FAB, bottom sheet, dialog — `0 6px 20px -6px rgba(15,27,48,.22)`
  v light režimu).

### Marker systém na mapě — tvar + prstenec + barva (nikdy jen barva)

**Toto je teď implementované a živé na webu** (`index.php`, funkce
`markerStyleFor()` / `statusBadgeFor()`) — appka má tuhle přesnou
logiku zrcadlit, ne vymýšlet vlastní prahy:

```
Vstupy: source, likes, dislikes, last_verified

source_group = source == "cd" ? "oficialni" : "komunitni"
color = GROUP_COLORS[source_group]     // #3B82F6 oficialni, #F59E0B komunitni

reported = dislikes > 0 && dislikes > likes
verified = source == "cd" OR (last_verified != null AND
           last_verified je novější než 183 dní)

když reported:
    → DUTÉ kolečko (jen obrys, žádná výplň), tlumená/muted barva
      → badge "⚠ Nahlášeno nefunguje"
jinak když verified:
    → PLNÉ kolečko + bílý 2px prstenec, `color` (podle source_group)
      → badge "✓ Oficiální zdroj (ČD)" (když source=="cd")
        nebo "✓ Nedávno ověřeno" (když přes last_verified)
jinak:
    → PLNÉ kolečko, BEZ prstence, `color` (podle source_group)
      → badge "Bez ověření"

Výdejní místa (jiný dataset, /api_pickup_points.php):
    → vždy ČTVEREC, #64748B, bílý 2px okraj, žádná ikona uvnitř
    → precision=="approx" → menší, průhlednější (odlišit nižší jistotu)
```

`reported`/`verified` se **nepočítají zpětně/kešují na appce** — počítej
je z aktuálních `likes`/`dislikes`/`last_verified` při každém renderu,
stejně jako web.

---

## 4) Obrazovky (v1) — stručně; detailní mockupy má designový artefakt

### Mapa (domovská)
Fullscreen mapa, plovoucí search bar nahoře (Nominatim), FAB vpravo
dole „Najít nejbližší" (`ACCESS_COARSE_LOCATION`, žádané lazy, appka
funguje i bez oprávnění). Klik na marker → bottom sheet (peek: název +
badge stavu + vzdálenost + Navigovat; tap → plný detail). Toggle pro
zobrazení výdejních míst. Legenda (rozbalovací) vysvětlující tvar +
prstenec + barvu — stejný obsah jako web (viz sekce Legenda a stav v
bočním panelu webu).

### Seznam
`LazyColumn`, řazeno podle vzdálenosti (nebo abecedně bez polohy).
Karta: glyf kategorie/skupiny (barva+tvar, viz Marker systém výš),
název, badge stavu (jen u toalet), vzdálenost vpravo. **Filtr nahoře**
(chip řádek nebo segmentovaný přepínač): „Vše" / „Toalety" / „Výdejní
místa" — napojený na `/api_app_places.php?category=` (sekce 2), appka
si díky tomu nemusí filtrovat data sama po fetchi.

### Detail místa
Název, badge stavu (viz marker systém výš), vzdálenost + odhad chůze
(vzdušnou čarou, `dist / 4,5 km/h`, označit jako přibližné), popis,
`likes`/`dislikes` — **hlasování JE dostupné ve v1** (viz sekce 2,
`/api_vote.php` nevyžaduje login). Primární CTA: **Navigovat** —
`geo:` URI nebo `https://www.google.com/maps/dir/?api=1&destination={lat},{lon}`.

### Offline / stav dat
Room cache posledního fetch, cache-first: appka ukáže cache hned, pak
tiše zkusí fresh fetch a vymění. Viditelný pill se stářím dat, když
fresh fetch selže: „Data aktualizována před X hodin".

---

## 5) Architektura (doporučení)

Kotlin + Jetpack Compose + Material 3, MVVM (`ViewModel`+`StateFlow`),
Retrofit + kotlinx.serialization, Room (offline cache), osmdroid
(mapa), Coil (obrázky — i když `photo_url` je teď vždy `null`, piš kód
co to zvládne i s hodnotou), Navigation Compose. Bez DI frameworku
(Hilt) pokud projekt zůstane malý — manuální DI/`ViewModelFactory`
stačí.

---

## 6) Rozsah v1 — co NE

- **Přidávání míst, psaní komentářů** — vyžadují opravdové přihlášení
  (`$_SESSION['user_id']`), appka v1 to neřeší, vede přes web v Custom
  Tabs.
- **Hlasování naopak DO v1 patří** — nevyžaduje login (viz sekce 2),
  jen se dřív mylně předpokládalo opačně.
- Notifikace, widget na plochu, Quick Settings tile.
- Filtrování podle `amenity`/zdroje v UI (`amenity` není spolehlivý
  enum).
- Publikace na Google Play — appka je pro lokální testování
  (`adb install`), Play Store submission je samostatný pozdější krok.

Relevantní podmnožina Google Core App Quality guidelines (ostatní
sekce — audio/video/messaging/biometrika/billing — appky se netýkají):
touch targety 48dp, kontrast 3:1/4,5:1 (barvy výš ověřené), systémová
navigace zpět, funkčnost na výšku i na šířku bez ztráty stavu, světlý+
tmavý režim, zachování stavu appky po návratu z Recents,
`ACCESS_COARSE_LOCATION` žádané lazy s vysvětlením + appka funguje i
bez něj, content-description na každém ikonovém/markerovém prvku,
appka naběhne rychle nebo ukáže progress do 2s, appka jen přes HTTPS
(network security config zakazující cleartext), žádné hardware ID,
nejnovější target/compile SDK, `.aab` formát, žádné logování GPS
polohy do systémového logu.

Kdykoliv narazíš na rozhodnutí, co tenhle brief nepokrývá: **„Kde je
nejbližší vhodné WC?"** — když něco tomuhle cíli neslouží a jen
komplikuje UI, nedělej to.

---

## Výstup / jak postupovat

1. Zkontroluj stav projektové složky (prázdná / existující scaffold) —
   neházej nic pryč bez ptaní.
2. Napiš krátký plán (soubory/moduly v pořadí vzniku).
3. Implementuj postupně, po každém větším kroku sestav (`./gradlew
   assembleDebug`) a oprav, co nejde — máš tu reálný build, nenechávej
   nic neověřené.
4. Na konci shrň, co je hotové, co zbylo rozdělané, a co narazilo na
   něco, co tenhle brief nepokrývá (napiš to jako otevřenou otázku,
   needěl backend sám).
