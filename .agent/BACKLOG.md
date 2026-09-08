# BACKLOG — co zbývá dodělat, aby appka i web fungovaly správně

> Pro Claude session (app-side i web-side). Vzniklo 2026-09-06 na žádost uživatele.
> Toto je **akční seznam**, ne nápady — nápady zůstávají v [TODO-IDEAS.md](TODO-IDEAS.md).
> Formát: `[owner]` = kdo to bere (backend / web / app-android / app-ios).
> Každá položka má acceptance criteria — „hotovo" znamená, že je splněná.
>
> Aktualizuj zaškrtávání přímo tady, ať je to single source of truth.

---

## 0. Fotky — kde jsme, ať se to nezkoumá znovu

Už **hotové a nasazené** (netýkej se toho znovu):
- ✅ `POST /api_add_photo.php` — live, 403 bez Bearer, multipart `id` + `photo_file`,
  ukládá do `photo_suggestions` (`status='pending'`), rate limit 10/h/user
- ✅ App Android: řádek „Přidat fotku / Navrhnout jinou fotku" v detailu (login-gated),
  sdílený `rememberImagePicker`, downscale 1800 px / q82, multipart upload,
  čistá hláška když endpoint není dostupný
- ✅ App Android: **admin fronta má sekci „Návrhy fotek"** — `PhotoSuggestionCard`
  s náhledem, autorem, souřadnicemi místa + Schválit/Odmítnout
  (`api_admin.php action=approve_photo|reject_photo` s `photo_id`)
- ✅ iOS: přidání fotky přímo v „Přidat místo" formuláři (PhotosPicker + downscale)

Zbývá k funkční smyčce → sekce 1.

---

## 1. FOTKY — dokončení end-to-end smyčky

### 1.1 [app-android] End-to-end test na reálném zařízení — **nejdřív tohle**
- [ ] Na Honoru: detail WC → „Přidat fotku" → vyfotit → odeslat
- [ ] Admin login → fronta → „Návrhy fotek" → Schválit
      (na Honoru se přihlásit reálným OAuth přes `/app-login.php`, nebo požádat
      backend o test token — na emulátoru se admin testoval tokenem)
- [ ] Po schválení: `photo_url` se objeví ve feedu `api_locations.php` pro to místo
      (kontrola cURL-em), Hero v detailu fotku zobrazí
- **Hotovo =** fotka je vidět v appce i po restartu; místo má v admin frontě před tím nulu fotek
- Pozn.: na emulátoru už create-flow prošel; teprve teď existuje živý endpoint

### 1.2 [web] Zobrazit fotku místa na webu — ✅ HOTOVO (2026-09-08, backend session)
- [x] Detail = tentýž popup jako na mapě (SPA URL routing). Fotka `loading="lazy"`,
      proklik na plnou velikost (`target=_blank`), placeholder „📷 Fotka zatím není
      k dispozici" když `photo_url` null/"". `index.php:611-627`, `.popup-photo-placeholder`.
- [x] Map popup náhled — tímtéž řešením.

### 1.3 [web] Moderace fotek na webu pro moderátory bez appky — ✅ HOTOVO (2026-09-08, backend session)
- [x] Ne webhook — `bot.py` persistentní polling (vzor nových míst): sloupec
      `photo_suggestions.posted_to_discord` (migrace) + `check_new_photo_suggestions()`
      (10 s poll) + `PhotoApprovalView` (Schválit/Zamítnout přímo z Discordu, píše do
      stejných tabulek jako `api_admin.php` approve_photo/reject_photo). Ověřeno živě
      (QA fotka → `posted_to_discord=1` do 10 s).

### 1.4 [app-ios] Fotka v detailu (Hero)
- [ ] `DetailView` zatím `photoUrl` vůbec nezobrazuje — přidat hero/ASyncImage
      nad nadpis, `isNullOrBlank` handling (stejně jako Android `DetailScreen.Hero`)
- **Hotovo =** místo se schválenou fotkou ukazuje fotku i na iOS

### 1.5 [backend] Hygiena fotek — ✅ HOTOVO (2026-09-08, backend session)
- [x] `reject_photo` v `api_admin.php` už soubor z disku mazal (bylo hotové dřív).
- [x] Všechny chybové odpovědi `api_add_photo.php` teď nesou `message` navíc k `error`
      (stejný text) → `resp.error ?: resp.message` v appce funguje.

### 1.6 [backend+web+app] Až fotky budou reálné
- [x] `og:image` per místo — `index.php` už bere `photo_url` jako og:image fallback
      (backend session, potvrzeno 2026-09-08).

---

## 2. NOTIFIKACE — adminy + uživatelé (nový scope, 2026-09-06 uživatel schválil)

> CLAUDE.md tvrdé omezení „no notifications" je tímto **překonáno uživatelem** —
> aktualizuj ho v rámci první notifikační změny.

### 2.1 [app-android+app-ios] FÁZE 1 — lokální notifikace bez backendu (doporučeno hned)
Zero infrastruktury, žádné tokeny, funguje hned:
- [ ] **Přidat závislost** `androidx.work:work-runtime-ktx` (WorkManager zatím
      v projektu vůbec není!) + `androidx.hilt:hilt-work` NE — ruční factory
- [ ] **`POST_NOTIFICATIONS` runtime permission** (Android 13+) — žádat až při
      prvním opt-in, ne při startu (stejná filozofie jako poloha: lazily + rationale)
- [ ] **Admin „fronta čeká"**: `WorkManager` periodic (15 min), když existuje Bearer token s `is_admin=true` → poll
      `api_admin_list.php` → pokud `count + photo_count` > 0 → notifikace
      „X míst a Y fotek čeká na schválení" (tap → AdminQueue). Dedup: uložit
      poslední známé počty.
- [ ] **„Nové místo v okolí"**: po každém feed refreshi diff id oproti poslednímu
      známému snapshotu; nová id do ~10 km od poslední polohy → notifikace
      (max 1/den, opt-in přepínač ve Více, default zapnuté jen pro přihlášené)
- [ ] Kanály (Android): „Moderace" (high, admin only), „Nové v okolí" (default),
      „Stav oblíbených" (low)
- [ ] **OEM poznámka pro uživatele** (Honor/EMUI/Samsung killují background work):
      v sekci Více krátká karta „Notifikace mi přestaly chodit?" → návod vypnout
      optimalizaci baterie pro appku (nebo appku připnout) — jinak to bude vypadat
      jako bug appky
- **Hotovo =** admin bez otevřené appky se dozví o novém místě do 15 minut;
      běžný uživatel dostane max 1 notifikaci denně o novinkách v okolí
- Omezení (říct uživateli): Doze/Battery saver může 15 min protáhnout na ~hodinu;
      okamžitost až FCM (fáze 2)

### 2.2 [backend] FÁZE 2 — FCM infrastruktura (až před Play submitem)
- [ ] Tabulka `push_tokens` (`user_id`, `platform`, `token`, `updated_at`, unique token)
- [ ] `POST /api_push_token.php` (Bearer) — registrace/obnova; `DELETE` při logoutu
- [ ] Triggery server-side:
      - **adminy** (`is_admin` tokeny): nové místo ve frontě, nový návrh fotky,
        nový komentář, report „nefunguje" (zvotež throttling — batch 1/hod)
      - **uživatele**: „nové místo v okolí" jen pokud jsme si uložili jejich hrubou
        polohu (souhlas!) — nebo krok 1 nechat lokálně a FCM jen pro adminy
      - (volitelně) „oblíbené místo nahlášeno nefunguje" — high value, low volume
- [ ] Neposílat nikomu nic bez explicitního opt-in flagu v user prefs
- **Hotovo =** admin dostane push do 1 minuty od submitu; odhlášený token se nepoužívá

### 2.3 [app-android] FCM klient (fáze 2)
- [ ] `firebase-messaging` dep, token → `/api_push_token.php` po loginu + při rotaci
- [ ] Notifikace kanály jako 2.1, tap-routing na Detail/AdminQueue

### 2.4 [app-ios] APNs klient (fáze 2)
- [ ] APNs entitlelement + token registrace; pozor — bez placeného dev účtu APNs
      nefunguje → fáze 2 až s účtem

### 2.5 [web] Notifikace na webu
- [ ] Minimálně: admin badge „N čeká" v UI webu (poll jako 2.1)
- [ ] (volitelně) Web Push pro adminy — hm, až když bude poptávka

---

## 3. OSTATNÍ — bložáky správného fungování

### 3.1 GDPR smazání účtu — **blokující pro Play**
- [x] [backend] `POST /api_account_delete.php` (Bearer, vzor `api_logout.php`) — **LIVE
      2026-09-08.** PLNÁ anonymizace: maže `author_name` **i** `discord_user_id`
      z `locations`/`photo_suggestions` (řádky zůstávají — veřejný obsah mapy), tvrdě
      maže `api_tokens`/`auth_codes`/consent. Token po volání neplatí. Response má
      `message` pole. Otestováno živě throwaway účtem.
- [ ] [app-android] „Smazat účet a data" v `AuthCard` (`MoreScreen`) s potvrzovacím
      dialogem → `EuroklicApi` nový `POST api_account_delete.php` (Bearer) →
      `AuthRepository.deleteAccount()` (po úspěchu lokální `logout()` cleanup).
- [ ] [app-ios] totéž v `AuthSession` + settings řádek.
- **Hotovo =** uživatel si smaže účet z appky, jméno u přispěných míst zmizí.

### 3.2 [app-android] Před Play submitem
- [ ] `targetSdk` 37 preview → stabilní (dnes blokátor)
- [ ] Release keystore + fingerprint do `assetlinks.json`
- [ ] **Release minifikace**: v `app/build.gradle.kts` je teď `optimization.enable = false`
      — zapnout R8 (fullMode), ověřit `app/src/main/keepRules/rules.keep`
      (kotlinx.serialization DTOs, Retrofit generika), otestovat release build
      na reálném zařízení
- [ ] **versionCode/versionName stratégia** (dnes 1/1.0 — před Play nastavit
      versionCode schéma, ať první upload není 1)
- [ ] Privacy policy URL (web stránka) + Play Data Safety formulář
      (coarse+fine poloha, auth identita, fotky)
- [ ] **Crash reporting — rozhodnout** (Sentry self-hosted vs. nic; Firebase
      Crashlytics kvůli soukromí cílové skupiny raději ne). Rozhodnutí zapsat
      do CLAUDE.md. **Analytics: žádné** (záměr — zapsat taky, ať to nikdo
      „nevylepší" sám)
- [ ] Reálná launcher ikona (uživatel řekl „ignorovat zatím" — ale pro Play nutná)

### 3.3 [web] SEO doladění (detaily v TODO-IDEAS B8)
- [ ] JSON-LD `Place` na `/lokace/*`, detaily v sitemap, `FAQPage`, og:image (1.6)
- **Hotovo =** Google Rich Results Test na detailu bez errorů

### 3.4 [app-android] Komentáře write — až backend dá endpoint
- [ ] Bearer POST + anti-spam vrstvy z TODO-IDEAS B3 (login povinný, limity, fronta)
- **Hotovo =** komentář z appky projde moderační frontou jako na webu

### 3.5 [app-ios] První build + test na Macu (uživatel chce koupit/zařídit Mac)
- [ ] `xcodegen generate` → očekávej 5–15 syntax fixů (kód nikdy neprošel kompilátorem)
- [ ] Test na reálném iPhonu: auth callback scheme, fotky, poloha, klávesnice ve formech
- [ ] Poslat error log Claudeovi → doladit

### 3.6 [backend] Mapy klíč rotovat (byl krátce public)
- [ ] Nový klíč v developer.mapy.com, omezit na produkty; starý smazat
- [ ] Update u uživatele: `local.properties` + `ios/Secrets.xcconfig`

### 3.7 [web] „Vyžádat ověření" / data-quality smyčka (TODO-IDEAS A11)
- [ ] Reported místa → fronta „potřebuje ověřit"; app: jemný náznak když je
      uživatel blízko reported místa
- **Hotovo =** každé „nefunguje" hlášení má do 30 dnů re-check od komunity

### 3.8 [backend] `submitted_via` štítek (z appky / z webu) v admin frontě — nízká priorita

### 3.9 [app-android+web] Půdorysy stanic — explorace (`docs/station-floorplans-*`) — nízko

### 3.10 [app-ios] Paritní díry po SLC (všechno nízké, ale zapsat)
- [ ] **Geokódované vyhledávání na mapě chybí** (Android má Nominatim search —
      iOS MapScreen zatím žádné search pole); přidat `NominatimSearch`
      (vlastní URLSession client, `countrycodes=cz,sk`, User-Agent)
- [ ] **App icon + asset catalog** — project.yml nemá `Assets.xcassets`; bez ikony
      vypadá instalace rozbitě; launch screen má jen prázdný `UILaunchScreen`
- [ ] Hlasování dedup hláška „Už jste takto hlasovali." — ověřit, že se zobrazuje
      (web text, přichází v `message`)
- [ ] AdminQueue ekvivalent na iOS — zatím záměrně vynecháno (SLC); rozhodnout,
      jestli pro adminy na iPhonu doplnit, nebo navěky „web only"

### 3.11 [web] Komentáře na detail stránkách
- [ ] Ověřit: komentáře jsou dnes jen v map popupu — na `/lokace/{id}-{slug}`
      detailu je zobrazit taky (SEO + parita; read-only, write po B3)
- **Hotovo =** komentáře z 290 viditelné i na web detailu

---

## 4. Doporučené pořadí (když „co dneska?")

1. **1.1** fotka E2E na Honoru (uzavře celou fotkovou smyčku) — web strana (1.2/1.3) už live
2. **2.1** lokální notifikace (admin fronta + nové v okolí) — největší hodnota / nejnižší cena
3. **3.1 app-side** — „Smazat účet a data" v AuthCard (endpoint už LIVE, Play blokátor)
4. **2.2–2.4** FCM fáze 2 (před Play)
5. Zbytek dle kapacity

Web/backend HOTOVO 2026-09-08 (backend session): 1.2, 1.3, 1.5, 1.6 og:image,
3.1 endpoint. Nedělalo se: 2.5, 3.3 (index.php už má PublicToilet JSON-LD), 3.7, 3.8,
3.11, 2.2 — „až bude čas". §3.6 rotace Mapy klíče = na uživateli (přihlášení do
developer.mapy.com).
