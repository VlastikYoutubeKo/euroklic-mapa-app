# Euroklíč Mapa — dodatek k API.md (nové/změněné od 2026‑09‑02)

Tenhle soubor **nenahrazuje** `API.md` — jen dodává, co v něm k datu jeho sepsání
ještě nebylo. Platí pro něj stejná pravidla jako pro `API.md`: je to inventář
toho, co backend skutečně dělá, ne přání. Než z něj něco použiješ v appce,
ověř si to znovu (pole se mohou znovu změnit).

Důvod vzniku: audit designového systému appky (`CLAUDE_PROMPT_ANDROID.md` →
artefakt „Euroklíč Mapa — designový systém") narazil na dvě věci, které bylo
potřeba buď doplnit na backendu, nebo opravit v dokumentaci, protože
neodpovídaly realitě zdrojového kódu.

---

## 1) NOVÉ POLE: `last_verified` v `/api_locations.php`

Každá `Feature.properties` teď navíc obsahuje:

```json
"last_verified": "2026-09-02 08:41:07"
```

nebo `"last_verified": null`, pokud místo ještě nikdy nedostalo kladný hlas
od zavedení tohoto pole.

**Typ:** `string|null`, formát `YYYY-MM-DD HH:MM:SS` (SQLite `CURRENT_TIMESTAMP`,
**UTC**, bez časové zóny v řetězci — stejně jako `created_at`/`updated_at`
popsané v `API.md`).

**Sémantika — přečti pozorně, není to totéž co „ověřeno moderátorem":**

- Nastavuje se automaticky na `CURRENT_TIMESTAMP` **pokaždé, když někdo dá
  👍 „Funguje"** přes `/api_vote.php` (nový hlas i změna z 👎 na 👍).
  Zdroj: `api_vote.php`.
- **Nikdy se nemaže ani nevrací zpět** — i případný pozdější 👎 hlas ho
  nechá beze změny. Přestal-li klíč mezitím fungovat, to je jiný,
  aktuálnější signál (`dislikes > likes`), ne otázka „kdy bylo naposledy
  ověřeno". Necombinuj to dohromady v appce.
- **Existující řádky (vzniklé před 2026‑09‑02) mají `last_verified = null`
  bez ohledu na to, kolik už mají `likes`.** Pole se neplní retroaktivně
  podle starých hlasů — nebylo by to pravdivé (nevíme přesně kdy ty starší
  hlasy padly vs. kdy se to skutečně naposledy ověřilo). Počítej s tím, že
  drtivá většina záznamů bude zpočátku `null`, i těch dávno populárních.
- Není to audit trail moderátora, je to **proxy** — „naposledy někdo z
  veřejnosti potvrdil, že tu funguje klíč". Ber to tak i v UI textu
  (viz doporučený štítek níž), ať to nepůsobí jako oficiální certifikace.

**Doporučené použití v appce** (shoduje se s §9 designového dokumentu, jen
konkrétní práh): pokládej místo za „nedávno ověřené" (bílý prstenec markeru,
badge „✓ Nedávno ověřeno"), pokud `last_verified` není `null` a je novější
než **183 dní**. Přesně tohle teď dělá i web (`index.php`, funkce
`markerStyleFor()`/`statusBadgeFor()`).

**Cache:** `/api_locations.php` cachuje výstup a invaliduje podle podpisu,
který mimo jiné počítá `SUM(likes + dislikes)` — protože se `last_verified`
mění výhradně společně se změnou `likes`, existující cache-invalidace to
pokryje automaticky, nebyla potřeba žádná změna cache logiky.

---

## 2) OPRAVA designového dokumentu §13 — hlasování NEVYŽADUJE přihlášení

Designový dokument appky (§13 „Ověřování míst") předpokládá, že zápisové
endpointy vyžadují „web session (PHPSESSID) + CSRF token" ve smyslu
**přihlášeného uživatele**, a kvůli tomu navrhuje variantu A (v1: hlasování
jen přes Chrome Custom Tabs na webu) s tím, že plné in-app hlasování (varianta
B) je až budoucí práce.

**To je nepřesné pro `/api_vote.php` konkrétně.** Ověřeno přímo ve zdroji:

```php
// api_vote.php — začátek souboru
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { ... }
if (!csrf_ok()) { ... }          // ← JEDINÁ kontrola
// ŽÁDNÁ kontrola $_SESSION['user_id'] nikde v souboru
```

`csrf_ok()` (v `config.php`) ověřuje jen, že token v hlavičce
`X-CSRF-Token` (nebo POST poli `csrf`) sedí s tokenem uloženým v **anonymní**
PHP session vázané na cookie `PHPSESSID`. Žádný Discord/Google login k tomu
není potřeba — `session_start()` v `config.php` vytvoří anonymní session i
bez přihlášení, stejně jako to dělá pro anonymního návštěvníka webu.

**Kontrast:** `/api_add.php` (přidání nového místa) **skutečně** vyžaduje
`$_SESSION['user_id']`, tj. opravdové přihlášení přes `/auth.php` nebo
`/auth_google.php`. Tady je varianta A z designového dokumentu (Custom Tabs
na web) pořád správné doporučení pro v1 — na tomhle backend nic nemění.

### Jak z appky reálně hlasovat (funguje už dnes, beze změny backendu)

1. `GET /api_csrf.php` — **s persistentním cookie jarem** (OkHttp/Retrofit
   `CookieJar`, nebo cokoliv, co si mezi requesty drží `Set-Cookie`).
   Odpověď: `{"csrf": "<64 hex znaků>"}`. Server zároveň pošle
   `Set-Cookie: PHPSESSID=...` (HttpOnly, Secure, SameSite=Lax, 30 dní
   životnost — `SameSite=Lax` je relevantní jen pro prohlížeče při
   cross-site navigaci, nativnímu HTTP klientovi nijak nevadí).
2. Token + cookie si ulož (stačí v paměti procesu klidu appky; obnovit
   stačí jen když `/api_vote.php` vrátí `403` s `error` zmiňujícím token).
3. `POST /api_vote.php`, **stejné cookie** co v kroku 1, plus:
   - form-encoded tělo: `id=<location_id>&type=like` (nebo `dislike`)
   - hlavička `X-CSRF-Token: <token z kroku 1>`
4. Odpověď `200`, JSON tělo — **ověř vždy `success`/`error` pole, HTTP
   status sám o sobě nerozliší „už jsi hlasoval" (`success:false`) od
   skutečné chyby** (stejné pravidlo jako u `api_comments.php` v `API.md`).
   Dedup hlasů je podle IP adresy (`REMOTE_ADDR`), ne podle uživatele nebo
   zařízení — jeden hlas na místo na IP, změna názoru (like↔dislike)
   přepisuje předchozí hlas, druhý stejný hlas vrátí
   `{"success": false, "message": "Už jste takto hlasovali."}`.

Nic z tohodle není nová práce na backendu — je to jen oprava mylného
předpokladu v designovém dokumentu. **Zápis nikdy nebyl potřeba zprostředkovat
webem přes Custom Tabs kvůli hlasování** — jen kvůli přidávání nových míst.

---

## 3) Co se v appce/designu doporučuje aktualizovat

- §13 tabulka „Kolize s architekturou" — sloupec u varianty B změnit z
  „v2, ideál" na **dostupné už teď**; varianta A zůstává v platnosti jen
  pro `api_add.php` (přidávání míst), ne pro hlasování.
- §9 callout „Závislost na datech" — `last_verified` je od teď v kontraktu
  (viz bod 1 výš), plný 3-stavový systém (oficiální / nedávno ověřeno
  komunitou / neoznačené + samostatně „nahlášeno nefunguje") jde postavit
  rovnou, ne až „po doplnění backendu".

## 4) NOVÝ ENDPOINT: `GET /api_app_places.php` (jen pro appku)

Sjednocené místo (2026‑09‑02) — appka potřebovala jedno volání pro
seznam s filtrem „toalety / výdejní místa", místo dvou samostatných
fetchů jako web. **Web tenhle endpoint nepoužívá** a nadále volá
`/api_locations.php` + `/api_pickup_points.php` zvlášť — nic z toho se
nemění, tenhle je čistě navíc.

```
GET /api_app_places.php
GET /api_app_places.php?category=toilet
GET /api_app_places.php?category=pickup
```

- Bez `category` vrátí obojí (toalety, pak výdejní místa) v jednom
  GeoJSON `FeatureCollection`.
- `category=toilet` / `category=pickup` vrátí jen danou skupinu.
- Neplatná hodnota `category` (cokoliv jiného než `toilet`/`pickup`) →
  `400`, `{"error": "Neplatná hodnota category. Povoleno: toilet, pickup."}`.
- Každá `Feature.properties` má navíc **`category`**: `"toilet"` nebo
  `"pickup"` — appka podle tohodle pole sekcuje/filtruje seznam, i
  když zavolá bez parametru.
- U `category:"toilet"` položek je pole navíc oproti
  `/api_locations.php`: **`source_group`** — `"oficialni"` (jen
  `source == "cd"`) nebo `"komunitni"` (`osm`/`mapotic`/`user`). Raw
  `source` zůstává v odpovědi taky (viz bod 6 níž) — `source_group` je
  jen zjednodušení pro UI/legendu/filtr, ne náhrada.
- U `category:"pickup"` položek je tvar properties identický s
  `/api_pickup_points.php`.
- Cachuje se stejným způsobem jako `/api_locations.php` (soubor +
  podpis podle stavu DB), zvlášť pro každou hodnotu `category`.
- Chyba: `500`, `{"error": "Chyba databáze."}`.

## 5) ZMĚNA: zdroje sloučené do 2 skupin (web i appka)

Od 2026‑09‑02 se `source` (`cd`/`osm`/`mapotic`/`user`) v **UI**
(web popup, legenda, appka) prezentuje sloučený do dvou skupin:

| Skupina | Zahrnuje | Barva |
|---|---|---|
| `oficialni` | `source == "cd"` | `#3B82F6` |
| `komunitni` | `source` ∈ `{osm, mapotic, user}` | `#F59E0B` |

**`source` (raw hodnota) se z API neodstranila** — `/api_locations.php`
i `/api_app_places.php` ho pořád vrací beze změny, jen se v UI nezobrazují
4 samostatné barvy/legendové řádky, ale 2. Na webu se konkrétní původní
zdroj teď ukazuje jako malá poznámka pod nadpisem popupu („Původní
zdroj: OpenStreetMap" / „WCkompas (Mapotic)" / „Přidáno uživatelem" /
„České dráhy") — appka by měla dělat totéž (skupina jako hlavní
label/barva markeru, `source` jako detail v popisu místa).

`source_group` v `/api_app_places.php` je jen pohodlnostní pole se
stejnou logikou — appka si ji nemusí počítat sama, ale klidně může
(`source == "cd" ? "oficialni" : "komunitni"`).

## 7) NOVÉ: volitelný geografický filtr — `?bbox=` / `?near=&radius_km=`

Přidáno na **všechny tři** GET endpointy: `/api_locations.php`,
`/api_pickup_points.php`, `/api_app_places.php`. Řeší, že appka
stahovala celý dataset (~1442 WC + ~232 výdejních míst) při každém
startu — teď si může poslat jen to, co potřebuje pro aktuální výřez
mapy nebo okolí uživatele.

```
?bbox=minLon,minLat,maxLon,maxLat
?near=lat,lon&radius_km=N          (radius_km volitelný, výchozí 50, cap 500)
```

- Bez parametru = **přesně dnešní chování** (celý dataset), nic se
  nemění pro nikoho, kdo parametr nepošle — web ho nepoužívá vůbec.
- `bbox` a `near` nejde kombinovat — poslání obojího najednou vrátí
  `400`, `{"error": "Zadejte jen jeden z parametrů: bbox, nebo near + radius_km."}`.
- Neplatný `bbox` (špatný počet částí, nečíselné hodnoty, `min >= max`,
  mimo WGS84 rozsah) → `400`, `{"error": "Neplatný bbox. Formát: minLon,minLat,maxLon,maxLat."}`.
- Neplatný `near`/`radius_km` (špatný tvar, souřadnice mimo rozsah,
  `radius_km <= 0` nebo `> 500`) → `400`,
  `{"error": "Neplatný near/radius_km. Formát: near=lat,lon&radius_km=N (N do 500, výchozí 50)."}`.
- Na `/api_app_places.php` jde `bbox`/`near` **kombinovat s `category`**
  — filtr se aplikuje na to, co `category` už vybrala (viz bod 4).

**Rozhodnutí o cache logice** (proč, ne jen co): `bbox`/`near` jsou
spojité parametry (nekonečně možných hodnot), takže "cachovat podle
hodnoty parametru" by nikdy nic netrefilo — každý request s trochu
jinými souřadnicemi by byl cache miss. Místo toho:
- Cache (soubor + podpis podle stavu DB) zůstává **beze změny** — pořád
  ukládá celý (nefiltrovaný) dataset, přesně jako dřív.
- Geografický filtr se aplikuje **až na hotové pole** `features`, těsně
  před `json_encode` na výstupu — ať šlo o čerstvě vygenerovaná data,
  nebo o data načtená z cache (cache hit se v tom případě rozkóduje,
  přefiltruje, znovu zakóduje jen pro tenhle jeden response, ale
  soubor cache samotný se tím nepřepisuje).
- Náklad navíc: jeden průchod polem (`O(n)` porovnání souřadnic), řádově
  levnější než dedup, co cache stejně řeší.
- Sdílená implementace: `geo_helpers.php` (nový soubor, `require`
  z `config.php`, takže automaticky dostupný všude) —
  `geo_resolve_filter_or_400()`, `geo_filter_features()`.

## 8) NOVÉ: pole detailu WC — `web_url`, `opening_hours`, `access`

Přidáno do `Feature.properties` u `/api_locations.php` a
`/api_app_places.php` (kategorie `toilet`). Nic z tohodle není nový
sloupec v DB — počítá se za běhu z `id`/`name`/`description`/`source`
(funkce `toilet_extra_properties()` v `geo_helpers.php`).

```json
"web_url": "https://euroklic.odjezdy.online/lokace/1548-euroklic-adamov-cd",
"opening_hours": "Po-Ne 04:30-23:30",
"access": "eurokey"
```

- **`web_url`** — vždy vyplněné, `string`. Používá **existující** route
  `/lokace/{id}-{slug}` (Caddyfile `rewrite` na `/index.php?id={id}`,
  `index.php` pak vykreslí plnou HTML stránku s OG tagy a
  `schema.org/PublicToilet` JSON-LD pro to konkrétní místo — appka ji
  může použít pro "Nahlásit problém" i pro sdílení). Slug generuje
  stejná `makeSlug()` funkce, co dřív byla jen v `index.php` (teď
  přesunutá do `geo_helpers.php`, používá ji obojí beze změny chování).
- **`opening_hours`** — `string|null`, **volný text** (ne OSM
  `opening_hours` syntaxe). Zdroj: `update_cd.php` ukládá otevírací
  dobu ČD stanice jako text přímo do `description`
  (`"🕒 Otevírací doba stanice: ..."`), tohle pole ho jen vytáhne
  regexem ven pro appku. **Pokrytí: jen `source == "cd"`, a ne ani
  všechny ty (93 ze 109 aktuálně).** OSM `opening_hours` tag se dnes
  při importu vůbec neukládá (viz otevřená otázka níž) — u `osm`,
  `mapotic`, `user` je vždy `null`.
- **`access`** — `"eurokey"` | `"unknown"`. **Odvozeno z metodiky
  importu, ne uloženo/kurátorováno ručně** — ověřeno proti zdroji:
  - `source == "cd"`: `update_cd.php` stahuje z ČD API výhradně stanice
    filtrované dotazem `"WC osazeno eurozámkem"` → `"eurokey"` pro
    všechny (109/109).
  - `source == "osm"`: `update.php` importuje do DB jen OSM body, které
    přísný Overpass dotaz vyfiltroval na tag `centralkey=eurokey` NEBO
    textovou zmínku eurokey/eurozámku v `description`/`note`/
    `wheelchair:description` → `"eurokey"` pro všechny (jiné OSM body
    se do DB vůbec nedostanou).
  - `source == "mapotic"` (WCkompas): **ověřeno přímo na webu** —
    `wckompas.cz` je obecná "Online mapa toalet (Pacienti IBD)", BEZ
    filtru na eurozámek. Import v `update.php` bere všechny body z
    jejich mapy bez rozdílu → `"unknown"`.
  - `source == "user"`: formulář `api_add.php` tohle pole vůbec
    nesbírá → `"unknown"`.

**AKTUALIZACE 2026-09-02 (druhé kolo požadavků appky) — `wheelchair`,
`accessibility_note` a `country` teď JSOU implementované:**

### `wheelchair` + `accessibility_note` — z reálných dat ČD stanic, ne odhad

Uživatel si všiml, že stránky stanic na `cd.cz` (ty samé, co
`update_cd.php` už stahoval kvůli GPS/jménu/hodinám) mají strukturovanou
sekci "PŘÍSTUPNOST STANICE" s kódy jako `b1`/`b2`/`b0`, `n0`/`n1`/`n3`,
`z1`/`z2`/`z3` + plným textovým popisem u každého. Přidáno do
`update_cd.php` (funkce `parseAccessibility()`), **ověřeno na živých
datech všech 109 stanic** (2026-09-02): 102× přístup do budovy
bezbariérový, 7× ne, 0× chybějící sekce.

- **`wheelchair`**: `"yes"` / `"no"` / `"unknown"`. Bere se JEN z
  položky "Přístupnost stanice - bN" (přístup do budovy, kde je i WC –
  ne "Přístupnost nástupiště", to je o dostupnosti nástupišť/vlaků,
  jiná otázka). Klasifikace: obsahuje-li popisný text slovo "není" →
  `"no"`, jinak (text říká "je bezbariérově přístupný") → `"yes"`.
  Funguje nezávisle na konkrétním čísle za "b" (b0/b1/b2...), kdyby ČD
  stupnici v budoucnu rozšířili. `"unknown"` jen když stránka sekci
  přístupnosti vůbec nemá (v aktuálních 109 stanicích 0×, ale appka ať
  s tím počítá).
- **`accessibility_note`**: `string|null`, syrový výpis VŠECH položek
  ze sekce (stanice + nástupiště + zrakově/sluchově postižení,
  ne jen tu použitou pro `wheelchair`) jako volný text, řádek na
  položku ("Název: popis"). Pro appku, co chce zobrazit víc než
  zjednodušené ano/ne.
- **Pokrytí: jen `source == "cd"` (109 řádků).** `osm`/`mapotic`/`user`
  nemají odkud tohle vzít (OSM `wheelchair` tag se dnes při importu
  nezachytává) → `wheelchair: "unknown"`, `accessibility_note: null`.
- Backfill proběhl živě (skutečné stažení všech 109 stránek `cd.cz`,
  ne dry-run) — hotovo, není to jen připravený kód čekající na spuštění.

### `country` — skutečný point-in-polygon, ne odhad obdélníkem

Dřív jsem tohle explicitně odmítl jako "obdélníkový odhad by lhal" —
platí to pořád, ale mezitím uživatel navrhl použít
[datahub.io/core/geo-countries](https://datahub.io/core/geo-countries/_r/-/data/countries.geojson)
(reálné hranice zemí, Natural Earth, 258 zemí, ~14 MB) místo
obdélníku. To je kvalitativně jiná věc — implementováno:

- Soubor `_archive/countries.geojson` (mimo webroot, `_archive/*` je
  v Caddyfile blokované – stejně jako u `_archive/import_osm_eu.py`
  dřív). `_archive/country_lookup.php`: `country_for_point($lat, $lon)`
  — bbox pre-filter (rychlé zamítnutí většiny zemí) + přesný
  ray-casting point-in-polygon test (řeší i díry/exklávy –
  `MultiPolygon` se všemi prstenci).
- Připojeno globálně přes `config.php` (funkce se jen definují,
  14MB soubor se líně načte až při prvním skutečném volání – žádný
  dopad na requesty, co `country_for_point()` nevolají).
- **`wheelchair` u nových zemí:** dataset sám má chybu – Francie a
  Norsko mají `ISO3166-1-Alpha-2 = "-99"` místo `"FR"`/`"NO"` (známá
  historická chyba v Natural Earth datech). Opraveno přes malou
  name→ISO výjimku v `country_lookup.php` (`GEO_ISO_OVERRIDE_BY_NAME`).
  Ostatní `-99` v datasetu jsou sporná/neobydlená území (Severní Kypr,
  Somaliland, Antarktida...) – tam eurozámek nehrozí, neřešeno.
- **Přesnost:** spolehlivé pro cokoliv desítky+ km od hranice. U
  úzkých výběžků/exkláv těsně u hranice (ověřeno na Varnsdorfu – český
  výběžek obklopený Německem ze 3 stran) se zjednodušená geometrie
  datasetu může splést sousední zemí. Ne bug, limitace zdrojových dat
  — appka by to neměla brát jako 100% jistotu u hraničních pixelů,
  jen jako silný odhad.
- **Backfill proběhl na celém existujícím datasetu** (1610 řádků,
  9,3 s, lokálně bez externích requestů): `CZ` 458, `DE` 641 (!),
  `CH` 171, `AT` 89, `SK` 68, `FR` 4, zbytek jednotky. Vysoké `DE`
  číslo NENÍ chyba backfillu — potvrzuje už dřív zdokumentovaný fakt
  (`API.md`), že OSM import je zčásti globální/bez přísného bbox
  filtru u starších řádků. 3 řádky vyšly `null` (pobřežní body u
  zjednodušené linie pobřeží, mimo jakýkoliv polygon) — očekávané,
  appka ať s `country: null` počítá.
- **Nové řádky od teď:** `update.php`, `update_cd.php` i `api_add.php`
  počítají `country` při vkládání/aktualizaci automaticky – řádky
  přibylé POTÉ nezůstanou `null` navždy jako u `last_verified`
  problému dřív.
- Sloupec: `locations.country TEXT DEFAULT NULL` (aditivní migrace).

### `floor_plan_url` — přímý odkaz na orientační plánek stanice (ČD)

Doplněno na výslovnou žádost ("plánky by se měly vracet taky"). **Není
to řešení průzkumného úkolu z `PROMPT_PLANKY_STANIC.md`** (ten se ptá
"kde přesně v budově je to WC" – pořád otevřená, nedotčená otázka).
Tohle je mnohem menší věc: přímý odkaz na stránku `cd.cz`, co appka/web
prostě otevře.

- **Zdroj:** stránka stanice (ta samá, co se stahuje kvůli GPS/hodinám/
  přístupnosti) má `<a class="link" href="/planek/{planekId}">Orientační
  plánek stanice</a>`. `update_cd.php` teď tenhle odkaz vytáhne a uloží
  jako plnou URL.
- **DŮLEŽITÉ: `planekId` NENÍ totéž co ID stanice.** Bílina má na
  stránce stanice `id=5454819`, ale `planekId=548198`. U jiných stanic
  (Praha hl.n.) čísla náhodou vyjdou stejná – nespoléhat na to, vždy
  se musí vzít z odkazu.
- **Pokrytí: 60 ze 109 ČD stanic (55 %).** Zbylých 49 nemá na `cd.cz`
  žádný odkaz na plánek vůbec – ověřeno (ne chyba parsování). `null`
  u nich je pravdivá odpověď, ne mezera k doplnění.
- Jen `source == "cd"` (stejně jako `wheelchair`/`opening_hours`) –
  jinde `null`.
- Backfill proběhl z **už dřív staženého HTML** (žádný nový live
  scrape `cd.cz` navíc) – `update_cd.php` do budoucna nový sloupec
  plní automaticky při dalším skutečném spuštění.

### `photo_url` — funguje přesně jak se ptáš, jen zatím nic nevyplnilo

Potvrzuju: pole **už dnes vrací obrázek, když existuje** – `api_add.php`
umí nahrát fotku (`photo_file` multipart upload nebo `photo` URL pole),
uloží ji do `/uploads/` a `photo_url` ji vrátí. Není potřeba nic měnit
na backendu, aby se fotky "začaly vracet" – prostě je zatím nikdo
nenahrál (nebo ano, viz níž).

**Drobný nález, který appka potřebuje ošetřit:** `photo_url` NENÍ vždy
striktně `null`, když foto chybí – u 4 řádků (staré `api_add.php`
submity) je to **prázdný string `""`**, ne `null`
(`$photo = trim($_POST['photo'] ?? '')` v `api_add.php` defaultuje na
`""`, ne na `null`). 1613 řádků `NULL`, 4 řádky `""`. Appka by měla
"není foto" testovat jako `photo_url == null || photo_url.isBlank()`,
ne jen null-check – jinak jí u těch 4 řádků prokrádne prázdný
`Image(url = "")` request.

Plánky stanic (viz výš, `floor_plan_url`) nejsou "fotky místa" v tomhle
smyslu – jsou to schematické plánky celé budovy, ne foto konkrétního
WC, proto mají vlastní pole, ne že by šly do `photo_url`.

### RUČNÍ obohacení dvou konkrétních záznamů (Bílina, Litvínov) — ne obecná změna importéru

Na výslovnou žádost doplněno `wheelchair`/`accessibility_note`/
`floor_plan_url`/`opening_hours` (přes `description`) u dvou
konkrétních existujících komunitních řádků:
- `id=1658` "ČD Bílina - WC" (stanice `cd.cz/stanice/bilina/5454819`)
- `id=2680` "WC - Litvínov, Nádraží" (stanice `cd.cz/stanice/litvinov/54089`
  — dohledáno přes web search, protože Litvínov není v žádném ČD
  seznamu, co `update_cd.php` normálně prochází)

**Proč ručně, ne přes `update_cd.php`:** obě stanice **nejsou** v
oficiálním seznamu "WC osazeno eurozámkem" (těch 109, co import
prochází) — `update_cd.php` by je nikdy neuviděl, ať běží kolikrát
chce. Data (přístupnost, plánek, hodiny) jsou stažená přímo z jejich
REÁLNÝCH stránek stanice na `cd.cz` stejným parserem
(`parseAccessibility()`), jen spuštěným ručně pro tyhle dvě konkrétní
URL, ne v rámci hromadného importu.

**`source` zůstává `"user"`, `access` zůstává `"unknown"`** — záměrně.
Stránky stanic samy o sobě NEPOTVRZUJÍ text "eurozámek"/"eurokey" (na
rozdíl od stanic ve filtrovaném seznamu, kde to plyne ze samotné
metodiky ČD API dotazu, viz bod 8). Že tahle dvě místa mají eurozámek,
víme jen z komunitního popisu, ne z `cd.cz` — proto by bylo nepoctivé
změnit `access` na `"eurokey"` jen kvůli tomu, že teď máme navíc
přístupnostní data. `description` u obou byl doplněn (ne přepsán) o
"🕒 Otevírací doba stanice: …" stejným formátem jako `update_cd.php`,
takže `opening_hours` se z něj automaticky vytáhne tou samou logikou.

**Tohle NENÍ opakovatelný proces** — je to jednorázová ruční oprava
dvou řádků, ne nový krok v `update_cd.php`. Kdyby se objevily další
podobné případy (komunitní záznam o reálné ČD stanici mimo oficiální
seznam), řešily by se stejně ručně, případně by stálo za zvážení
rozšířit `update_cd.php` o obecnější "stáhni podle URL stanice, ne jen
podle filtrovaného seznamu" režim — to by ale byla samostatná, větší
změna, ne vedlejší efekt týhle opravy.

## 9) NOVÉ: `.well-known/assetlinks.json` — přidán placeholder pro nativní appku

Soubor už existoval (`online.odjezdy.euroklic.twa`, TWA appka z dřívějška).
Přidal jsem **druhý** záznam do stejného JSON pole, placeholder pro
nativní appku:

```json
{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "PLACEHOLDER_NATIVE_APP_PACKAGE_NAME",
    "sha256_cert_fingerprints": ["PLACEHOLDER_NATIVE_APP_SHA256_CERT_FINGERPRINT"]
  }
}
```

Až appka má `applicationId` a podpisový keystore, nahraď obě
`PLACEHOLDER_*` hodnoty (`package_name` = `applicationId` z
`build.gradle`, fingerprint = `keytool -list -v -keystore ... | grep SHA256`)
— soubor je na `https://euroklic.odjezdy.online/.well-known/assetlinks.json`.

**Otevřená otázka — `euroklicmapa.cz`:** appka chtěla assetlinks i pro
tuhle doménu. **`euroklicmapa.cz` dnes nemá v Caddyfile žádný site
block** — není to jen chybějící soubor, doména tu vůbec není
obsloužená (a nevím, jestli na tenhle server vůbec DNS ukazuje).
Nasazení nové domény je samostatné rozhodnutí (DNS, TLS cert, Caddy
site block, případně redirect/rebrand strategie) — nedělal jsem to
tiše jako vedlejší efekt týhle dávky, to je na potvrzení zvlášť.

## 10) Co se NEZMĚNILO (pro jistotu)

- `/api_add.php` pořád vyžaduje přihlášení (viz bod 2) — appka pro
  „Přidat místo" ve v1 správně vede přes web (Custom Tabs), přesně podle
  designového dokumentu.
- `/api_locations.php` a `/api_pickup_points.php` mají beze parametrů
  **stejný tvar odpovědi jako předtím, jen s poli navíc** (`last_verified`,
  `web_url`, `opening_hours`, `access` — vše aditivní, nic se
  nepřejmenovalo ani neodebralo) — web je volá dál úplně stejně, beze
  změny chování, protože žádný z nových query parametrů nepoužívá.
  `/api_app_places.php` (bod 4 výš) je nový, ale jen jako doplněk pro
  appku, nenahrazuje ani nemění je.
- Zbytek kontraktu z `API.md` (GeoJSON `[lon, lat]`, žádné CORS/rate-limit/
  stránkování, HTTP 200 fallback na homepage pro neznámé cesty, `amenity`
  nespolehlivé, `photo_url` u drtivé většiny záznamů prázdné) beze změny.
  Feed **záměrně** obsahuje i místa mimo ČR/SK (příhraničí, cizina) —
  appka je neořezávat, appka si o to řekne sama přes `bbox`/`near`
  (bod 7), pokud/kde to chce omezit.
