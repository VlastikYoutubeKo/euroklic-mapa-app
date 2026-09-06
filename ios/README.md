# Euroklíč Mapa pro iOS (SLC)

Nativní SwiftUI přepis jádra Android appky — **Simple, Lovable, Complete**:
kompletní jádro (najdi nejbližší WC), žádné napůl udělané funkce, žádné
third-party závislosti (jen Apple SDK → po vygenerování funguje napoprvé).

## Rozsah

| Funkce | Stav |
|---|---|
| Mapa (Mapy.com dlaždice + clustering) | ✅ |
| **Nejbližší WC** (hlavní akce, poloha + navigace) | ✅ |
| Seznam + filtry Vše/Toalety/Výdejní | ✅ |
| Detail (status, otevírací doba, plán stanice, web) | ✅ |
| Hlasování 👍/👎 (CSRF + cookies) | ✅ |
| Komentáře (read-only) | ✅ |
| Oblíbené (JSON v Documents) | ✅ |
| Přihlášení PKCE (ASWebAuthenticationSession + Keychain) | ✅ |
| Přidání místa (formulář + pin + volitelné foto) | ✅ |
| Statistiky / O projektu | ✅ |
| Admin moderace | ❌ desktop úkon — na telefonu neřešíme |
| Psaní komentářů | ❌ backend nemá bezpečný write endpoint |
| Notifikace / widgety | ❌ záměrně |

## Jak to zprovoznit na Macu

0. **Mapy.com klíč** (používá se při buildu, není v repu):
   ```bash
   cp Secrets.xcconfig.example Secrets.xcconfig
   # otevři Secrets.xcconfig a vlož klíč z https://developer.mapy.com
   ```
1. Nainstaluj Xcode 15+ a [XcodeGen](https://github.com/yonaskolb/XcodeGen):
   ```bash
   brew install xcodegen
   ```
2. Vygeneruj projekt a otevři:
   ```bash
   cd ios
   xcodegen generate
   open EuroklicMapa.xcodeproj
   ```
3. Signing & Capabilities → vyber svůj **Personal Team** (_stačí_ — bez
   placeného účtu běží na vlastním zařízení 7 dní, v simu navždy).
4. Run ▶︎ (simulator nebo iPhone).

Bez `Secrets.xcconfig` se appka sestaví taky, ale mapa padne na Apple
basemap (v konzoli je varování) — dlaždice Mapy.com se nenačtou.

## Bezpečnostní poznámky

- **Mapy.com API klíč je inline** ve `EuroklicMapa/Views/MapView.swift`
  (stejně jako v Android appce — klíč v klientské appce je vždy extrahovatelný).
  Omez ho v [Mapy.com pro vývojáře](https://developer.mapy.com/) na produkt, ať
  ho nikdo nezužitkuje na tvůj účet.
- Bearer token je v **Keychainu** (nikoli UserDefaults); PHP session cookie
  drží `HTTPCookieStorage` (nutné pro CSRF u hlasování).
- Přihlášení jde přes hostovanou stránku `/app-login.php` s **PKCE** (S256) —
  stejný flow jako Android; callback scheme `euroklicmapa://`.

## Rozdíly oproti Android verzi (záměrné, SLC)

- Clustering používá **vestavěný** `MKMarkerAnnotationView` (Android má vlastní
  grid clustering pod z13.5).
- „Ring/hollow“ stav markerů je zjednodušen na barvu + glyph (balónek
  `MKMarkerAnnotationView` neumí prstenec) — textové badge v detailu/seznamu
  jsou 1:1 s webem.
- Cache feedů je JSON soubor v Documents (Android má Room); ETag/304 řeší
  systémový `URLCache`.
