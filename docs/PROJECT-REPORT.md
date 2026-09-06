# Euroklíč Mapa — Rozsáhlý dokument o provedené práci

> Podrobná zpráva o celém projektu. Veškerou implementaci provedl Claude
> (Claude Code) — tento dokument shrnuje, co bylo postaveno, jak je to
> strukturované a co zůstává otevřené.
>
> Krátká verze: [SUMMARY.md](SUMMARY.md)
> Autentický zdroj detailů: [CLAUDE.md](../CLAUDE.md), [docs/](.)

*Autor: GLM (z-ai/glm-5.3-flash) — vygenerováno v Android Studio, 2026-09.*

---

## 1. Cíl projektu

**Euroklíč Mapa** je nativní Android aplikace, která na každé obrazovce
odpovídá na jedinou otázku: *„Kde je nejbližší vhodná toaleta?"*

Mapuje bezbariérové veřejné toalety, které lze otevřít **Euroklíčem**
(univerzální přístupový klíč pro osoby s postižením) v Česku a na Slovensku,
a navíc zobrazuje výdejní místa (pickup points). Je to nativní protějšek
veřejného webu `euroklic.odjezdy.online` (PHP + SQLite) — backend je
**nezměnitelný**, aplikace pouze konzumuje jeho existující API.

Cíloví uživatelé: lidé s omezenou mobilitou, senioři a rodiče s kočárky.
Proto platí tvrdá pravidla přístupnosti (WCAG AA kontrast, dotekové cíle
min. 48 dp, TalkBack popisy na každé ikoně včetně vzdálenosti a typu bodu,
podpora světlého/tmavého motivu dle systému, funkčnost bez oprávnění
k poloze).

---

## 2. Technologický stack

| Oblast | Technologie |
|---|---|
| Jazyk | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 (NavigationSuiteScaffold, BottomSheetScaffold) |
| Toolkit | AGP 9.4.0, compile/target SDK 37 (preview), minSdk 24 |
| Architektura | MVVM, `StateFlow`, jediný modul `:app` |
| DI | Ruční (manuální DI přes `EuroklicApplication`) — záměrně žádný Hilt |
| Síť | Retrofit + OkHttp + kotlinx.serialization (GeoJSON) |
| Úložiště | Room v7 (cache), DataStore (nastavení + auth token), AndroidKeyStore |
| Mapa | osmdroid s vlastními rastrovými Mapy.com dlaždicemi |
| Poloha | FusedLocationProviderClient; `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (fine přidáno 2026-09-05 na reálném zařízení), funkční i bez oprávnění |
| Navigace | Jetpack Navigation 3 (`NavDisplay`, `NavKey`) |
| Typografie | IBM Plex Sans (Downloadable Fonts) |
| Build | Gradle s configuration cache, JDK 25 toolchain, `gradlew.bat` na Windows |

---

## 3. Architektura kódu

Balíček `cz.euroklicmapa`, celkem **~64 Kotlin souborů** v `app/src/main/java`.

### 3.1 Aplikace a aktivita
- `EuroklicApplication` — staví Room DB, Retrofit/`EuroklicApi`, `EuroklicRepositoryImpl`,
  `LocationRepository`, `FavoritesRepository`; sdílený `OkHttpClient`
  (60 s read / 30 s connect / 90 s call — velké one-shot GeoJSON payloady).
- `MainActivity` — `singleTop` + `onNewIntent`, zpracovává OAuth callback
  (custom scheme `euroklicmapa://auth-callback` i App Link
  `https://euroklic.odjezdy.online/app/auth-callback`), čte motiv z DataStore.

### 3.2 Datová vrstva (`data/`)
- `remote/EuroklicApi` — Retrofit rozhraní: `locations`, `pickupPoints`,
  `appPlaces`, `csrf`, `vote`, `addPlace` (multipart), `adminList`, `adminReview`,
  `token`, `me`, `logout`.
- `model/GeoJsonModels.kt` — GeoJSON DTOs (`FeatureCollection<T>`),
  `Json { ignoreUnknownKeys = true }`; každé nespolehlivé pole je nullable
  s defaultem, aby jeden chybějící klíč nesestřelil celý feed.
- `model/AuthModels.kt`, `model/AppPlaceModels.kt`, `remote/VoteModels.kt`.
- `local/` — Room v7, `exportSchema = false`; entity `locations`,
  `pickup_points`, `sync_metadata`, `favorites` (v4, reálná migrace —
  oblíbené jsou uživatelská data, nikoli cache).
- `mapper/Mappers.kt` — převod GeoJSON `[lon, lat]` → entity; nevalidní páry
  produkují `NaN` a jsou odmítnuty.
- `repository/EuroklicRepository` — **cache-first**: Room `Flow` s
  `.onStart { refresh() }`, refresh dělá `clear + insert` (přeskočí prázdnou
  odpověď), loguje čas poslední synchronizace. Geograficky scopený fetch
  `near=<pozice>&radius_km=500` s fallbackem na celý feed.
- `repository/FavoritesRepository`, `AddPlaceRepository` (multipart s fotkou),
  `AdminRepository` (schvalování/odmítání).
- `auth/` — `AuthRepository` (stavový automat Loading/LoggedOut/LoggedIn),
  `AuthTokenHolder` (`@Volatile` token pro interceptor),
  `SecureTokenStore` (AES-256-GCM, device-bound `AndroidKeyStore` klíč,
  na disku je vždy ciphertext; soubor vyloučen z cloud backupu).
- `remote/AuthInterceptor` — přidává `Authorization: Bearer`, na 401 volá
  `onUnauthorized()`, nasouvá svěží token z hlavičky `X-Refreshed-Token`
  (klouzvá obnova, server rotuje tokeny starší 60 dnů).
- `remote/Geocoding.kt` — Nominatim vyhledávání (`countrycodes=cz,sk`).
- `location/LocationRepository` — sdílený zdroj hrubé polohy (`StateFlow<GeoPoint?>`),
  bez oprávnění vrací `null` a volající korektně degradují.
- `prefs/ThemeRepository` — motiv (světlý/tmavý/systém) přes DataStore.

### 3.3 UI vrstva (`ui/`)
- `navigation/Destinations.kt` — `@Serializable sealed class : NavKey`
  (`Map`, `List`, `Favorites`, `More`, `About`, `Detail(id, type)`,
  `AddPlace`, `AdminQueue`).
- `screens/MainScreen` — `rememberNavBackStack` + single-pane `NavDisplay`
  (s povinnými `entryDecorators` — bez nich sdílí všechny `Detail` entry
  jeden ViewModel a zobrazuje zastaralé id) + `NavigationSuiteScaffold`
  (adaptivní bottom bar / nav rail).
- **Obrazovky:** `MapScreen` (persistentní BottomSheetScaffold, kategorie-chips,
  brand FAB, vyhledávání), `ListScreen` (řazení, filtry, karty), `DetailScreen`
  (hero, badge, hlasování, otevírací doba, plán stanice, navigace, oblíbené,
  min-mapa), `FavoritesScreen`, `MoreScreen` (přihlášení, přidat místo, admin,
  odkazy), `AboutScreen` (disclaimer, co je Euroklíč, NRZP varování),
  `AddPlaceScreen` (formulář + `LocationPickerMap` + foto), `AdminQueueScreen`.
- `viewmodel/` — `StateFlow` přes `combine().stateIn(WhileSubscribed(5000))`;
  `ui/viewmodel/Places.kt` obsahuje sdílenou logiku `flattenPlaces(...)`
  (sloučení obou feedů + seřazení podle vzdálenosti) — používá ji Mapa,
  Seznam i Oblíbené, takže se nemohou rozejít. ViewModele se vytvářejí přes
  ruční `*ViewModelFactory`.
- `map/EuroklicMap` — osmdroid v `AndroidView`:
  - **Clustering** pod zoom 13.5 (>40 markerů): grid clustering v 168 px
    buňkách, bubliny s počtem (vlastní implementace, bez bonuspacku).
  - Individuální tečky: `SimpleFastPointOverlay` dle (tvar, barva, status)
    + průhledná hit-vrstva; poloměr škáluje s hustotou a zoomem.
  - Výběr markeru: brand halo + posun kamery (střed posunutý na jih o ~18 %
    lat. rozsahu), `MapEventsOverlay` ruší výběr klepnutím do prázdna.
  - Pinování tile cache do `filesDir/osmdroid`, expirace 30 dní — jinak
    osmdroid stahuje dlaždice znovu každou session.
- `map/LocationPickerMap` (pohyb mapy pod fixním pinem), `MiniMap`
  (neinteraktivní lokátor v detailu), `MapyCzTileSource` (API klíč inline),
  `MarkerIconFactory` („vaše poloha" tečka).
- `components/` — `PlaceCard`, `SourceBadge`/`SourceGlyph`/`SourceChip`,
  `DataFreshnessBanner` (pilulka čerstvosti >2 h), `LoadingState`/`EmptyState`.
- `theme/` — žádná dynamická barva; kontrast-ověřená paleta, plná sada
  tokenů light/dark, rozšířené barvy (`EuroklicTheme.extended`), IBM Plex Sans.
  Češtiná texty jsou natvrno v composeables (ne v `strings.xml`).

### 3.4 Utility (`util/`)
- `GeoUtils` — formátování vzdálenosti/času chůze (4,5 km/h), spuštění
  navigace (Maps → `geo:` fallback → toast), `openUrl`; manifest má blok
  `<queries>` (jinak API 30+ `resolveActivity()` vrací null a externí
  intent mlčky nefunguje).
- `PlaceStatus` — **zrcadlí web 1:1**: tvar/ring markeru (hollow = nahlášeno,
  ring = ověřeno/úřední, ≤183 dní) i textový badge (Nahlášeno nefunguje /
  Ověřeno / Zatím bez hlasů / nic) — bez vymýšlení vlastních prahů.
- `Countries` — cizozemské badge (`country` z feedu).
- `ImageUtils.downscaleToJpeg` — dlouhý bok 1800 px, kvalita 82, EXIF rotace.
- `AppInfo` — sdílená verze aplikace.

---

## 4. Co bylo postaveno — chronologie

### Fáze 1 — Read-only v1 (procházení)
- Mapa (osmdroid + Mapy.com dlaždice), Seznam, Detail, povinná atribuce
  „© Seznam.cz a.s. a další", HTTPS-only, pouze coarse poloha s opodstatněním,
  plná funkčnost bez oprávnění.
- Offline-first: Room cache + banner čerstvosti dat.

### Fáze 2 — Redesign mapové obrazovky
- `ModalBottomSheet` → **trvalý `BottomSheetScaffold`** (peek = „N míst v okolí"
  + seznam dle vzdálenosti, tažením celý seznam, klepnutím na marker
  `SelectedPlaceCard`).
- **Grid clustering**, větší tečky + hit-vrstva, výběr markeru s halo
  a offsetem kamery, tap-to-deselect.
- Fix tile cache (filesDir + 30 dní + 8 vláken) — konec pomalého
  opakovaného stahování dlaždic.
- Geokódované vyhledávání přes Nominatim, kategoriové `FilterChip`s
  (Vše / Toalety / Výdejní).

### Fáze 3 — Backend follow-up
- `last_verified` → logika statusu markeru a `StatusBadge` (kopie webu).
- **Anonymní hlasování** — CSRF token + `SessionCookieJar`, retry na 403,
  zápis nových počtů a `lastVerified` zpět do Room.
- **Navigace na 4 taby** (Mapa / Seznam / Oblíbené / Více), single-pane
  `NavDisplay` (ListDetail scene strategy bylo na telefonech na škodu).
- **Oblíbené** — `FavoriteEntity` (denormalizovaný snapshot), DB v4
  s reálnou migrací, `FavoritesRepository` + obrazovka + bookmark toggle.
- **MoreScreen** — přepínač motivu (DataStore), 3 řádky odkazů, sekce
  „O projektu" přesunula disclaimery do `AboutScreen`.
- Mini-mapa v detailu.
- Zdroje sloučeny do 2 skupin: **Oficiální** (`cd`, modrá) vs. **Komunitní**
  (oranžová); původní zdroj jen na WC detailu.
- Filtr kategorií na Seznamu (klient-side nad cache).
- Smazán `CzSkBounds` — feed legitimně sahá i mimo ČR/SK; přidán geoscopený
  fetch `near`/`radius_km=500` s fallbackem na plný feed.
- WC pole `web_url`/`opening_hours`/`access` → DB v5 → karta Euroklíč,
  otevírací doba, odkaz na web.

### Fáze 4 — Auth & write funkce (2026-09-04)
- **Přihlášení token-based s PKCE (RFC 8252):** `state` nonce + `code_verifier`,
  hosted login stránka `/app-login.php` v prohlížeči, callback na
  `MainActivity` (obě URI), výměna `code` → Bearer token přes `/api_token.php`,
  profil přes `/api_me.php`. Zpětná kompatibilita s `?token=` callbackem.
- **Bezpečnost:** token šifrovaný AES-256-GCM (AndroidKeyStore, ruční
  implementace, protože `security-crypto` není na offline classpath); soubor
  `auth` vyloučen z cloud backupu i device transferu; sliding obnova přes
  `X-Refreshed-Token`; logout revokuje token na serveru i lokálně.
- **App Link** `https://euroklic.odjezdy.online/app/auth-callback`
  (`autoVerify="true"`) — debug SHA-256 je v serverovém `assetlinks.json`.
- **Přidání místa** — formulář, interaktivní výběr polohy mapou, foto
  z kamery/souboru (FileProvider, bez `CAMERA` permission), downscale
  na 1800 px, multipart submit; server ukládá `source='user'`, `approved=0`.
- **Admin moderace** — řádek „Ke schválení" viditelný jen když `me.is_admin`,
  schválení/odmítnutí, řádek zmizí po úspěchu.
- **Ověřeno na Pixel 10 AVD:** admin login, moderace, non-admin bez admin
  sekce, add-place včetně emulátorové kamery, ciphertext na disku, restart
  aplikace zachová token, state-nonce kontrola odmítne falešný deep link.
  (Výměna `code`→`token` a `X-Refreshed-Token` ověřeny backendem curl-em.)
- WC pole `wheelchair`/`accessibility_note`/`country`/`floor_plan_url`
  → DB v6+v7 → karty v detailu + country tag na kartě místa.

### Fáze 5 — 2026-09-05 (app-side session: fixy, ETag, foto-prep)
- **Fine location** — appka žádá `ACCESS_FINE_LOCATION` + `COARSE`
  současně (požadavek po testu na Honoru); `LocationRepository` akceptuje
  kterékoli, `PRIORITY_HIGH_ACCURACY` jen při uděleném fine.
- **Fix:** mapa v add-place kradla gesto okolnímu scrollu —
  `LocationPickerMap` dostal `requestDisallowInterceptTouchEvent` listener.
- **Redesign kategorie chipů** nad mapou (bez pilulkového Surface,
  aktivní chip = plná brand modrá + check ikona).
- **FAB „Přidat místo“ i na mapě** (small FAB + login gate);
  `LoginDialog` vytažen do sdíleného `ui/components/LoginDialog.kt`.
- **ETag cache feedů** — backend nasadil `ETag` na locations/pickups,
  app-side přidán OkHttp disk `Cache` (20 MB) → podmíněné GETy, na 304
  se tělo přehraje z cache (ověřeno na emulátoru).
- **Foto k existujícímu místu (app-side prep)** — řádek „Přidat fotku“
  v detailu (login-gated), sdílený `rememberImagePicker`,
  `AddPlaceRepository.submitPhoto` → `EuroklicApi.addPhoto`
  (`POST api_add_photo.php`); endpoint zatím neexistuje → čistá hláška
  „není dostupné“.
- Detaily včetně konverzace s backend session:
  [app-session-report-2026-09-05.md](app-session-report-2026-09-05.md).

---

## 5. Ověření a stav buildu

- `./gradlew :app:assembleDebug` a `:app:testDebugUnitTest` — procházejí.
- Testy zatím jen šablony ze ST (žádná CI, žádný ktlint/detekt).
- Manuálně ověřeno na Pixel 10 emulátoru (světlý i tmavý motiv): procházení,
  mapa s clusteringem, výběr markerů, hlasování, oblíbené, auth, add-place,
  admin moderace.
- Testovací jednotky a lint report: `app/build/reports/`.

---

## 6. Co zůstává otevřené

| Položka | Stav |
|---|---|
| `photo_url` v feedu | Efektivně prázdné pro všechny řádky. **UI pro přidání fotky je v appce hotové** a gated na login — čeká se na backend endpoint `POST /api_add_photo.php` (kontrakt navržen, do moderační fronty). |
| GDPR smazání účtu | `POST /api_account_delete.php` na backendu — **blokující pro Play submit**; produktové rozhodnutí „smazat vs. anonymizovat" je na backendu. App-side řádek „Smazat účet" je malý a čeká. |
| `targetSdk` 37 → stabilní | Nutné před jakýmkoli Play submitem (37 je dnes preview). |
| Půdorysy stanic | `floor_plan_url` je wiring hotov, „kde v budově je WC" (`docs/station-floorplans-exploration.prompt.md`) nevyřešeno. |
| `assetlinks.json` | Debug fingerprint je nasazen; release fingerprint doplnit při vydání. |
| Caddy blok pro `euroklicmapa.cz` | Chybí (doména zatím není); deep links mezitím používají `web_url` (`euroklic.odjezdy.online/lokace/…`). |
| Notifikace / widgety | Ne — záměrně mimo rozsah. QR cross-device login „až úplně nakonec". |
| Testy | Pouze šablony; žádné CI, ktlint, detekt. Zbytek `TODO-IDEAS.md` sekce A bez priority („nechat zatím"). |

---

## 7. Shrnutí dělení práce

Claude provedl kompletní implementaci: architekturu, datovou vrstvu (Retrofit,
Room v7, migrace, mappers), UI (všechny obrazovky, mapový engine s clusteringem,
theme systém), auth s PKCE a šifrovaným úložištěm, hlasování, add-place,
admin moderaci i dokumentaci ([CLAUDE.md](../CLAUDE.md), `docs/`).
Backend zůstal nedotčený — aplikace ho pouze konzumuje, dle tvrdého
omezení projektu.