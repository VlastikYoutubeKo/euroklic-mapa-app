# PROMPT PRO CLAUDE — pokračování práce na Euroklíč Mapa

> Tento soubor je vstupní bod pro Claude Code session pracující na tomto repu.
> Naposledy aktualizováno: 2026-09-06 (po pushi do GitHubu a injektáži klíčů).

## Přečti si, v tomhle pořadí (všechno je v repu)

1. **CLAUDE.md** (root) — autoritní příručka: API kontrakty, marker/status logika,
   tvrdá omezení, build příkazy. Když je v rozporu s ničím níže, vyhrává CLAUDE.md
   pro technický stav, BACKLOG pro prioritu.
2. **`.agent/BACKLOG.md`** — ⚡ AKTUÁLNÍ PRACOVNÍ SEZNAM s acceptance criteria.
   Vybírej odtud, podle pořadí v sekci „4. Doporučené pořadí".
3. `.agent/TODO-IDEAS.md` — jen dlouhodobé nápady (když dojdeš na konec BACKLOGu).
4. `docs/app-session-report-2026-09-05.md` — kontext posledního většího dění
   (auth/add-place/admin/notifikace souvislosti), včetně citací z komunikace
   s backend session.

## Tvrdá pravidla (porušení = chyba práce)

- **Backend nikdy neupravovat.** Jen konzumovat jeho API. Web-side úkoly
  (označené `[web]` / `[backend]` v BACKLOGu) **neprogramuj** — přepošli je
  backend session přes bridge kanál, jak to fungovalo doteď.
- **Klíče nikdy do gitu.** Mapy.com klíč: Android = `local.properties`
  (`MAPY_APIKEY=`), iOS = `ios/Secrets.xcconfig`. Obojí je gitignored —
  po `clone` si je musíš vytvořit, než budeš buildit/mapovat.
- Notifikace: omezení „no notifications" v CLAUDE.md je **překonáno uživatelem
  (2026-09-06)** — notifikace jsou nový scope (BACKLOG sekce 2). Po první
  notifikační změně aktualizuj omezení v CLAUDE.md.
- Přístupnost a parita se webem (statusy, badge texty) — nijak nevymýšlet
  vlastní prahy; logika statusů je zrcadlo webu (app `PlaceStatus.kt`,
  iOS `Place.swift`).

## Git / GitHub

- Repo: `https://github.com/VlastikYoutubeKo/euroklic-mapa-app` (branch `main`).
- Historie byla jednou přepsána force-pushe (odstranění klíče) — to už se
  neopakuje; normální commity + push.
- Commit zprávy česky nebo anglicky, stručně; žádné secrets v commitech.
- Když dokončíš položku z BACKLOGu: odškrtni `[x]` přímo v BACKLOG.md,
  commitni i s ním.

## Aktuální top-3 (dnes)

1. **BACKLOG 1.1** — fotka E2E na Honoru (`ALDE6R2B11004838`): upload fotky
   z detailu → admin fronta „Návrhy fotek" → schválit → `photo_url` ve feedu
   a v Hero detailu. (Endpoint `api_add_photo.php` je live, admin UI v appce hotové.)
2. **BACKLOG 2.1** — lokální notifikace bez backendu: WorkManager pro admina
   (fronta čeká, 15 min poll) + „nové místo v okolí" (diff cache, 1×/den,
   opt-in). Detaily a acceptance criteria v BACKLOG sekci 2.1.
3. **BACKLOG 1.2 + 1.3** — přeposlat web session: fotka na webu + Discord ping
   při návrhu fotky.

## Když se zasekneš

- Chyby API → nejdřív `CLAUDE.md` sekce „Backend API" (200 + `success:false`,
  HTML na chybějící cestě, `[lon, lat]` pořadí, atd.)
- Rozhlasuješ se mezi variantami → zeptej se uživatele přímo, nepředpokládej
  (vzorem je cutover `/app-login.php` v session reportu §3.5)
- Upravíš-li CLAUDE.md nebo BACKLOG, commitni je spolu s kódem.
