# Euroklíč Mapa — app-session report (auth → add-place → admin → UI fixy 2026-09-05)

Shrnutí práce na Android appce (`cz.euroklicmapa`) od chvíle, kdy uživatel schválil celý rozsah
"auth → add-place → admin", až po dnešní (2026-09-05) drobné UI/bugfixy. Druhá polovina
dokumentu je výběr reálných citací z komunikace s druhou (backend/web) Claude session přes
`SendMessage`/bridge kanál — ne parafráze, skutečné úryvky z přenosu.

Autor: tahle (app-side) Claude Code session. Cílová skupina: uživatel + budoucí Claude session
na tomhle repu, které budou chtít vědět "co se stalo a proč", aniž by musely číst celý transcript.

---

## 1. Co bylo dnes (2026-09-05) změněno

### 1.1 Bug: mapa v "Přidat místo" posouvala celou stránku

**Nahlásil uživatel** (testuje na reálném Honoru, ne jen emulátoru):
> "QUICK PROBLÉM, u přidat míst sliding na mapě posouvá stránku, ne jen tu mapu. to je hodně
> riskantní"

**Příčina:** `LocationPickerMap` (`ui/map/LocationPickerMap.kt`) — osmdroid `MapView` vložená přes
`AndroidView` — sedí uvnitř `AddPlaceScreen`ova `Column().verticalScroll(rememberScrollState())`.
Jakýkoli tah po mapě s vertikální složkou Compose vyhodnotil jako scroll okolní stránky, ne jako
pan mapy pod pinem.

**Fix:** na `MapView` přidán `setOnTouchListener`, který při `ACTION_DOWN` zavolá
`v.parent?.requestDisallowInterceptTouchEvent(true)` (mapa si "zamkne" gesto pro sebe) a při
`ACTION_UP`/`ACTION_CANCEL` to zase odemkne. Listener vrací `false`, takže osmdroidu nic nebere,
jen blokuje krádež gesta okolním scrollem — stejný mechanismus jako mapa uvnitř
`ScrollView`/`RecyclerView` v klasickém Androidu.

Soubor: `app/src/main/java/cz/euroklicmapa/ui/map/LocationPickerMap.kt`.

Ověřeno: `assembleDebug` čistě, nainstalováno na emulátor i na připojený fyzický Honor
(`ALDE6R2B11004838`) — uživatel si to tam ověřuje sám.

### 1.2 Redesign: kategorie chipy nad mapou (Vše / Toalety / Výdejní místa)

**Podnět uživatele:**
> "schválně se jukni nyní jak vypadaj ty tlačítka nahoře, to by mělo být nějak jinak designed ne?
> (tlačítkama myslím to pod searchem, a 'nad' mapou"

**Diagnóza (ze screenshotu na emulátoru):** chip řádek byl obalený ve vlastním bílém pilulkovém
`Surface` se stejným tvarem/barvou/elevací (`shadowElevation = 6.dp`, `shape = 999.dp`,
`color = surface`) jako search bar hned nad ním → oči to čtou jako jeden slitý blob. Aktivní chip
("Vše") byl navíc jen slabě odstíněný `primaryContainer` na bílé — nízký kontrast, těžko na první
pohled poznat, co je vybrané. To je i vizuálně nekonzistentní s tím, jak appka jinde signalizuje
"tohle je aktivní/primární" (FAB, Navigate CTA a layer-toggle používají plnou brand modrou
`EuroklicTheme.extended.brandButton` = `#2454E0`).

**Fix** (`ui/screens/MapScreen.kt`, `CategoryChipRow`/`MapFilterChip`):
- Zrušen obalující pilulkový `Surface` — chipy teď plavou samostatně, ne jako druhá kopie search
  baru.
- Nevybraný chip: `containerColor = surface` + tenký obrys (`FilterChipDefaults.filterChipBorder`,
  `borderColor = outlineVariant`) — čitelnost i nad různobarevnou mapou.
- Vybraný chip: plná `brandButton` výplň + `onBrandButton` text + `Icons.Rounded.Check` jako
  leading icon — stejný vizuální jazyk jako zbytek appky.
- Malá `elevation = 2.dp` na chip, místo jedné velké `6.dp` elevace na celý blok.

Ověřeno vizuálně (screenshot na emulátoru) — chipy teď čitelně oddělené od search baru, aktivní
stav jednoznačný i letmým pohledem.

### 1.3 Nová položka: tlačítko "Přidat místo" i na hlavní obrazovce (Mapa)

**Podnět uživatele:** "hele dobry, jen by se teda hodilo tlacitko na pridani mista i na hlavni
stranku (mapu)"

Doteď šlo přidat místo jen přes Více → "Přidat místo". Přidáno:
- **Malé sekundární FAB** ("+", `SmallFloatingActionButton`, `secondaryContainer`/
  `onSecondaryContainer` — schválně tišší barva než `brandButton`, aby nekonkurovalo primárnímu
  tlačítku polohy) naskládané nad stávající FAB pro vycentrování polohy, obě ve společném
  `Column` zarovnaném `BottomEnd`.
- Stejná login-gate logika jako v `MoreScreen`: přihlášen → rovnou `onAddPlace()`, nepřihlášen →
  `LoginDialog`.
- `LoginDialog` byl doteď `private` uvnitř `MoreScreen.kt` → vytažen do sdíleného
  `ui/components/LoginDialog.kt`, `MoreScreen` teď ho jen importuje. DRY, jedna definice textu
  přihlašovacího dialogu pro celou appku.
- `MapScreen(onAddPlace: () -> Unit = {})` nový parametr, `MainScreen.kt` ho zapojuje na
  `Destinations.AddPlace`.

Ověřeno naživo na emulátoru: tap na "+" v nepřihlášeném stavu → dialog "Přihlásit se" s texty
"Zrušit"/"Pokračovat" (screenshot). Instalováno na emulátor i Honor.

**Změněné/nové soubory dnešní relace:**
- `ui/map/LocationPickerMap.kt` — touch listener fix
- `ui/screens/MapScreen.kt` — chip redesign + druhé FAB + auth state + login dialog
- `ui/screens/MoreScreen.kt` — `LoginDialog` vytažen ven, drobný import cleanup
- `ui/components/LoginDialog.kt` — nový sdílený soubor
- `ui/screens/MainScreen.kt` — `onAddPlace` prop na `MapScreen`

`./gradlew :app:assembleDebug` proběhlo čistě po každé změně; instalováno a vizuálně ověřeno na
emulátoru (`emulator-5554`) i na fyzickém zařízení (`ALDE6R2B11004838`, Honor).

---

## 2. Širší kontext — co appka dostala v předchozích kolech (pro orientaci)

Rychlá rekapitulace, aby dokument dával smysl i bez čtení celého repa:

- **Auth** — token-based login s PKCE (RFC 8252): `AuthRepository`, `AuthTokenHolder`,
  `SecureTokenStore` (ruční AES-256-GCM přes `AndroidKeyStore`, protože
  `androidx.security:security-crypto` není v offline Gradle cache), `AuthInterceptor` (Bearer +
  401 handling + `X-Refreshed-Token` sliding renewal). App Link
  `https://euroklic.odjezdy.online/app/auth-callback` (`autoVerify=true`) + custom-scheme fallback
  `euroklicmapa://auth-callback`. Login jde přes sdílenou hostovanou stránku `/app-login.php`
  (appka nehardkóduje seznam providerů).
- **Add place** — `AddPlaceScreen`/VM/`AddPlaceRepository`, poloha přes "posuň mapu pod pinem"
  (`LocationPickerMap`), foto kamera/galerie + klientský downscale (1800px, JPEG q82),
  multipart `POST /api_add.php`.
- **Admin queue** — `AdminQueueScreen`/VM/`AdminRepository`, `GET /api_admin_list.php` +
  `POST /api_admin.php` (approve/reject), viditelné jen pro `me.is_admin`.
- **Bezpečnostní hardening (Tier 1 + Tier 2, schváleno uživatelem najednou)** — App Link,
  šifrované úložiště tokenu + vyloučení ze zálohy, rate limit na `api_add.php` (backend), PKCE,
  token TTL se sliding renewal.
- **Brand Book** — 22kapitolový design-systém artefakt, appka z něj čerpala (barvy/typo/marker
  grammar už v appce existovaly, brand book je psal zpětně z `Color.kt`/`Type.kt`), web ho
  dostal jako zadání pro redesign.
- **More screen declutter** — disclaimer/"co je Euroklíč"/NRZP karty přesunuty do nové
  `AboutScreen`, nalezena a opravena skrytá duplicita (3 odkazy mířící na stejnou URL).
- **Fine location** (2026-09-05, dřívější požadavek na Honoru) — appka žádá
  `ACCESS_FINE_LOCATION` i `ACCESS_COARSE_LOCATION` současně, `LocationRepository` honoruje
  kterékoli udělené oprávnění.

---

## 3. Komunikace s backend/web session — vybrané úryvky

Skutečné citace z `SendMessage` výměn (kanál `bridge:session_01JfPwtkLCNxmrLozteG8bM9`),
seřazené chronologicky podle tématu. Krácené jen tam, kde šlo o opakování; význam beze změny.

### 3.1 Volba auth mechanismu (custom scheme vs. cookie-extrakce z Custom Tabs)

**Já → backend, k návrhu "vytáhnout cookie z Custom Tabs":**
> "Klíčový problém A: **Custom Tabs cookie NEJDE z appky přečíst.** Chrome Custom Tabs záměrně
> cookies hostitelské appce nevystavuje (bezpečnostní hranice)... Čili A neušetří 'token
> plumbing', jen přidá křehkost. B navíc: Custom scheme `euroklicmapa://auth-callback?token=...`
> = jen `<intent-filter>` v manifestu... Bearer token v OkHttp interceptoru, uložený v
> `EncryptedSharedPreferences` (Keystore-backed)."

Výsledek: varianta B (token přes deep link), později zpřísněná o App Link a PKCE (viz níž).

### 3.2 Bezpečnostní audit (`vibe-security` na obou stranách)

**Já → backend**, po tom, co uživatel řekl mít obavu, že login "nemá dostatečnou security":
> "1) **Token v deep-linku** ... Jakákoli appka na telefonu může scheme `euroklicmapa://`
> zaregistrovat a při redirectu token odchytit... **Návrh: PKCE-style výměna** — appka pošle
> `code_challenge=SHA256(verifier)`... callback vrátí krátkodobý jednorázový `code`...
> 2) **App Link místo custom scheme**... 3) **Token TTL**... 4) escapování `name`/`desc`
> (Discord embed injection!) ... 5) Rate limiting."

Backend odpověděl s vlastním auditem; konsolidace zpět uživateli:

**Já → backend, po obdržení backend-side auditu:**
> "**Tier 1 — udělat před reálnými uživateli:** 1. App Link ... 2. App-side úložiště
> (EncryptedSharedPreferences-ekvivalent) ... 3. Rate limit na api_add.php ...
> **Tier 2 — fast-follow:** 4. PKCE ... 5. Token TTL + sliding renewal ...
> 6. AllowedMentions.none() na botu."

Uživatel odpověděl (mimo bridge, přímo mně): **"všechno naráz, Tier 1 + 2."** — a tak jsem to
oznámil zpátky:

**Já → backend:**
> "Uživatel řekl: **všechno naráz, Tier 1 + 2.** Takže jedeme na App Link,
> EncryptedSharedPreferences-ekvivalent, backup exclude, rate limit, PKCE, token TTL.
> **Debug SHA-256 fingerprint** (pro assetlinks.json, testovací): `46:8F:11:03:6B:9D:...`"

O pár kroků později, po ověření naživo:

**Já → backend:**
> "Ověřeno na emulátoru... **Šifrování at-rest** — `files/datastore/auth.preferences_pb`
> obsahuje base64 ciphertext..., **ne** raw token... **Přežití restartu** — force-stop +
> relaunch → pořád přihlášen (decrypt-on-init). ✓ **Logout** → soubor 0 bajtů, token revokován.
> ✓ ... **Neověřeno na emulátoru:** `code→token` exchange (`/api_token.php`) — potřebuje reálný
> 60s OAuth `code`, otestuješ ty curl end-to-end."

### 3.3 Testovací tokeny a ověření naživo (auth → add-place → admin)

Poté co uživatel definitivně řekl **"celé, auth → add-place → admin"**, jsem postavil všechny tři
kroky dopředu a požádal backend o testovací Bearer tokeny (reálný OAuth na emulátoru nejde
otestovat). Výsledek ověření:

**Já → backend:**
> "Testovací tokeny fungovaly parádně — **všechny tři kroky ověřeny naživo na emulátoru:**
> - **Admin login** → karta 'Test Admin (appka) · administrátor', sekce MODERACE se objevila ✓
> - **Admin fronta** → `GET /api_admin_list.php` naparsováno (opravil jsem DTO na
>   `{"locations":[...]}` + `description`)... ✓
> - **Zamítnutí id=2691** ('TEST appka token - SMAZAT') → `POST /api_admin.php action=reject`
>   prošlo... ✓
> - **Non-admin login** → karta 'Test uživatel (appka)', žádná sekce Moderace ✓
> - **Add-place** naživo... vyfotil emulátorovou kamerou → downscale → multipart `photo_file` →
>   toast 'Místo odesláno ke schválení' + návrat ✓
> **Prosba o úklid:** ten add-place test vytvořil reálnou položku ve frontě —
> `TEST-appka-SMAZAT`... zamítni ze své strany, ať to nemusím dál lovit."

Vedlejší poznámka odsud (Discord bot mýlil "z appky" za "z Webu") jsem jen předal dál — čistě
backend/bot doména.

O kolo později se ukázalo, proč se admin nemohl znovu přihlásit stejným tokenem:

**Já → backend:**
> "Díky — a to vysvětluje ten záhadný re-login: **`/api_logout.php` mi ten admin token
> revokoval** (v testu jsem klikl 'Odhlásit'), a pak jsem se zkoušel přihlásit tím samým, už
> mrtvým tokenem... Není to emulátor ani bug, jen jsem recykloval revoknutý token. Logout funguje
> přesně jak má."

### 3.4 Brand Book handoff webu

**Já → backend**, po schválení Brand Book artefaktu uživatelem:
> "Uživatel chce, abys viděl nový brand look a **implementoval ho na webu**...
> **Zadání od uživatele:** web má být **vizuálně 1:1 jako mobilní appka** (stejné Material tokeny)
> ... **Jen desktop layout je jiný** (mapa + trvalý levý sidebar + nav rail místo spodních listů),
> ale pořád Material a pořád ty samé tokeny. **Kanonický zdroj tokenů** = `ui/theme/Color.kt` +
> `Type.kt`..."
(následoval kompletní výpis hex hodnot, typografické škály, radiusů, marker gramatiky — viz
Brand Book artefakt pro plné znění).

Uživatel následně upřesnil rozsah/tempo, přeposláno:

**Já → backend:**
> "Uživatel rozhodl: **dodělat celý web lokálně a nasadit jako jednu velkou změnu**, až bude vše
> hotové a otestované... Sidebar + nav rail na desktopu... je součást téhle vlny, ne odložená
> fáze."

### 3.5 Cutover flip-flop u `/app-login.php` (reálný příklad "ověřit s uživatelem přímo")

Tohle je dobrý příklad vzorce, který se v týhle spolupráci opakoval — žádná strana neimplementuje
na základě toho, co druhá strana *tvrdí*, že uživatel chce, bez přímého ověření:

**Já → backend** (poté co jsem appku přepnul na sdílenou login page, protože byla nasazená na
stagingu):
> "**Problém:** `/app-login.php` je zatím jen na `euroklic-split` stagingu, ne na produkci...
> Šlo by ji nasadit na produkci **hned, samostatně**, nezávisle na zbytku redesignu?"

**Backend odpověď** (parafrázovaně v mém navazujícím zprávě, protože backend session si to přímo
ověřila s uživatelem): trvat na jednom velkém deploy, žádné dílčí nasazení.

**Já → backend:**
> "Rozumím, žádné samostatné nasazování. Appka je vrácená zpátky na přímý `/auth.php` +
> `/auth_google.php` flow... Kód pro `/app-login.php` variantu jsem nechal zdokumentovaný v
> docstringu `buildLoginUri()` a v CLAUDE.md, ať je jasné, na co se přepnout, až dáš vědět, že
> jde velký deploy."

Později, po skutečném produkčním nasazení celého redesignu:

**Já → backend:**
> "Appka přepnuta zpátky na `buildLoginUri()` (bez providera) → `/app-login.php`... ověřil jsem
> to naživo na emulátoru proti produkci: appka otevřela
> `https://euroklic.odjezdy.online/app-login.php?client=app&state=…`, stránka se vykreslila
> správně... žádný 404."

### 3.6 Sjednocená login page + odložený QR cross-device login

**Já → backend**, po tom co uživatel navrhl "jedna login page s Discord/Google/(později GitHub)":
> "Návrh — hostovaná login page: ... `GET /app-login.php?client=app&state=<nonce>&code_challenge=
> <S256>&code_challenge_method=S256`... Proč to takhle: Přidání GitHub (nebo čehokoli dalšího) =
> **čistě backend**, appka se nemusí měnit ani rebuildovat...
> **Druhá věc, výslovně jako nejnižší priorita — QR cross-device login**... Uživatel s tím
> souhlasí, ale řekl explicitně 'až úplně nakonec' — po login page a po zbytku redesignu."

### 3.7 More-screen dedup nález, přeposlaný jako tip webu

**Já → backend**, po nálezu skryté duplicity (3 odkazy na stejnou URL v `MoreScreen`):
> "Mimochodem — koukni, jestli váš web nemá tu samou skrytou duplicitu (3 odkazy na jednu URL) v
> patičce/menu, když už jsem na to narazil v appce."

### 3.8 Relay TODO-IDEAS.md od jiného AI nástroje

Uživatel přidal do repa tři `.md` soubory vygenerované jiným nástrojem (GLM/z-ai/glm-5.3-flash v
Android Studiu) a požádal o přeposlání druhé session:

**Já → backend:**
> "Uživatel přidal do repa tři nové .md soubory (vygenerované jiným AI nástrojem...) a požádal mě
> je přeposlat. Beru je jako podnět k diskusi, ne jako hotová zadání — posílám ať to vidíš, žádnou
> z položek jsem sám nezačal implementovat."
(následoval plný text `TODO-IDEAS.md`, sekce B — konkrétní návrhy pro backend: ETag, foto k
existujícímu místu, anti-spam pro komentáře, SEO, "WC podél trasy" nápad.)

### 3.9 Poslední stav — ETag (otevřená položka, čeká na backend)

**Já → backend** (check-in bez čekání na uživatele, appka byla v klidu):
> "Ahoj, jen se hlásím sám od sebe — jak jde ta přestavba na `@material/web`?... Appka je teď v
> klidovém stavu, takže mám prostor reagovat rovnou, ne až přes uživatele."

Backend odpověděl se seznamem priorit (ETag, JSON-LD/SEO, …):

**Já → backend, poslední zpráva v tomhle vlákně:**
> "Upřímně: nic z toho seznamu appka teď akutně nepotřebuje ani na nic neblokuje. Postupuj podle
> svojí priority (ETag + JSON-LD teď). Jedna věc k ETagu, ať to nevyjde naprázdno: appka dnes
> **posílá požadavky bez podmíněnosti** — žádný `If-None-Match` se neposílá, žádná logika na
> 304... Je to malá věc, ale je to nutná druhá půlka toho winu... dej mi vědět, až bude nasazený a
> v jakém tvaru (hlavička `ETag`, `304` chování), ať přidám tu app-side polovinu."

**Tohle je aktuálně otevřený bod** — čeká se na backend, až ETag nasadí a řekne přesný tvar
hlaviček; teprve pak přijde app-side polovina (uložit ETag do `sync_metadata`, poslat
`If-None-Match`, na 304 přeskočit parse/insert).

---

## 3.10 Dodatek (2026-09-05, později týž den)

Dvě navazující věci po vygenerování tohoto dokumentu:

**Přidat fotku k existujícímu místu (TODO-IDEAS B2).** App-side postavené dopředu:
`DetailScreen.WcBody` → řádek „Přidat fotku" / „Navrhnout jinou fotku" (gated na login),
sdílený `ui/components/rememberImagePicker` (používá ho teď i `AddPlaceScreen`),
`DetailViewModel.uploadPhoto` → `AddPlaceRepository.submitPhoto` → `EuroklicApi.addPhoto`
(`@Multipart POST api_add_photo.php`). Endpoint zatím neexistuje → volání spadne s jasnou
hláškou „Přidávání fotek zatím není na serveru dostupné" (mapuju `SerializationException` z
homepage-HTML i 404). Backend session dostala přesný návrh kontraktu (Bearer, multipart
`id` + `photo_file`, do moderační fronty, `ApiResult` tvar).

**ETag na feedech — hotové.** Backend nasadil `ETag` + `Cache-Control: no-cache` na
`/api_locations.php` a `/api_pickup_points.php` (ETag = md5(data signature + raw query string),
takže per-`near=`/`bbox=` varianta má vlastní). App-side vyřešeno přidáním OkHttp disk `Cache`
(`cacheDir/http_cache`, 20 MB) na sdílený klient — každý `refresh*()` je teď podmíněný GET
(`If-None-Match`), na `304` OkHttp přehraje uložené tělo → repo dál parsuje plný
`FeatureCollection`, ale po síti nepřišlo nic. Ověřeno na emulátoru: druhý start appky poslal
`If-None-Match: "e3bdc73…-gzip"`, dostal 304, `.1` body soubory v cache se nezměnily (mtime i
velikost), mapa se vykreslila identicky. Klíč cache = celá URL včetně query stringu, takže
per-varianta ETagy sedí samy. Micro-opt „na 304 přeskočit i Room clear+reinsert" jsem NEudělal
— vyžadovalo by `Response<T>` plumbing + per-URL ETag tabulku a nestojí to za to; úspora dat po
síti je pointa.

---

## 4. Otevřené položky (napříč oběma stranami, stav k 2026-09-05)

- ~~**ETag na feedech**~~ — **hotové** obě strany (viz §3.10).
- **Backend endpoint `POST /api_add_photo.php`** (foto k existujícímu místu, TODO-IDEAS B2) —
  app-side hotové a čeká, backend session má návrh kontraktu.
- **`POST /api_account_delete.php`** (GDPR self-service smazání účtu) — identifikováno jako
  blokující pro Play Store submit, produktové rozhodnutí "smazat vs. anonymizovat" je na
  backendu; app-side práce (řádek "Smazat účet" v More) je malá a čeká na endpoint.
- **Release keystore + assetlinks fingerprint** — jen debug fingerprint zatím poslán; release
  přijde s prvním skutečným buildem ke zveřejnění.
- **`targetSdk` 37 → stabilní** — nutné před jakýmkoli Play submitem (37 je dnes preview).
- **Reálná launcher ikona** — uživatel řekl zatím ignorovat.
- **QR cross-device login** — explicitně nejnižší priorita, "až úplně nakonec", a uživatel chce
  osobně otestovat, než to bude považováno za hotové.
- **`submitted_via` štítek v admin frontě** ("z appky" vs. "z webu") — nízká priorita, poznamenáno,
  nezačato.
- Zbytek `TODO-IDEAS.md` sekce A (komentáře, statistiky, testy, crash reporting, …) — bez
  přiřazené priority, uživatel řekl "nechat zatím".

---

*Dokument vygenerován touto (app-side) Claude Code session na žádost uživatele
("napiš podrobny .md dokument s tim co jsi zmenil, udelal, a i zaroven kousky konverzace s druhou
session"). Citace v sekci 3 jsou doslovné výňatky z `SendMessage` přenosu této konverzace, ne
rekonstrukce z paměti.*
