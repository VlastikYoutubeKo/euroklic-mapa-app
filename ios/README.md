# Euroklíč Mapa pro iOS

Nativní SwiftUI aplikace (iOS 17+), žádné third-party závislosti (jen Apple SDK).
Instalace pro uživatele: [docs/ios-sidestore-navod.md](../docs/ios-sidestore-navod.md).

## Rozsah

| Funkce | Stav |
|---|---|
| Mapa (Mapy.com dlaždice nebo Apple Maps, volba v Více → Mapa) | ✅ |
| **Nejbližší WC** (poloha + navigace), seznam a filtry Vše/Toalety/Výdejní | ✅ |
| Hledání adresy na mapě | ✅ |
| Detail jako karta nad mapou: status, otevírací doba, plán stanice, odjezdy z nejbližších zastávek | ✅ |
| Hlasování 👍/👎 (CSRF + cookies) | ✅ (hlas se pamatuje v telefonu) |
| Komentáře: čtení i psaní, fotka k místu, nahlášení problému (jde do moderace) | ✅ |
| Přihlášení PKCE (ASWebAuthenticationSession + Keychain), avatar a jméno | ✅ |
| Přidání místa (formulář + pin + volitelné foto) | ✅ |
| Admin fronta (místa, fotky, komentáře, hlášení) | ✅ |
| Oblíbené | ✅ |
| **Režim Odjezdy** (veřejná doprava): mapa vozidel, zastávky, hledání, oblíbené, aktuality | ✅ (přepínač v Více → Režim aplikace; [docs/ios-odjezdy-rezim.md](../docs/ios-odjezdy-rezim.md)) |
| Widgety: Nejbližší WC, Odjezdy poblíž (Plocha i Zámek) | ✅ |
| Čeština a angličtina podle jazyka telefonu (výchozí angličtina) | ✅ |
| „Co je nového“ po aktualizaci (z `CHANGELOG*.md`) | ✅ |
| Push notifikace | ❌ nejde: vyžadují placený Apple Developer účet (APNs) |

## Build na Macu

0. Mapy.com klíč (není v repu): `cp Secrets.xcconfig.example Secrets.xcconfig` a vlož klíč z <https://developer.mapy.com>.
   Bez klíče se appka sestaví, mapa použije Apple Maps.
1. `brew install xcodegen`
2. `cd ios && xcodegen generate && open EuroklicMapa.xcodeproj`
3. Signing & Capabilities → osobní Team (stačí bezplatné Apple ID, na zařízení běží 7 dní).
4. Run ▶︎.

Unsigned `.ipa` staví GitHub Actions (`.github/workflows/ios.yml`) a připojí ho k release s tagem `v*`.
Release notes jsou sekce z `CHANGELOG.md` (EN) a `CHANGELOG.cs.md` (CS), obě se balí i do appky.

## Lokalizace

- Klíč v `Localizable.strings` je **český zdrojový text**; `en.lproj` obsahuje angličtinu, `cs.lproj` je identita
  (musí obsahovat každý klíč, protože vývojový jazyk je `en` a chybějící klíč by se jinak ukázal anglicky).
- Řetězce mimo SwiftUI (`String`) se obalují `L("…")` (`Util/Localization.swift`, kopie je i ve `EuroklicWidgets/`).
- `appIsCzech` řídí množná čísla, desetinný oddělovač a výběr changelogu.
- Přidáš-li český text, přidej klíč do obou tabulek (`en.lproj` s překladem, `cs.lproj` jako identitu).

## Bezpečnost

- Bearer token je v **Keychainu**; PHP session cookie drží `HTTPCookieStorage` (nutné pro CSRF u hlasování).
- Přihlášení jde přes hostovanou stránku `/app-login.php` s **PKCE** (S256), callback `euroklicmapa://`.
- Mapy.com klíč se injektuje při buildu (`Secrets.xcconfig`), v repu není. Klíč v klientské appce je ale vždy
  extrahovatelný, omez ho v [Mapy.com pro vývojáře](https://developer.mapy.com/) na produkt.

## Rozdíly oproti Androidu

- Clustering: iOS používá vestavěný `MKMarkerAnnotationView`, Android má vlastní grid clustering pod z13.5.
- Ring/hollow stav markerů je zjednodušen na barvu + glyph; textové badge jsou 1:1 s webem.
- Cache feedů je JSON v Documents (Android má Room); ETag/304 řeší systémový `URLCache`.
- iOS má navíc režim Odjezdy, widgety, angličtinu a „Co je nového“; Android má navíc lokální notifikace.
