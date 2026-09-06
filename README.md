# Euroklíč Mapa

> *„Kde je nejbližší vhodná toaleta?"* — mapa bezbariérových WC otevíratelných
> Euroklíčem v ČR a SR, plus výdejní místa klíče. Nativní klient veřejného webu
> [euroklic.odjezdy.online](https://euroklic.odjezdy.online) (PHP + SQLite),
> jehož backend je pouze konzumován, nikdy neměněn.

Cíloví uživatelé: lidé s omezenou mobilitou, senioři, rodiče s kočárky.
**Přístupnost je vstupní podmínka, ne funkce** (WCAG AA, 48 dp cíle, TalkBack/VoiceOver).

## Struktura repa

| Cesta | Co je uvnitř |
|---|---|
| `app/` | **Android app** — Kotlin, Jetpack Compose, MVVM, Room, osmdroid (hlavní, produkční) |
| `ios/` | **iOS app (SLC)** — SwiftUI + MapKit, zero závislostí; generuje se přes XcodeGen |
| `docs/` | Backend kontrakty, session reporty, explorace půdorysů stanic |
| `CLAUDE.md` | Autoritní provozní příručka pro AI session i lidi (API pravidla, stavy markerů) |

## Android

```bash
./gradlew.bat :app:assembleDebug      # Windows
./gradlew :app:assembleDebug          # macOS/Linux
./gradlew :app:testDebugUnitTest
```

Detaily architektury: [CLAUDE.md](CLAUDE.md).

## iOS (SLC — Simple, Lovable, Complete)

Kotlin/Swift cross-compile neexistuje, takže iOS verze je nativní SwiftUI přepis
jádra appky (mapa s Mapy.com dlaždicemi, „Nejbližší WC", hlasování, komentáře,
oblíbené, přidání místa, přihlášení PKCE). Zero závislostí — jen Apple SDK.

```bash
# na Macu (Xcode 15+):
brew install xcodegen
cd ios && xcodegen generate
open EuroklicMapa.xcodeproj   # vybrat osobní team, spustit na sim/zařízení
```

Podrobnosti a rozsah: [ios/README.md](ios/README.md).

## Licence

[MIT](LICENSE) · Mapové dlaždice © Seznam.cz a.s. a další (povinná atribuce).

## Klíče (nejsou v repu)

Mapy.com tile klíč se injektuje při buildu, nikdy není v gitu:

| Platforma | Kde | Kdy se načte |
|---|---|---|
| Android | `local.properties` → `MAPY_APIKEY=…` (nebo env `MAPY_APIKEY`) | `BuildConfig.MAPY_APIKEY` při buildu |
| iOS | `ios/Secrets.xcconfig` (zkopíruj z `.example`) → `MAPY_APIKEY` | Info.plist `MapyApiKey` při buildu |

Klíč vytvoříš na [developer.mapy.com](https://developer.mapy.com/).
