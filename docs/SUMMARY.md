# Euroklíč Mapa — Shrnutí projektu

> Krátké shrnutí. Podrobný rozsáhlý dokument: [PROJECT-REPORT.md](PROJECT-REPORT.md)

*Autor: GLM (z-ai/glm-5.3-flash) — vygenerováno v Android Studio, 2026-09.*

## Co aplikace je
Nativní Android aplikace (Kotlin, Jetpack Compose) odpovídající na otázku
**„Kde je nejbližší vhodná toaleta?"** — mapa bezbariérových WC otevíratelných
Euroklíčem v ČR a SR, plus výdejní místa. Nativní protějšek webu
`euroklic.odjezdy.online`. Cíloví uživatelé: lidé s omezenou mobilitou, senioři,
rodiče s kočárky. **Přístupnost je vstupní podmínka, ne funkce.**

## Technologie
- **Stack:** Kotlin 2.2.10, Compose + Material 3, AGP 9.4.0, compile SDK 37, minSdk 24
- **Architektura:** MVVM se `StateFlow`, jediný modul `:app`, ruční DI (žádný Hilt)
- **Data:** Retrofit + kotlinx.serialization, Room (offline-first cache),
  OkHttp disk cache (podmíněné GETy / ETag), DataStore
- **Mapa:** osmdroid s rastrovými Mapy.com dlaždicemi
- **Navigace:** Jetpack Navigation 3 + `NavigationSuiteScaffold`

## Hlavní funkce
1. **Mapa** — clustering, filtr kategorií (Vše/Toalety/Výdejní), geokódované
   vyhledávání (Nominatim), trvalý spodní sheet se seznamem nejbližších míst
2. **Seznam** — řazení podle vzdálenosti, filtr kategorií, karty míst
3. **Detail** — status badge, otevírací doba, plán stanice, hlasování, navigace,
   min-mapa, oblíbené
4. **Hlasování** — anonymní (CSRF + cookie), zapisuje se zpět do Room
5. **Přihlášení** — OAuth přes hosted login stránku s **PKCE** (RFC 8252), token
   šifrovaný AES-256-GCM v AndroidKeyStore, auto-refresh přes 401
6. **Přidání místa** — formulář + foto (kamera/soubor, downscale na 1800 px)
   + výběr polohy mapou
7. **Admin moderace** — fronta ke schválení pro administrátory
8. **Oblíbené, motivy** (světlý/tmavý/systém), banner čerstvosti dat, Czech UI

## Stav (k 2026-09-05)
- `assembleDebug` i `testDebugUnitTest` procházejí; ověřeno na emulátoru
  i na reálném zařízení (Honor)
- Nové (2026-09-05): fine+coarse poloha, ETag cache feedů (podmíněné GETy,
  nic nepřijde po síti na 304), UI fixy (mapa v add-place, kategorie chipy,
  FAB „Přidat místo“ na mapě, sdílený `LoginDialog`), UI pro „přidat fotku
  k místu“ připravené a gated na login
- Zbývá: backend endpoint `POST /api_add_photo.php`, GDPR smazání účtu
  (blokující pro Play), release keystore + fingerprint, `targetSdk` 37
  preview → stabilní před Play
- Detaily: [app-session-report-2026-09-05.md](app-session-report-2026-09-05.md)
