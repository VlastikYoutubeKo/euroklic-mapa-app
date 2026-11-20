# Euroklíč Mapa

> *„Kde je nejbližší vhodná toaleta?"* — mapa bezbariérových WC otevíratelných
> Euroklíčem v ČR a SR, plus výdejní místa klíče. Nativní klient veřejného webu
> [euroklic.odjezdy.online](https://euroklic.odjezdy.online) (PHP + SQLite),
> jehož backend je pouze konzumován, nikdy neměněn.

Cíloví uživatelé: lidé s omezenou mobilitou, senioři, rodiče s kočárky.
**Přístupnost je vstupní podmínka, ne funkce** (WCAG AA, 48 dp cíle, TalkBack/VoiceOver).

## Stažení

Aplikace **nejsou v Google Play ani v App Store** (zveřejnění v obchodech je placené a zatím se nevyplatí),
instalují se ze souboru. Vše je v [GitHub Releases](https://github.com/VlastikYoutubeKo/euroklic-mapa-app/releases/latest):

| Platforma | Soubor | Jak |
|---|---|---|
| Android 7.0+ | `EuroklicMapa.apk` (podepsané) | stáhnout a nainstalovat (povolit instalaci z neznámých zdrojů) |
| iOS 17+ | `EuroklicMapa.ipa` (nepodepsané) | přes SideStore / AltStore / Feather / LiveContainer, viz [návod](docs/ios-sidestore-navod.md) |

Stránka s odkazy a zdroji pro SideStore/AltStore: <https://euroklic.odjezdy.online/app>.
Co je nového v které verzi: [CHANGELOG.cs.md](CHANGELOG.cs.md) (česky), [CHANGELOG.md](CHANGELOG.md) (anglicky).

## Co aplikace umí

| | Android | iOS |
|---|---|---|
| Mapa Euroklíč WC + výdejních míst, „Nejbližší WC“ | ✅ | ✅ |
| Seznam, filtry, hledání adresy, oblíbené | ✅ | ✅ |
| Detail (status, otevírací doba, plán stanice, hlasování, komentáře) | ✅ | ✅ |
| Přihlášení (Discord/Google, PKCE), přidání místa s fotkou, komentář/fotka k místu, nahlášení problému | ✅ | ✅ |
| Admin fronta (schvalování) | ✅ | ✅ |
| Odjezdy: živá mapa vozidel, zastávky, odjezdy v detailu místa | částečně (odjezdy u místa) | ✅ plný režim |
| Widgety (nejbližší WC, odjezdy) | ❌ | ✅ |
| Notifikace | ✅ lokální (WorkManager) | ❌ |
| Čeština + angličtina (podle jazyka telefonu) | ❌ jen česky | ✅ |
| „Co je nového“ po aktualizaci | ❌ | ✅ |

## Struktura repa

| Cesta | Co je uvnitř |
|---|---|
| `app/` | **Android** — Kotlin, Jetpack Compose, MVVM, Room, osmdroid |
| `ios/` | **iOS** — SwiftUI + MapKit, bez závislostí; projekt generuje XcodeGen (`ios/project.yml`) |
| `.github/workflows/` | `ci.yml` (testy Androidu), `android.yml` (podepsané APK), `ios.yml` (nepodepsané IPA); tag `v*` vytvoří release s oběma soubory |
| `CHANGELOG.md`, `CHANGELOG.cs.md` | změny po verzích (EN/CS); sekce tagu se použije jako popis release a v iOS appce jako „Co je nového“ |
| `docs/` | backend kontrakty, session reporty, návod na SideStore |
| `CLAUDE.md` | provozní příručka pro AI session i lidi (API pravidla, stavy markerů) |

## Vývoj

```bash
# Android
./gradlew :app:assembleDebug      # (Windows: gradlew.bat)
./gradlew :app:testDebugUnitTest

# iOS (Mac, Xcode 15+)
brew install xcodegen
cd ios && xcodegen generate && open EuroklicMapa.xcodeproj
```

Detaily: [CLAUDE.md](CLAUDE.md) (Android), [ios/README.md](ios/README.md) (iOS).

## Vydání nové verze

1. Přidej sekci `## <verze> — <datum>` do `CHANGELOG.md` **i** `CHANGELOG.cs.md`.
2. Commit, push, pak `git tag -a vX.Y.Z -m vX.Y.Z && git push origin vX.Y.Z`.
3. GitHub Actions postaví `EuroklicMapa.apk` (podepsané release klíčem) a `EuroklicMapa.ipa` a připojí je k release.

Podpisový klíč Androidu (`euroklic-release.jks`) **není v repu**, je v GitHub Secrets
(`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`)
a záloha je u autora. Bez něj nejde vydat kompatibilní aktualizace.

## Klíče (nejsou v repu)

Mapy.com tile klíč se injektuje při buildu, nikdy není v gitu:

| Platforma | Kde | Kdy se načte |
|---|---|---|
| Android | `local.properties` → `MAPY_APIKEY=…` (nebo env `MAPY_APIKEY`) | `BuildConfig.MAPY_APIKEY` při buildu |
| iOS | `ios/Secrets.xcconfig` (zkopíruj z `.example`) → `MAPY_APIKEY` | Info.plist `MapyApiKey` při buildu |
| CI | GitHub Secret `MAPY_APIKEY` | oba workflowy |

Klíč vytvoříš na [developer.mapy.com](https://developer.mapy.com/).

## Licence

[MIT](LICENSE) · Mapové dlaždice © Seznam.cz a.s. a další (povinná atribuce).
