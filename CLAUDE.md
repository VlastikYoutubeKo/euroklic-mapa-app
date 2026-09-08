# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this app is

**Euroklíč Mapa** — a native Android app that answers one question on every screen:
*"Where is the nearest suitable toilet?"* It maps barrier-free toilets unlockable with
a *Euroklíč* (a universal accessibility key) in Czechia and Slovakia. It is the native
counterpart to the live public website `euroklic.odjezdy.online` (PHP + SQLite).

Target users are people with reduced mobility, seniors, and parents with strollers.
**Accessibility is an entry condition, not a feature.**

### Hard constraints

- **Never modify the backend. Only consume its existing APIs.** It is live and in public use.
- ~~v1 is read-only browse.~~ **Superseded 2026-09-04 (user OK'd full scope):** the app now
  does Discord/Google **login** (token-based), **add-place with photo**, and an **admin
  moderation queue** — see "Auth & write features" below. Anonymous voting was already in.
  **Superseded 2026-09-06 (user):** local notifications (WorkManager, phase 1) are in — see
  "Local notifications" below. Still no widgets, no FCM/push (phase 2, BACKLOG 2.2), no Play
  Store publishing. Favor the "nearest suitable toilet" use case; drop anything that only
  complicates the browse UI.
- Accessibility requirements that code must uphold: 48dp min touch targets; WCAG AA contrast
  (the color tokens in `ui/theme/Color.kt` are pre-verified — recompute if you change them);
  TalkBack content descriptions on every icon/marker (include distance + point type);
  light + dark following system; state preservation across Recents/rotation; feature parity
  in portrait and landscape; HTTPS only; location requested lazily with a rationale, app fully
  functional without it. **2026-09-05:** now requests `ACCESS_FINE_LOCATION` **and**
  `ACCESS_COARSE_LOCATION` together (user asked for precise location after testing on a real
  device) — either grant counts (`LocationRepository.hasPermission()`), and `refresh()` asks for
  `PRIORITY_HIGH_ACCURACY` only when fine was actually granted (the OS caps the fix to whatever
  was granted regardless, so this is just honest about what we're asking for).

## Backend API (read-only, consume as-is)

Base URL `https://euroklic.odjezdy.online/` (`EuroklicApi.BASE_URL`).

Authoritative source: the backend owner's 2026-09-02 context file, saved as
`docs/backend-context-2026-09-02.md` (plus its earlier dodatek). Where it and
`docs/backend-api-inventory.prompt.md` disagree, the context file wins for **API +
marker-state logic**. The published design artifact stays authoritative for visual detail,
screen layout, and rationale.

- `GET /api_app_places.php[?category=toilet|pickup]` → unified list feed; each feature has
  `category` and toilet features also `source_group` (`oficialni`/`komunitni`). Wired
  (`AppPlaceProperties`, `EuroklicApi.appPlaces()`) but unused — the Seznam filters the merged
  cache client-side.
- **`?bbox=minLon,minLat,maxLon,maxLat`** and **`?near=lat,lon&radius_km=N`** (N 1–500, applied
  after the server's cache) on all three GET endpoints; combinable with `category`. The app
  fetches `near=<last known>&radius_km=500` when a location is known, full feed otherwise, and
  **falls back to the full feed if the scoped fetch is empty** (user abroad). The feed
  **intentionally spans beyond CZ/SK** — do not re-add a client-side geo clamp.
- WC features (`/api_locations.php`, `/api_app_places.php` toilets) also carry: **`web_url`**
  (`/lokace/{id}-{slug}` — real route), **`opening_hours`** (free text, ČD stations only),
  **`access`** (`"eurokey"` for `cd`+`osm`, `"unknown"` for `mapotic`+`user`),
  **`wheelchair`** (`yes|no|unknown` — real ČD-station building accessibility, `unknown`
  elsewhere), **`accessibility_note`** (raw multi-line text, ČD only), **`country`**
  (ISO-2 point-in-polygon, or `null`; DE has more rows than CZ — feed is OSM-global),
  **`floor_plan_url`** (direct link to the ČD station `/planek/{planekId}` page — note
  `planekId` ≠ station id; ~60/109 ČD rows, `null` elsewhere).
  All wired: DTO → entity (DB **v7**, `MIGRATION_5_6` + `MIGRATION_6_7`), shown in
  `DetailScreen.WcBody` (`WheelchairCard`, `ExpandableSection` for the note, `ForeignBadge`,
  `FloorPlanLink`) and as a country tag on `PlaceCard` (`util/Countries.kt`,
  `isForeignCountry`/`countryName`). `photo_url` may be `""` not just `null` for a few old
  rows — the app already tests `isNullOrBlank()` (`DetailScreen.Hero`).

- `GET /api_locations.php` → GeoJSON `FeatureCollection` (`count` ~1580), `approved=1` only,
  server-side deduped. No query params, no pagination. `properties.source` ∈ `cd | osm |
  mapotic | user`. `geometry.coordinates` is `[lon, lat]` (WGS84). `photo_url` is `null` for
  **every** row today. `amenity` is a free-ish string — **not a usable enum**, don't filter on
  it. **`last_verified`** (`"YYYY-MM-DD HH:MM:SS"` UTC, or `null`) — auto-set to now whenever
  someone upvotes via `/api_vote.php`; never cleared; not backfilled, so most rows are `null`.
- `GET /api_pickup_points.php` → same shape, ~232 points. `precision` ∈ `address | approx`
  (~29 `approx`). **`kraj`, `district`, `address` nullable** (`district` missing ~84/232,
  `address` ~64/232). `phone` may hold several comma-separated numbers — use the first for `tel:`.
- **Any `api_*.php` may return HTTP 200 with `{"success": false, …}` or `{"error": "…"}` in the
  body.** Always check `success` / `error`, never rely on the status code. A missing path
  returns the homepage HTML with 200 (the kotlinx.serialization converter throws on it).
- **Voting works from the app, no login.** `GET /api_csrf.php` (needs a persistent `CookieJar`)
  → `{"csrf":"<64 hex>"}` + `Set-Cookie: PHPSESSID`. Then `POST /api_vote.php`, form body
  `id=<locId>&type=like|dislike`, header `X-CSRF-Token: <token>`, same cookie. Dedup by **IP**
  (not device); repeat same vote → `{"success":false,"message":"Už jste takto hlasovali."}`;
  success → `{"success":true,"likes":N,"dislikes":N}`. Refetch the token only on `403`.
- `POST /api_add.php` (new place) still needs a real OAuth session → out of scope for v1
  (link to the web in Custom Tabs). `GET /api_comments.php` is auth-free but also deferred.
- No CORS / `Last-Modified` / rate-limit headers. **`ETag` (2026-09-05):** the two GeoJSON
  feeds (`/api_locations.php`, `/api_pickup_points.php`) now send `ETag: "<md5hex>[-gzip]"` +
  `Cache-Control: no-cache` (revalidate every time). The ETag is computed from *data signature
  + raw query string*, so each `near=`/`bbox=`/`id=` variant has its own. **App side:** handled
  by an OkHttp disk `Cache` (`cacheDir/http_cache`, 20 MB) on the shared client — it turns each
  `refresh*()` into a conditional GET (`If-None-Match`), and on `304` replays the stored body so
  the repo still parses a full `FeatureCollection` but nothing crossed the wire. Cache key = full
  URL incl. query string, so per-variant ETags Just Work. No repo/DTO changes; other endpoints
  send no cache headers so nothing else is stored. (A further micro-opt — skipping the
  Room clear+reinsert on a 304 — would need `Response<T>` plumbing + a per-URL ETag table and
  wasn't worth it; the network saving is the point.)
- **Open question (unresolved):** the live `/api_locations.php` returned ~800 of 1442 points
  outside CZ/SK. `CzSkBounds` clamps as a stopgap; still needs a server-side fix or a decision.

### Marker / place status — mirror the website exactly (do not invent thresholds)

**Two separate concerns, both mirroring the website 1:1:**

**Marker shape/ring** — `util/PlaceStatus.placeStatus(source, likes, dislikes, lastVerified)`
→ `PlaceStatus` enum, recomputed every render, drives the dot in `EuroklicMap` + the glyph in
`SourceGlyph`:
- `reported = dislikes > 0 && dislikes > likes` → **hollow** circle (`isHollow`).
- else `source == "cd"` → **filled + white ring** (`hasRing`).
- else `last_verified != null && age < 183 days` → **filled + white ring**.
- else → **filled, no ring** (`UNVERIFIED`).
- Pickup points: always a slate square + white border; `approx` precision → smaller / translucent.

**Text badge** — `util/PlaceStatus.voteBadge(likes, dislikes, isCd)` → `VoteBadge` enum, used
by `StatusBadge(likes, dislikes, isCd)`. Deliberately **does NOT use `last_verified`** (that's
only for the ring above) — purely likes/dislikes/`cd`, exactly the website popup badge:
- `dislikes > 0 && dislikes > likes` → `REPORTED` → "⚠ Nahlášeno nefunguje (N👎)".
- else `likes > 0` → `VERIFIED` → "✓ Ověřeno · N👍" (any source — an old community row with
  votes but a null `last_verified` still lands here).
- else `isCd` → `CD_NO_VOTES` → "Zatím bez hlasů", **neutral grey, no ✓** (104/109 ČD rows).
- else → `NONE` → renders nothing.
`SourceBadge` ("Oficiální / Komunitní zdroj") carries the origin — the badge must not repeat it.
Wired on `DetailScreen.WcBody`, the map `SelectedPlaceCard` (`MapMarker` carries `likes`/
`dislikes`), and `PlaceCard` (`compact` → "Ověřeno"/"Nahlášeno"/"Bez hlasů").

Map tiles: same source as the website — `https://api.mapy.com/v1/maptiles/basic/256/{z}/{x}/{y}?apikey=...`
(raster). Attribution "© Seznam.cz a.s. a další" is mandatory.

### Auth & write features (2026-09-04, backend deployed)

**Login = token-based with a PKCE code exchange** (RFC 8252). `data/auth/`: `AuthRepository`
(state machine `Loading/LoggedOut/LoggedIn(MeResponse)`, one-shot `AuthEvent` flow for toasts),
`AuthTokenHolder` (`@Volatile` token for the interceptor — DataStore is async),
**`SecureTokenStore`** (AES-256-GCM via a device-bound `AndroidKeyStore` key — hand-rolled
because `androidx.security:security-crypto` isn't on the offline classpath; the token on disk
is ciphertext). Own DataStore file `"auth"` (a 2nd `preferencesDataStore("settings")` would
crash), and that file is **excluded from cloud backup + device transfer** (`backup_rules.xml`,
`data_extraction_rules.xml`). `remote/AuthInterceptor` adds `Authorization: Bearer` to every
request, calls `onUnauthorized()` on a 401, and swaps in a fresher token from an
`X-Refreshed-Token` response header (sliding renewal — server rotates tokens older than 60 d).
- Flow: `buildLoginUri()` generates a `state` nonce **and a PKCE `code_verifier`**, opens the
  shared hosted login page `/app-login.php?client=app&state=<nonce>&code_challenge=<base64url
  S256>&code_challenge_method=S256` in the browser (plain `ACTION_VIEW`, no `androidx.browser`
  dep) — that page (live on production since the 2026-09-04 web redesign deploy) lists the
  providers (Discord, Google, …) and forwards `state`/`code_challenge` on to whichever the user
  picks, so **the app never hardcodes a provider list**; adding one (GitHub etc.) is
  backend-only, no app change. Backend redirects to
  `euroklicmapa://auth-callback?code=<one-time, 60 s>&state=…` (or, once verified,
  the App Link `https://euroklic.odjezdy.online/app/auth-callback`) → `MainActivity`
  (`singleTop` + `onNewIntent`, handles both URIs) → `handleCallback` checks the nonce, then
  `POST /api_token.php` with `code` + the local `code_verifier` → real Bearer token → `/api_me.php`.
  A leaked redirect URL is useless without the verifier (which the app never transmits except
  to `/api_token.php` over HTTPS). **Back-compat:** a `?token=…` callback still works directly
  until the backend cuts the `token=` branch.
- Token has **no hard expiry** — server-side revoke + the 60-day sliding renewal above.
  `logout()` hits `/api_logout.php` (revokes *that* token) then clears locally. Backend tags
  each token `via = app`; `/api_add.php` records `locations.submitted_via` — the app neither
  sends nor shows it.
- **App Link** — `<intent-filter android:autoVerify="true">` for
  `https://euroklic.odjezdy.online/app/auth-callback` is live; the debug signing SHA-256 is in
  the server's `assetlinks.json`. Custom scheme stays as the fallback. Release fingerprint →
  assetlinks at release.
- `MoreScreen` `AuthCard` (login dialog with one "Pokračovat" → `/app-login.php`, or avatar +
  username + Odhlásit). `MainScreen` collects `authRepository.events` → `Toast`.

**Add place** — `Destinations.AddPlace` → `AddPlaceScreen`/`AddPlaceViewModel` (Compose
`mutableStateOf` form state) → `AddPlaceRepository` → `EuroklicApi.addPlace()` (`@Multipart`,
fields `name`/`desc`/`lat`/`lon`, photo part `photo_file`, **no CSRF with Bearer**). Location
via `ui/map/LocationPickerMap` (move-map-under-fixed-pin). Photo via `PickVisualMedia` /
`TakePicture` (FileProvider `${applicationId}.fileprovider`, `res/xml/file_paths.xml`, no
`CAMERA` permission) → `util/ImageUtils.downscaleToJpeg` (longest edge 1800, q82, EXIF
rotation) — `/uploads/` caps at 8 MB. Server sets `source='user'`, `approved=0`. Entry: More →
"Přidat místo" (gated; logged-out tap opens the login dialog).

**Admin queue** — `Destinations.AdminQueue` → `AdminQueueScreen`/VM → `AdminRepository` →
`EuroklicApi.adminList()` (`GET /api_admin_list.php` → `{"locations": [...], "count": N}`,
`AdminPlace` = raw DB row, `description` not `desc`) + `adminReview(action, id)` (`POST
/api_admin.php`, `action` ∈ `approve|reject`). More → "Ke schválení" row, shown only when
`me.is_admin`. Approve/reject per card, row drops from the list on success.

**Verified live on the Pixel 10 AVD (2026-09-04, with backend test tokens):** admin login →
LoggedIn card + `is_admin` → Moderace section; queue list renders; reject removes the row;
non-admin login (no admin section); add-place form (name, interactive pin-picker, emulator
camera photo) → multipart submit → "odesláno ke schválení" toast + nav back. Security round:
the auth URL carries `code_challenge`+`S256`; token adoption + AES-GCM-at-rest confirmed (the
on-disk `auth.preferences_pb` is ciphertext, not the raw token) + survives an app restart
(decrypt-on-`init`); logout zeroes the file. The state-nonce check is real — an `adb` deep
link with a mismatched/shell-split `&state=` is rejected. **Not emulator-testable:** the actual
`code`→`token` exchange (needs a real 60-s OAuth `code`) and `X-Refreshed-Token` (only fires
past 60 d) — both verified by the backend via curl.

### Local notifications (2026-09-06, phase 1 — WorkManager only, no backend / no FCM)

`notifications/` package. `androidx.work:work-runtime-ktx` (default `androidx.startup`
initializer — no `Configuration.Provider`, no `hilt-work`). One `enqueueUniquePeriodicWork`
(`"queue_poll"`, `KEEP`, 15 min, `NetworkType.CONNECTED`) scheduled from
`EuroklicApplication.onCreate()`; `NotificationChannels.register()` (API 26+ guard) makes 3
channels there too: `"moderation"` (HIGH, admin only), `"nearby"` (DEFAULT),
`"favorites"` (LOW, reserved — no sender yet).

`QueuePollWorker : CoroutineWorker` casts `applicationContext as EuroklicApplication` for its
deps (`api` is now `public` on the Application for this). `doWork()` runs two independent,
try/caught parts and always returns `Result.success()` (no retry storm — next period is 15 min):
- **Admin** — gated on `notif_admin_queue` pref + `authRepository.state` being `LoggedIn` with
  `me.is_admin`. Polls `api.adminList()`; if `count + photo_count > 0` and either differs from
  the stored last-known counts → `"moderation"` notification ("Čeká na schválení" / "N míst ·
  M fotek"), tap → admin queue. Counts always stored back.
- **Nearby** — gated on `notif_nearby` pref (default **off**, user opts in). Reads cached WC
  ids one-shot from the DAO (`EuroklicRepository.getAllLocationIds()` / `getLocationsByIds()` —
  no `refresh()` side effect). Empty `known_place_ids` → seed silently (no notification). Else
  new ids within ~10 km of `locationRepository.lastKnown` (skipped if null) and ≥ 24 h since
  `last_nearby_notif_epoch` → one `"nearby"` notification, tap → app (Map). Snapshot always
  updated; epoch only when notified.

Prefs live in `data/prefs/NotificationPrefs.kt`, reusing the single `"settings"` DataStore
(`settingsDataStore` is now `internal` in `ThemeRepository.kt` — **never** a second
`preferencesDataStore("settings")`). Keys: `notif_admin_queue` (default true),
`notif_nearby` (default false), `last_admin_place_count`, `last_admin_photo_count`,
`known_place_ids` (comma-joined), `last_nearby_notif_epoch`.

`POST_NOTIFICATIONS` is in the manifest but requested **lazily** — only when a toggle in
More → "Notifikace" is switched on (API 33+; `rememberLauncherForActivityResult`). Denial
keeps the toggle state, notifications just won't show. That section also has a non-toggle
"Notifikace nechodí?" card → `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (OEM battery
optimisation note for Honor/Xiaomi/Samsung). Tap routing mirrors the launcher-shortcut
pattern: notification `PendingIntent` → `MainActivity` `nav=admin_queue` extra →
`EuroklicApplication.requestAdminQueueNav()` pending flag → `MainScreen` consumes it once →
`Destinations.AdminQueue`. Phase 2 (FCM/push) stays deferred — BACKLOG 2.2.

## Build & test

Windows shell: use `gradlew.bat`. Bash tool: use `./gradlew`. Config cache is **on**
(`org.gradle.configuration-cache=true`); the Gradle daemon runs on a **JDK 25** toolchain
(`gradle/gradle-daemon-jvm.properties`). Toolchain: AGP 9.4.0, Kotlin 2.2.10, compile/target
SDK **37** (preview), minSdk 24.

```bash
./gradlew assembleDebug            # debug APK
./gradlew bundleDebug              # debug AAB — ship as .aab, not bare APK (see plan.md)
./gradlew installDebug             # build + install to connected device/emulator
./gradlew testDebugUnitTest        # JVM unit tests (src/test)
./gradlew testDebugUnitTest --tests "cz.euroklicmapa.ExampleUnitTest.addition_isCorrect"   # single test
./gradlew connectedDebugAndroidTest   # instrumented tests (src/androidTest) — needs a device
./gradlew lint                     # Android lint; report in app/build/reports/lint-results-*.html
```

There is no CI, no ktlint/detekt, no Cursor/Copilot rule files. Test coverage is currently
just the Android Studio template stubs.

## Architecture

Single Gradle module `:app`, package `cz.euroklicmapa`, MVVM with `StateFlow`.

### Dependency injection — manual, no framework

`EuroklicApplication.onCreate()` builds the Room database, the Retrofit/`EuroklicApi`
instance, and `EuroklicRepositoryImpl`, then exposes it as `repository`. Composables reach it
via `(LocalContext.current.applicationContext as EuroklicApplication).repository` and hand it
to a `*ViewModelFactory`. Keep it this way unless the project grows substantially — do not
add Hilt.

### Data layer (`data/`)

- `remote/EuroklicApi` — Retrofit interface; responses deserialized with **kotlinx.serialization**
  via a generic `FeatureCollection<T>` (`WcProperties`, `PickupPointProperties`).
  (Moshi and OkHttp logging are on the classpath but not wired in.)
- `model/GeoJsonModels.kt` — GeoJSON DTOs. `Json { ignoreUnknownKeys = true }`. Every field
  the verified contract lists as nullable/sometimes-missing has a nullable type + default, so
  one missing key can't fail the whole `FeatureCollection` parse.
- `local/` — Room **v2**, `exportSchema = false`, `.fallbackToDestructiveMigration(dropAllTables
  = true)` (the DB is a disposable API cache — any schema change just rebuilds it). Entities:
  `locations`, `pickup_points`, `sync_metadata` (one row per feed, last successful sync epoch ms).
- `mapper/Mappers.kt` — `Feature<…>.toEntity()` extensions. **This is where `[lon, lat]`
  becomes `longitude = coordinates[0]`, `latitude = coordinates[1]`** (via `lon()`/`lat()`
  helpers that yield `NaN` for a malformed pair, which `CzSkBounds.contains` then rejects so
  the feature is dropped, not crashed). ViewModels build `GeoPoint(latitude, longitude)`.
- `repository/EuroklicRepository` — **cache-first**. `getLocations()`/`getPickupPoints()`
  return the Room `Flow` with `.onStart { refresh() }`; `refresh*()` fetches, then
  `clear* + insert*` in place (skipped if the response is empty) and stamps `sync_metadata`.
  Network errors are caught (offline-first) but now `Log.w("EuroklicRepo", …)`; a success logs
  `fetched N, in CZ/SK M`. `observeLocationsSync()` exposes the last-sync epoch as a
  `Flow<Long?>` for the freshness banner. Detail lookups are one-shot `suspend` by `Int` id.
- `EuroklicApplication` builds the shared `OkHttpClient` with **60 s read / 30 s connect /
  90 s call** timeouts — the feeds are large one-shot GeoJSON payloads and the default 10 s
  read timed out on a slow link (symptom: "0 míst", silent).
- `remote/Geocoding.kt` — `GeocodingRepository`: address search via public Nominatim
  (`countrycodes=cz,sk`, identifying `User-Agent`, own 20 s-timeout client). `MapViewModel`
  owns the search box state; a hit sets `recenterTarget`.
- `location/LocationRepository` — single shared source of the user's **coarse** location
  (`StateFlow<GeoPoint?> lastKnown`, `refresh()` via `FusedLocationProviderClient`,
  `hasPermission()`). Built in `EuroklicApplication.locationRepository`; every screen's
  ViewModel reads from it so map/list/detail agree on distances. Returns `null` without the
  permission — callers degrade gracefully.

### UI layer (`ui/`)

- **Navigation 3**, not Navigation-Compose. `screens/MainScreen` owns a
  `rememberNavBackStack(Destinations.Map)` and a single **single-pane** `NavDisplay` (no
  scene strategy). `navigation/Destinations` is a `@Serializable sealed class : NavKey`
  (`Map`, `List`, `Favorites`, `More`, `Detail(id, type)`); `type` is `"WC"` or `"PICKUP"`.
  `NavigationSuiteScaffold` (4 items, explicit brand `itemColors`) gives the adaptive
  bottom-bar/nav-rail. System back works; the detail screen also has a floating circular
  back affordance and a bookmark toggle over its hero.
  **`NavDisplay` must be passed `entryDecorators = listOf(rememberSaveableStateHolder…,
  rememberViewModelStoreNavEntryDecorator…)`** — the 1.0.1 default omits the ViewModel-store
  decorator, so without it every `Detail` entry shares one `DetailViewModel` (shows a stale
  id). Don't remove it.
- Each screen gets its ViewModel via `viewModel(factory = XxxViewModelFactory(repository,
  locationRepository, …))` — the factory args are pulled from `EuroklicApplication` with a
  `with(applicationContext as EuroklicApplication) { … }` block in the default arg.
  ViewModels expose `StateFlow` built with `combine(...).stateIn(viewModelScope,
  WhileSubscribed(5000), …)`. `DetailViewModel` uses a `DetailState` sealed interface.
  `ui/viewmodel/Places.kt` owns `PlaceListItem`, `PlaceCategory` and **`flattenPlaces(...)`**
  — the merge-both-feeds-and-sort-by-distance logic shared by `ListViewModel`,
  `FavoritesViewModel` **and `MapViewModel`** (the Map sheet list and the Seznam tab must not
  drift). `MapViewModel` also exposes `category` + `setCategory()` (same 3-way filter as
  Seznam, drives both markers and the sheet list).
- `map/EuroklicMap` — osmdroid `MapView` inside `AndroidView`. **`renderMarkers(mv, RenderState)`**
  rebuilds every marker overlay and runs from *both* the `AndroidView` update lambda (state
  change) and the `DelayedMapListener` (pan/zoom — a pan doesn't recompose), reading the
  current markers/selection/callbacks off a mutable `RenderState` the listener closes over.
  - **Below `CLUSTER_MAX_ZOOM` (13.5)** and >40 markers: screen-space **grid clustering** —
    points in the same `CLUSTER_CELL_PX` (168 px) cell with ≥ `MIN_CLUSTER` (3) collapse into
    one brand-blue count bubble (`Marker`, bitmap cached by count in `clusterBitmapCache`);
    tapping it animates in +2 zoom. No osmdroid-bonuspack (not on the offline classpath) — this
    is hand-rolled.
  - **At/above 13.5 (or ≤40 markers):** individual dots via `SimpleFastPointOverlay` per
    (shape, colour, status) bucket (`MEDIUM_OPTIMIZATION`; `MAXIMUM` can get stuck empty),
    **plus one transparent hit layer** over the whole list so a fingertip lands on them.
    Visible buckets are `clickable = false`; only the hit layer is. Dot radii are
    **`density`-scaled and grow with zoom** (`k = density * (1 + zoomT*0.5)`, `zoomT` ramps
    13.5→18) via the local `r()` helper in `drawIndividual` — raw `SimpleFastPointOverlay`
    px were specks on a real screen.
  - `selectedMarkerId` draws a brand halo + enlarged solid over that point and pans the camera
    so the marker clears the bottom sheet (centre shifted south by ~18 % of the visible
    latitude span, once per selection via `CameraState.lastSelected`). A `MapEventsOverlay`
    turns a tap on empty map into `onMapClick` (clears the selection).
  First camera move: `recenterTarget` (FAB / search) wins; otherwise centre on the first
  location fix; otherwise stay at the CZ/SK default set in the factory — **never auto-fit to
  the dataset** (it spans well beyond CZ/SK). The `Configuration.getInstance()` block before
  the `MapView` loads prefs, sets the UA, **and pins the tile cache**: `osmdroidBasePath =
  filesDir/osmdroid` (survives OS cache eviction), `expirationOverrideDuration = 30 days`
  (Mapy.com sends a short `Cache-Control`, so without this osmdroid re-downloads tiles every
  session), `tileDownloadThreads = 8`, 300/250 MB cache cap/trim.
- `map/MapyCzTileSource` — custom `OnlineTileSourceBase`; the Mapy.com apikey is
  **injected at build time** — `MAPY_APIKEY=` in `local.properties` (gitignored) or the
  `MAPY_APIKEY` env var → `BuildConfig.MAPY_APIKEY`. Missing key = logged warning + no tile
  downloads; the key itself is **not in VCS** (removed 2026-09-05 after the GitHub push).
- `map/MarkerIconFactory.kt` — now only `UserLocationIcon.build()` (the "you are here" dot).
- `util/GeoUtils.kt` — `formatDistance`, `formatWalkingTime` (straight-line, 4.5 km/h; both
  cap for large values), `distanceBetween`, `launchNavigation()` (Maps directions URL →
  `geo:` fallback → toast), `openUrl()`. Both `startActivity` inside `try/catch` — **and the
  manifest has a `<queries>` block**: without it, API 30+ `resolveActivity()` returns null and
  every outward intent (incl. "Navigovat") silently no-ops.
- `util/CzSkBounds.kt` — a run against the live `/api_locations.php` returned ~800 of 1442
  points outside CZ/SK (global OSM `wheelchair=*`). `EuroklicRepositoryImpl` clamps both feeds
  to this box (a rectangle + an explicit Vienna cut-out) on write **and** on read. Remove once
  the feed is confirmed clean server-side (see `docs/backend-api-inventory.prompt.md`).
- `ui/components/` — `PlaceCard` (list/sheet row: glyph tile + title + source/subtitle + votes,
  distance loudest), `SourceBadge`/`SourceGlyph` (shape+colour origin — circle for WC, square
  for pickup), `DataFreshnessBanner` (rounded pill; shows only when cache is >2 h old / empty),
  `LoadingState`/`EmptyState`.
- `theme/` — no dynamic color (brand palette in `Color.kt` is contrast-verified). `Color.kt`
  carries the full light/dark token set incl. `surfaceContainer*`. `Type.kt` is a full IBM
  Plex Sans scale (Google Downloadable Fonts). `EuroklicTheme` object exposes
  `EuroklicTheme.extended` (`ExtendedColors`: `success`/`onSuccess`, `accent`, `textStrong`,
  `brandButton`/`onBrandButton` — the filled-button blue `#2454E0`, identical in both modes,
  used explicitly on the FAB, Navigate CTA, and layer toggle). The composable also drives the
  status/nav-bar icon appearance via `WindowCompat`.
- UI strings are hardcoded Czech in composables, not in `res/values/strings.xml`.

## Current state

`./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` pass. Verified by hand on a Pixel 10
emulator (light + dark). The read-only v1 browse flow is done and redesigned to the brand
system: Map (floating search that geocodes via Nominatim, `FilterChip` category row —
Vše/Toalety/Výdejní — replacing the old pickup toggle, brand FAB, CZ/SK default view,
**persistent `BottomSheetScaffold`**: peek = "N míst v okolí" + the distance-sorted nearby
list, or a `SelectedPlaceCard` when a marker is tapped; drag up for the full list), List
(title + count/sort, location-hint card, bordered `PlaceCard`s, loading/empty states), Detail
(hero band + floating back, source badge, sections, votes, sticky Navigate bar). Offline
freshness pill, HTTPS-only config, coarse-location-only manifest.

Done since (map-screen redesign — `scratchpad/map-screen.html` artifact):
- `MapScreen` `ModalBottomSheet` → persistent `BottomSheetScaffold`
  (`rememberStandardBottomSheetState(PartiallyExpanded, skipHiddenState = true)`,
  `sheetPeekHeight = 232.dp`). Marker tap → `partialExpand()` + `SelectedPlaceCard`; tap empty
  map → deselect. Category `FilterChip` pill floats under the search bar.
- Selected marker highlight + camera offset in `EuroklicMap`; `MapEventsOverlay` for
  tap-to-deselect (see the `map/EuroklicMap` bullet).
- osmdroid **tile-cache fix**: `filesDir` base path + 30-day `expirationOverrideDuration` +
  8 download threads — stops the slow re-download of Mapy.com tiles every session.
- **Marker clustering** (`renderMarkers` in `EuroklicMap`, see the bullet above) — the old
  flat-dot map was an unreadable/unhittable blob at country zoom; now count bubbles below
  z12.5, bigger dots + a transparent hit layer above it. Verified live on the Pixel 10 AVD
  (cold start, drill-in, marker select, deselect, list-row → detail; no ANR/jank).
- `flattenPlaces()` extracted to `ui/viewmodel/Places.kt`, now shared by `MapViewModel` too.
- `floor_plan_url` → DTO → entity (DB **v7**, `MIGRATION_6_7`) → `DetailScreen.FloorPlanLink`.
- The Seznam tab stays (4 tabs) — it's the text-first / TalkBack path; the Map sheet just
  makes the list reachable without leaving the map.

Done since (2026-09-02, backend context file + follow-up):
- `last_verified` → DTO → entity → `util/PlaceStatus.placeStatus()` (mirrors the website:
  REPORTED / OFFICIAL / RECENTLY_VERIFIED / UNVERIFIED, recomputed per render).
- Marker rendering keys on status: hollow ring (reported), filled + white ring (verified),
  filled (unverified); two stacked `SimpleFastPointOverlay`s for the ring. `StatusBadge` on
  cards / marker sheet / detail.
- **Anonymous voting** — `EuroklicApi.csrf()` + `vote()`, `SessionCookieJar` on the shared
  client. `EuroklicRepository.vote()` fetches CSRF (one 403 retry), writes new counts back to
  Room and bumps `lastVerified` on an upvote. `DetailViewModel.vote()` + `VoteRow`. Verified
  live on the emulator.
- **Nav IA → 4 tabs** (Mapa / Seznam / Oblíbené / Více), single-pane `NavDisplay` (dropped
  `ListDetailSceneStrategy` — it was squeezing the map on tablets and isn't needed phone-first).
- **Favourites** — `FavoriteEntity` (denormalised snapshot), DB **v4** with a real
  `MIGRATION_3_4` (favourites are user data, not disposable cache; `fallbackToDestructiveMigration`
  now only covers the v1–v3 gap). `FavoritesRepository`, `FavoritesScreen`/`FavoritesViewModel`,
  bookmark toggle in `DetailScreen` (`DetailViewModel.isFavorite` / `toggleFavorite`). Round-trip
  verified.
- **`MoreScreen`** — kept short on purpose (2026-09-04 declutter — the disclaimer + "co je
  Euroklíč" + NRZP warning used to be three always-visible cards before anything actionable):
  theme toggle (light/dark/system via `data/prefs/ThemeRepository` + DataStore, read in
  `MainActivity`), Přidat místo / Ke schválení (admin) rows, 3 link rows (`util.openUrl`) — one
  merged from 3 that used to point at the identical URL. `AboutScreen` (`Destinations.About`)
  now holds the disclaimer/explainer/NRZP-warning cards + version, one tap away via "O projektu".
  `util/AppInfo.appVersionName()` shared by both.
- **Mini-map** in detail — `ui/map/MiniMap` (non-interactive osmdroid locator, eats gestures).

Done since (backend context file `docs/backend-context-2026-09-02.md`):
- **Sources → 2 groups.** `sourceColor()` / `sourceLabel()` now return the group
  (`isOfficialSource` = `source == "cd"`): official `#3B82F6` "Oficiální zdroj", community
  `#F59E0B` "Komunitní zdroj". Raw source shown only as `Section("Původní zdroj", …)` on the
  WC detail via `originalSourceLabel()`. Marker grouping keys on the 2 groups.
- **List category filter** — `PlaceCategory` (ALL / TOILET / PICKUP) chip row on `ListScreen`,
  `ListViewModel.category` folded into the `combine`. **Client-side** over the already-cached
  merged data (offline-first; the map needs both feeds cached anyway) — verified 807 / 232.
- `data/model/AppPlaceProperties` + `EuroklicApi.appPlaces()` for `/api_app_places.php` are
  wired but **unused** — kept as the escape hatch if server-side filtering is ever wanted.

Done since (backend follow-up + user decision):
- **`CzSkBounds` deleted** — the feed legitimately spans beyond CZ/SK. Repo now drops only
  non-finite coordinates.
- **Geo-scoped fetch** — `EuroklicRepositoryImpl` takes `LocationRepository` and passes
  `near`/`radius_km=500` when a location is known (with full-feed fallback on empty).
- **WC fields wired** — `web_url` / `opening_hours` / `access` through DTO → entity (DB **v5**,
  `MIGRATION_4_5` = 3 `ALTER TABLE`). `DetailScreen.WcBody`: `KeyRequiredCard` when
  `access == "eurokey"`, `Section("Otevírací doba", …)`, `ReportLink` (opens `web_url`).

Still open:
- `photo_url` still effectively empty for every row (`null`, or `""` on ~4 old rows — app
  handles both). `api_add.php` *can* store an uploaded photo; nobody has. Product question.
- **Add/replace a photo on an EXISTING place (2026-09-05).** Photo can no longer *only* be
  attached at creation time. **Both sides shipped & live-verified 2026-09-05.**
  - App submit: `DetailScreen.WcBody` → `AddPhotoRow` ("Přidat fotku" / "Navrhnout jinou
    fotku", between the source section and `VoteRow`), gated on login (logged-out tap → shared
    `LoginDialog`). `ui/components/rememberImagePicker` — shared camera/gallery flow, now used
    by **both** `DetailScreen` and `AddPlaceScreen` (the latter's private picker was deleted).
    `DetailViewModel.uploadPhoto(uri)` + `photoUploading` → `AddPlaceRepository.submitPhoto(
    placeId, uri)` (reuses `downscaleToJpeg` → JPEG ≤1800px q82) → `EuroklicApi.addPhoto(id,
    photo_file)` (`@Multipart POST api_add_photo.php`, Bearer). `AddPhotoResult` sealed type;
    403/404/413/429 each get their own message. `DetailViewModel`/`DetailViewModelFactory`
    gained an `AddPlaceRepository` param.
  - Backend (`POST /api_add_photo.php`): multipart `id` (`locations.id`) + `photo_file`, Bearer
    required. Validates id exists & `approved=1` (else 404), format from content (JPG/PNG/WEBP/
    GIF), 8 MB cap (413), rate limit 10/h/user (429). Queues a `photo_suggestions` row
    (`status='pending'`) — **not** a direct publish. `{success, error?, message?}`, HTTP codes
    where they fit.
  - Moderation is a **separate queue**, additive to `api_admin_list.php` (no-arg):
    `photo_suggestions: [{id, location_id, photo_url, discord_user_id, author_name, status,
    location_name, location_lat, location_lon}]` + `photo_count` (the `locations`/`count` keys
    are untouched). `api_admin.php` gained `action=approve_photo`/`reject_photo` with a
    **`photo_id`** field (separate from `id` for new-place approve/reject). `approve_photo`
    writes `locations.photo_url` + busts the GeoJSON cache (app picks it up on normal refresh);
    `reject_photo` also deletes the file. **Wired into the app** (2026-09-06): `AdminListResponse`
    carries `photo_suggestions`/`photo_count`, `AdminListState.Loaded` gained `photos`,
    `AdminRepository.reviewPhoto(photoId, approve)` → `adminReview(action=approve_photo|
    reject_photo, photoId=)`, `AdminQueueViewModel.reviewPhoto` + `actingPhotoId`,
    `AdminQueueScreen` renders a `PhotoSuggestionCard` section (headers "Nová místa" /
    "Návrhy fotek" show only when both lists are non-empty).
- Station floor plans: `floor_plan_url` (the whole-building schematic link) is now wired, but
  the "where in the building is the WC" exploration (`docs/station-floorplans-*`) is untouched.
- `assetlinks.json` has a placeholder for the native app; fill `package_name` +
  `sha256_cert_fingerprints` at release. `euroklicmapa.cz` has no Caddy block yet — deep links
  use `web_url` (`euroklic.odjezdy.online/lokace/…`) for now.
- Station floor plans (`docs/station-floorplans-exploration.prompt.md`) — a separate
  read-only exploration of whether cd.cz plans can yield "where in the building the WC is".
  Not started; no app change until that returns something usable.
- The map still fetches by `near` and shares one Room cache with the list; `bbox`-on-pan for
  the map is a possible optimisation, not done.

Not done (deferred): launcher icon (user said ignore for now), splash / Glance widget /
QS tile / deep links, map camera state across process death, tests for the new code
(incl. the notification poll worker), FCM/push (notifications phase 2, BACKLOG 2.2).

### Running on the emulator

`emulator -avd Pixel_10` then `./gradlew installDebug`. The first `/api_locations.php` fetch
takes ~15–40 s on the emulator link; the list shows `LoadingState` until it lands. The AVD's
GPS defaults to Mountain View, so "nearest" distances look absurd until you set a real
location (`adb emu geo fix <lon> <lat>` is unreliable for `getCurrentLocation`; use the
emulator's Extended Controls → Location).

## Optional: import Gemini CLI config

A Gemini CLI config exists at `~/.gemini/settings.json`. To import its portable pieces
(MCP servers, commands, etc.), reply `/import` to see what's importable, then
`/import --yes=<digest>`. Do not hand-copy it.
