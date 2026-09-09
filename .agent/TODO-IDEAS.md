# TODO / nápady pro další implementaci — Euroklíč Mapa

> ⚡ **AKTUÁLNÍ PRACOVNÍ SEZNAM je teď [BACKLOG.md](BACKLOG.md)** (fotky, notifikace,
> bložáky — s acceptance criteria). Tahle файла zůstává jako dlouhodobé nápady.

> Soubor pro Claude: co by ještě stálo za implementaci v aplikaci
> (`:app`, `cz.euroklicmapa`) a na webu (`euroklic.odjezdy.online`).
> Vzniklo porovnáním stavu aplikace s živým webem (kontrolováno přímo cURL-em,
> `api_locations.php` vrací 1443 bodů, `photo_url` stále prázdné pro všechny řádky).
>
> Čtěte společně s `CLAUDE.md` — ta zůstává autoritou pro existující kód
> a tvrdá omezení (backend neměnit, přístupnost, coarse-only poloha).

*Autor: GLM (z-ai/glm-5.3-flash) — vygenerováno v Android Studio, 2026-09.*

---

## A. Aplikace — chybějící / dosahující parity s webem

### A1. Komentáře k místům — ✅ READ hotové (2026-09-05), write čeká na backend
- App zobrazuje komentáře v detailu (sekce „Komentáře", jen když nějaké jsou;
  datum z `created_at`, autor, text; náznak „přidává se na webu").
  `GET /api_comments.php?location_id=…` (kontrakt ověřen cURL-em:
  `text`/`author` default „Anonym"/`created_at` „YYYY-MM-DD HH:MM:SS";
  200 i na chybu). Ověřeno naživo na „WC Tyršovy Sady" (id 290, Hradec Králové).
- **Write (psaní komentářů) zůstává otevřené** — až backend dá endpoint
  s Bearer, použít anti-spam vrstvy z B3.

### A2. Statistiky — ✅ HOTOVÉ (2026-09-05)
- Karta „Statistiky" v `AboutScreen` (Více → O projektu): počty WC/výdejen,
  počet ověřených/nahlášených, TOP nejlépe hodnocené a nejproblematičtější.
- Počítané client-side z Room cache přes `StatsViewModel` (offline-first,
  žádné nové API); badge-semantika zrcadlí web (ověřeno = 👍, nahlášeno =
  👎 převažuje). Ověřeno naživo (1081 WC / 232 výdejen v cache).

### A3. Widget „Vložit mapu" — výměna zkušeností
- Web má embed widget mapy na cizí weby. Aplikace by v „O projektu" mohla
  mít odkaz/CTA „Sdílet mapu" → otevřít web (Custom Tabs) s vysvětlením
  widgetu. Nenáročné, propagační hodnota.

### A4. Foto k místu — rozjet produktovou smyčku
- `photo_url` je dnes prázdné pro všechny 1443 řádků. Aplikace **umí**
  fotku nahrávat (`AddPlaceScreen` + `ImageUtils.downscaleToJpeg`), ale:
  - Detail místa nezobrazuje fotku, pokud se jednou objeví — ověřit
    `DetailScreen.Hero` proti `isNullOrBlank()` (podle CLAUDE.md hotovo,
    ale otestovat s reálnou fotkou hned, jak první řádek fotku dostane).
  - Web navíc dovolí **vložit fotku odkazem z internetu** (viditelné
    v add-formu na webu). Pokud `/api_add.php` podporuje i `photo_url`
    pole, přidat do app formuláře volbu „Vložit odkaz".
  - Web nemá upload fotky k existujícímu místu — ideální next backend
    požadavek (viz B2).

### A5. GPS „Najít nejbližší WC" — ✅ HOTOVÉ (2026-09-05)
- **Velký brand FAB** na mapě = „Najít nejbližší WC": refresh polohy →
  vycentruje na nejbližší místo (v rámci aktivního filtru kategorie) →
  vybere ho → sheet ukáže `SelectedPlaceCard` s distancí + „Navigovat".
- **Záměrně sloučeno** se starým „vycentrovat na moji polohu" FAB (uživatel
  odmítl dvě skoro identická modrá tlačítka) — výběr nejbližšího místa vždy
  přivede kameru k uživateli; „jsem tu" tečka zůstává na mapě.
- Bez výsledku (žádná poloha / prázdná cache) → toast.
  Ověřeno naživo (Praha 797 m; po `geo fix` na Hradec 0 m).

### A6. Otevírací doba / `opening_hours`
- [x] Parser `util/OpeningHours.kt` napsán (2026-09-08) — lenient, pure-JVM
  (`parseOpeningHours` → `statusAt(Calendar)` → `OPEN/CLOSED/UNKNOWN`). Přepracováno
  2026-09-08 pro reálné formáty feedu: víc klauzulí oddělených **mezerou před dnem**
  (`"Po-Pá 03:50-21:35 So-Ne 04:50-21:35"`), čárkové výčty dnů
  (`"Po,St,Pá … Čt … So,Ne …"`), noční mezery (`"0:00-1:30 2:30-24:00"`), garbled
  den v seznamu se přeskočí (nespadne celý parse). `OpeningHoursTest` = 19.
- [x] **Chip „Otevřeno/Zavřeno" ZPĚT (2026-09-08).** Backend opravil scrape:
  `opening_hours` teď bere blok „Prostory pro cestující" (hala), ne pokladnu —
  je to reálná doba haly, které se dá věřit. Přidáno nové pole
  **`wc_opening_hours`** (`String?`, ~6 stanic, doba přímo pro WC).
  `DetailScreen.OpeningHoursSection(hall, wc)`: zobrazí WC dobu když je
  (label „OTEVÍRACÍ DOBA WC"), jinak dobu haly („OTEVÍRACÍ DOBA STANICE"),
  + chip Otevřeno/Zavřeno z `parseOpeningHours(...)?.statusAt(...)`.
  Wired DTO → entity → DB **v9** (`MIGRATION_8_9`: `wcOpeningHours` na `locations`
  i `favorites`) → `Mappers` round-trip.
- [ ] **[backend] otevřené:** dedup — u některých stanic bohatší `cd` řádek prohrává
  s chudším `mapotic` bodem. Necháváme jako otevřenou `[backend]` poznámku,
  neřešíme app-side. (`opening_hours` teď nese celý multi-day-range blok vč. So–Ne,
  takže původní „zahazuje So–Ne" už neplatí.)

### A7. Testy (dlouhodobě zanedbané) — ČÁSTEČNĚ HOTOVO (2026-09-09)
- [x] `PlaceStatus.placeStatus()` + `voteBadge()` + `VoteBadge.label()` +
      `nowUtcTimestamp()` — `app/src/test/.../util/PlaceStatusTest.kt` (27 testů,
      včetně 183denní hranice a „REPORTED přebíjí cd").
- [x] `flattenPlaces(...)` — `app/src/test/.../ui/viewmodel/PlacesTest.kt`
      (13 testů: kategorie filtr, řazení dle vzdálenosti / abecedy, subtitle fallbacky).
- [x] `Mappers.kt` — `[lon, lat]` → entity (pořadí lon/lat, NEprohozené), `NaN` na
      prázdné/neúplné coords, pass-through všech polí `WcProperties`/`PickupPointProperties`
      (`opening_hours`→`openingHours`, …). `app/src/test/.../data/mapper/MappersTest.kt` (10 testů).
- [x] `QueuePollWorker` čisté funkce — vytažené do `internal object QueuePollLogic`
      (`notifications/QueuePollLogic.kt`, bez změny chování workeru): `places`/`photos`/
      `newPlacesNearby` (české skloňování) + `adminShouldNotify(...)`.
      `app/src/test/.../notifications/QueuePollLogicTest.kt` (8 testů: pluralizační hranice
      0/1/2/4/5/22, pravdivostní tabulka `adminShouldNotify` vč. sentinelu `-1` na prvním pollu).
      `./gradlew :app:testDebugUnitTest` = 90 testů, 0 failures. `doWork()` / `CoroutineWorker`
      se neunit-testuje (potřebuje Android + živý `EuroklicApplication`).
- [ ] `SecureTokenStore` — instrumented test šifrování/dešifrování. (zbývá, instrumented)
- [x] CI (GitHub Actions): `assembleDebug` + `testDebugUnitTest` na každý PR/push do main —
      `.github/workflows/ci.yml` (JDK 25 toolchain, `android-actions/setup-android`, bez
      `MAPY_APIKEY` a bez `lint`).
- Pozn.: `placeStatus` používá lenient `SimpleDateFormat` — číselně tvarovaný ale
  mimo rozsah string („2024-13-99…") se zroluje na platné datum, nespadne do UNVERIFIED.
  Jen skutečně neparsovatelný vstup jde do fallbacku.

### A8. Ladicí / observability
- Chybí crash reporting (Sentry/Firebase Crashlytics — ale zvážit privacy:
  cílová skupina je citlivá na soukromí; Sentry self-hosted jako option).
- `Log.w("EuroklicRepo")` existuje — zvážit jednoduchý in-app „diagnostický
  log" pro beta testery.

### A9. Play Store release prep
- Zatím nikde: release signing config, ověřený `bundleRelease`, **privacy
  policy URL** (Play ji vyžaduje — a app potřebuje vlastní stránku, ne
  jen webovou), Data Safety formulář (app sbírá coarse poloha + auth
  identita → deklarovat), screenshoty, content rating dotazník.
- App targetuje SDK 37 **preview** — pro produkci na Play bude potřeba
  rozhodnutí o targetSdk v čase vydání (preview target může být odmítnut).

### A10. GDPR — self-service mazání dat (týká i webu/backendu)
- Backend ukládá jména, fotky, IP-based vote dedup, Discord/Google
  identitu. Web má privacy page, ale **chybí self-service smazání**
  účtu/dat. Klasický GDPR gap: endpoint typu `DELETE /api_me.php`
  (revokuje tokeny, smaže submitted places/comments/fotky).
- Aplikace pak jen přidá „Smazat účet a data" do `MoreScreen`/AuthCard.
- **Stav 2026-09-05:** endpoint `POST /api_account_delete.php` identifikován
  jako **blokující pro Play Store submit**; produktové rozhodnutí
  „smazat vs. anonymizovat" je na backendu. App-side práce je malá a čeká
  na endpoint.

### A11. Data quality smyčka — „Byl jste tu? Ověřte"
- [x] HOTOVO (2026-09-08, jen detail WC) — `DetailScreen.WcBody`: `VerifyPresenceCard`
  nad `VoteRow`, zobrazí se jen když `type=="WC"` && `placeStatus(...)==REPORTED` &&
  `userLocation != null` && vzdálenost ≤ 75 m. Text „Jste na místě? Ověřte, jestli je
  WC funkční." + dvě kompaktní tlačítka Funguje/Nefunguje (48dp, contentDescription)
  volající stejné `onVote(true/false)` jako `VoteRow`. Není notifikace, jen UI stav.
  Sheet na mapě záměrně netknutý (follow-up).
- Hlasování odpoví na „funguje teď", ale nikdo **nemá za úkol**
  reported místo re-checknout. Nápad: reported bod se dostane do fronty
  „potřebuje ověřit" (web: moderátoři; app: jemný náznak na detailu
  nebo v sheet listu, když je uživatel geograficky blízko *reported*
  místa — „Byl jste tu? Ověřte funguje/nefunguje").
- Jedinečné pro mobil — uživatel doslova stojí u dveří. Nízký friction,
  velký dopad na kvalitu dat. (Není to push notifikace — jen UI stav,
  takže neporušuje „no notifications" omezení.)

### A11b. Offline resilience
- [x] HOTOVO (2026-09-08) — **oblíbené = full detail snapshot.** `FavoriteEntity`
  (DB **v8**, `MIGRATION_7_8` = 18 `ALTER TABLE favorites ADD COLUMN … TEXT`) teď nese
  celý detail: WC (`description`/`note`/`photoUrl`/`webUrl`/`openingHours`/`access`/
  `wheelchair`/`accessibilityNote`/`country`/`floorPlanUrl`) i výdejnu (`address`/`phone`/
  `email`/`hours`/`district`/`kraj`/`precision`/`sourceUrl`, `note` sdílené). `FavoritesRepository.add`
  plní přes `WcLocationEntity/PickupPointEntity.toFavoriteEntity()` (`data/mapper/Mappers.kt`).
  `DetailViewModel` `combine(observeLocation(id), favorites.observeFavorite(id, isPickup))` →
  když živý Room řádek chybí (feed ho `near=` odřízl / smazán server-side / nikdy nesynced),
  spadne na `fav.toWcLocationEntity()` / `toPickupPointEntity()` → detail se otevře i offline.
  Testy: `FavoriteMapperTest` (7 – round-trip všech polí, `source ?: ""`, null → null).
- **~50 nejbližších prefetch je obsolete** — `EuroklicRepositoryImpl` už tahá
  `near=<last>&radius_km=500` s fallbackem na plný feed, takže po prvním syncu je
  fakticky *každý* CZ/SK řádek v Room cache. Prefetch by nic nepřidal.
- Zbývá: Nominatim vyhledávání pořád potřebuje síť (mimo scope A11b).

### A12. Cross-promo web ↔ app
- Detail místa na webu nemá „Otevřít v aplikaci" odkaz (App Links
  deep-linkují app→web obráceně směr nijak); na web detail přidat
  intent link na `euroklicmapa://detail?id=…` → drive instalek.
- Ověřit v app share button: sdílí `web_url` (ne app-only deep link,
  který nebude fungovat pro příjemce bez app) + TalkBack label.

### A13. Home-screen shortcut „Nejbližší WC" — ✅ HOTOVÉ (2026-09-05)
- Static App Shortcut (long-press ikony, žádná permission,
  `res/xml/shortcuts.xml` + `ic_shortcut_near_me`): intent action
  `cz.euroklicmapa.action.NEAREST_WC` → `MainActivity` → pending flag
  v `EuroklicApplication` → `MapScreen` spotřebuje a spustí nearest flow
  (bez permission → rovnou rationale dialog).
- Cold i warm start ověřeny přes `adb am start` se stejným action
  (statický shortcut na launcheru používá tentýž intent).

---

## B. Web (euroklic.odjezdy.online) — návrhy pro backend vlastníka

> Opakování: backend je live a veřejně používaný — **nesmí se měnit
> jednostranně**. Tyto položky jsou návrhy k diskusi s vlastníkem backendu.

### B1. ETag / Last-Modified na feedy — ✅ HOTOVÉ (2026-09-05)
- Backend nasadil `ETag` + `Cache-Control: no-cache` na
  `/api_locations.php` a `/api_pickup_points.php` (ETag = md5(data
  signature + raw query string) → per-`near=`/`bbox=` varianta má vlastní).
- App-side: OkHttp disk `Cache` (20 MB) na sdíleném klientu → každý
  `refresh*()` je podmíněný GET (`If-None-Match`), na 304 OkHttp přehraje
  uložené tělo. Ověřeno na emulátoru (druhý start → 304, cache soubory
  se nezměnily). Detail: [app-session-report-2026-09-05.md](../docs/app-session-report-2026-09-05.md).

### B2. Upload fotky k existujícímu místu — včetně moderace
- `api_add.php` umí fotku u nového místa, ale neexistuje endpoint
  „přidej fotku k existujícímu WC". Killer-feature: komunita začne
  reálně fotit přístupy/dveře → `photo_url` konečně dostane data.
- **Musí jít stejnou moderační cestou jako nové místo** (Discord
  moderátorům ke schválení), ne rovnou publikovat. Anti-spam viz B3.
- **Stav 2026-09-05 — app-side hotové a čeká:** řádek „Přidat fotku" /
  „Navrhnout jinou fotku" v detailu (login-gated), sdílený
  `rememberImagePicker`, `AddPlaceRepository.submitPhoto` →
  `EuroklicApi.addPhoto` (`@Multipart POST api_add_photo.php`).
  Endpoint zatím neexistuje → čistá hláška „Přidávání fotek zatím není
  na serveru dostupné". Backend session má návrh kontraktu (Bearer,
  multipart `id` + `photo_file`, do moderační fronty).

### B3. Komentáře + foto k místu — anti-spam/anti-scam návrhy

Obě B2 a komentáře jsou write-endpointy otevřené komunitě → bez ochrany
za pár dní spam. Vrstvená obrana (od nejjednoduššího po nejpřísnější):

1. **Přihlášení povinné.** Psát smí jen přihlášený (Bearer, už dnes
   existuje). Anonymní hlasování zůstává — psaní je jiné.

2. **Trust level / denní limity.** Nový účet začíná s limitem:
   1 komentář/fotka za den, 3 za první týden. Po schválených
   příspěvcích (moderátor ackne) se limit uvolní. Spam boti
   nemají trpělivost na denní limity — hlavní obrana proti mass-registraci.

3. **Moderační fronta, ne přímý publish** — stejně jako `api_add.php`
   (`approved=0` + Discord webhook moderátorům). Komentář/fotka se
   zobrazí až po schválení. Fallback pro vše podezřelé.

4. **Rate limiting per token/IP** — server-side: 5 write akcí / hodinu
   / účet + 10 / den / IP (dedup po IP už zná hlasování).

5. **Obsahové filtry (levné, spouštějí se před frontou):**
   - délka: min. 3 znaky, max. ~2000
   - odkazy: 0–1 URL na komentář; nový účet s URL → vždy do fronty
   - duplicita: normalizovaný text (lowercase, trim) + hash; stejný
     text od stejného účtu nebo na víc míst = auto-reject. **Dedupuj
     po auth user id, ne po IP** — IP se u mobilů mění a NAT sdílí
     tisíce uživatelů.
   - plain-text only, žádné HTML/markdown render — XSS ochrana zdarma
   - černá listina slov jen jako signál k frontě (ne auto-delete,
     ať nesmekne „Praha" v němčině apod.)

6. **Report tlačítko** u komentáře/fotky (app i web) → ping moderátorům
   na Discord. Zneužívání řeší komunita sama, moderátoři nemusí číst vše.

7. **Audit log** — kdo co schválil/odmítl (`moderated_by`, `moderated_at`),
   ať se dá zpětně dohledat, co prolzlo.

Co NEdělat: captchi (přístupnost! cílová skupina nevyplní reCAPTCHA),
SMS verifikace (friction). Honeypot hidden pole nic nestojí — přidat
jako jednu vrstvu navíc, ale nestačí sám.

### B4. `assetlinks.json` — release fingerprint (pozlěji)
- V `assetlinks.json` je debug SHA-256. Při vydání na Play doplnit
  release fingerprint + `package_name`. Bez toho App Link nefunguje
  a padá se zpět na custom scheme.
- **On hold:** doména `euroklicmapa.cz` zatím není — viz B5.

### B5. Doména `euroklicmapa.cz` — on hold
- Doména zatím není zaregistrovaná. Když bude: Caddy blok + vlastní
  `assetlinks.json` + 301 z aktuální `euroklic.odjezdy.online/lokace/…`
  (nebo zachovat odjezdy.online jako canonical — rozhodnout před launchem,
  aby se neměnilo canonical později → SEO ztráta).

### B7. Lokalizační/správnost názvů
- V seznamu na webu i v app jsou názvy typu „Euroklíč (OSM)" — duplicitní
  a neinformativní. Zvážit (server-side!) náhradu z OSM tagů
  (`operator`, `ref`, adresu), případně heuristiku „WC — ulice, město".

### B8. SEO webu

> Zkontrolováno přímo cURL-em na živém webu (homepage + detail
> `/lokace/638-…`). Základ je **dobrý** — tohle je materiál k dolaďování,
> ne záchrana.

Co už je v pořádku (nedotýkat se):
- `<html lang="cs">`, unikátní `<title>` a meta description i na detailu
  místa (description obsahuje i popis WC — super),
- `<link rel="canonical">` na obou typech stránek,
- Open Graph (og:title/description/image) na homepage,
- `robots.txt` + `sitemap.xml` (existuje, ~257 kB),
- 1× `<h1>` na stránce, `theme-color`.

Chybí / stojí za práci:
1. **Structured data (JSON-LD) na detailu místa** — hlavní příležitost.
   `ld+json` na stránkách je, ale detail místa by měl nést
   `Place`/`TouristDestination` typu schema.org se `geo`, `openingHours`
   (pokud známe), `publicAccess`. Google pak ukáže bohatý výsledek
   (adresa, otevírací doba) přímo ve výsledcích hledání „bezbariérové
   WC [město]" — přesně search, kterým uživatelé přijdou.
2. **Detail místa = landing page.** Každé WC má svou URL (`web_url`),
   ale detaily by měly mít i vlastní texty: meta description per místo
   („Bezbariérová toaleta s Euroklíčem v OBI Praha-Štěrboholy —
   ověřeno komunitou, hlášeno funguje") místo generické. Ověřit, že
   detaily se dostávají do sitemap (ne jen homepage).
3. **og:image per místo** — pokud místo má `photo_url`, použít ji jako
   og:image; sdílení do Discordu/WhatsApp pak ukazuje fotku místa
   (reálně i marketing — lidé sdílejí „kam zajít").
4. **FAQ na homepage** — existuje vizuálně; přidat `FAQPage` JSON-LD
   → Google často ukáže FAQ rozbalené ve výsledcích.
5. **hreflang cz↔sk** — pokud bude SR cílená, i pro stejnojmenné
   ČR/SK místa. Zatím site je cs-only, ale sitemap by mohl oddělovat
   `country` (CZ vs SK) cílení.
6. **Výkon**: feed se tahá client-side JS; pro SEO je důležité, že
   texty míst (názvy, popisy) jsou v HTML (jsou — viditelné v curl).
   Zvážit `loading="lazy"` na fotky a preconnect na `api.mapy.com`.
7. **Interní odkazy**: „Seznam míst" na homepage je dlouhý flat seznam;
   skupit po krajích/městech s odkazy (`/lokace/…?kraj=…`) —
   vytváří to crawlable strukturu a long-tail landing pages
   („bezbariérové WC Jihomoravský kraj").

### B9. WC podél trasy („stanice na trase") — diferenciátor
- Nápad: uživatel zadá vlakové spojení (odkud → kam) → app ukáže
  Euroklíč WC + výdejní místa na stanicích a v okolních zastávkách
  po trase. Nikdo konkurenční toto nemá; ČD stanice mají data (i
  `floor_plan_url`).
- **Otevřená otázka: jak na to.** Potřeba zeptat se druhého Claude Code
  instance, která má zdrojový kód `app.odjezdy.online` — tam už
  existuje logika nad vlakovými spoji a ČD daty. Zjistit:
  - existuje API „stations along route" (nebo jen „timetable"),
  - jestli jde spojit ČD trip data s naší `locations` (match podle
    stanice id / názvu),
  - případně jednodušší verze: uživatel zadá výchozí + cílovou
    stanici → list Euroklíč WC na všech stanicích mezi nimi.
- Zatím jen roadmap item — závisí na tom, co app.odjezdy.online
  backend umí a co z toho lze (s jeho vlastníkem) vystavit.

---

## C. Priority (doporučení)

1. **A1 komentáře** (read) + **B3** dotaz backendu na write
2. **A2 statistiky** z lokální cache (levné, výrazná hodnota)
3. **A7 testy** — PlaceStatus/flattenPlaces (ochrana proti driftu)
4. **A5 nejbližší WC** akce + **A13 shortcut** (podstata aplikace,
   málo viditelná; A13 je 1 den práce)
5. **A11 data-quality smyčka** (jedinečná pro mobil, velký dopad)
6. **A9 release prep** — nutné před Play, začít s privacy policy/Data
   Safety včas (nezávislé na kódu)
7. Zbytek dle diskuse s backend vlastníkem (B2 + B3 anti-spam a B8 SEO
   mají největší dlouhodobý dopad; B4/B5 on hold do domény;
   B9 čeká na zjištění z app.odjezdy.online)
