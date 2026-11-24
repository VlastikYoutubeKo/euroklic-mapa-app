# Euroklíč Mapa

**Kde je nejbližší bezbariérové WC, které odemkneš Euroklíčem?** Euroklíč Mapa to ukáže na jedné obrazovce:
mapa toalet na Euroklíč v Česku, na Slovensku, v Německu, Rakousku a Švýcarsku, seřazená podle vzdálenosti od tebe,
s adresou, popisem, otevírací dobou a vybavením. K tomu výdejní místa samotného klíče. Zdarma, bez reklam a bez účtu.

Pro lidi s omezenou mobilitou, seniory a rodiče s kočárky.

<p>
  <img src="https://euroklic.odjezdy.online/sideload/screens/ios-1.jpg" width="170" alt="Mapa s nejbližšími toaletami">
  <img src="https://euroklic.odjezdy.online/sideload/screens/ios-2.jpg" width="170" alt="Detail místa">
  <img src="https://euroklic.odjezdy.online/sideload/screens/ios-3.jpg" width="170" alt="Seznam míst">
</p>

- **Funguje i bez signálu.** Stažená místa a mapa oblasti zůstanou po ruce offline.
- **Soukromí.** Poloha se používá jen k seřazení míst a vycentrování mapy a nikam se neodesílá. Dobrovolné anonymní měření je ve výchozím stavu vypnuté, viz [PRIVACY.md](PRIVACY.md).
- **Dělá ji komunita.** Chybějící místo přidáš s fotkou, potvrdíš hlasem, že funguje, nebo nahlásíš problém.
- **Přístupnost je podmínka, ne funkce:** velké cíle, vysoký kontrast, TalkBack a VoiceOver, světlý i tmavý režim, česky i anglicky.
- **Navíc režim Odjezdy:** živá mapa autobusů, tramvají a vlaků a odjezdy ze zastávek.

Data jsou z OpenStreetMap (© přispěvatelé OpenStreetMap, ODbL), od Českých drah a od komunity.
Web: [euroklic.odjezdy.online](https://euroklic.odjezdy.online) · Pro novináře a partnery: [press kit](docs/press/README.md).

---

*Níže je technická část pro vývojáře.* Aplikace je nativní klient veřejného webu (PHP + SQLite), jehož backend
je pouze konzumován, nikdy neměněn. Přístupnost splňuje WCAG AA (48 dp cíle).

## Stažení

Aplikace **nejsou v Google Play ani v App Store** (zveřejnění v obchodech je placené a zatím se nevyplatí),
instalují se ze souboru. Vše je v [GitHub Releases](https://github.com/VlastikYoutubeKo/euroklic-mapa-app/releases/latest):

| Platforma | Soubor | Jak |
|---|---|---|
| Android 7.0+ | `EuroklicMapa.apk` (podepsané) | stáhnout a nainstalovat (povolit instalaci z neznámých zdrojů) |
| iOS 17+ | `EuroklicMapa.ipa` (nepodepsané) | přes SideStore / AltStore / Feather / LiveContainer, viz [návod](docs/ios-sidestore-navod.md) |

Stránka s odkazy a zdroji pro SideStore/AltStore: <https://euroklic.odjezdy.online/app>.

Beta (testovací) verze mají vlastní zdroj a changelog, viz [Beta verze](docs/beta-verze.md).
Co je nového v které verzi: [CHANGELOG.cs.md](CHANGELOG.cs.md) (česky), [CHANGELOG.md](CHANGELOG.md) (anglicky).

## Co aplikace umí

| | Android | iOS |
|---|---|---|
| Mapa Euroklíč WC + výdejních míst, „Nejbližší WC“ | ✅ | ✅ |
| Seznam, filtry, hledání adresy, oblíbené | ✅ | ✅ |
| Detail (status, otevírací doba, plán stanice, hlasování, komentáře) | ✅ | ✅ |
| Přihlášení (Discord/Google, PKCE), přidání místa s fotkou, komentář/fotka k místu, nahlášení problému | ✅ | ✅ |
| Admin fronta (schvalování) | ✅ | ✅ |
| Režim Odjezdy: živá mapa vozidel, zastávky, odjezdy, hledání, oblíbené | ✅ (bez aktualit a výluk) | ✅ plný režim |
| Widgety | ✅ nejbližší WC | ✅ nejbližší WC, odjezdy |
| Notifikace | ✅ lokální (WorkManager) | ❌ |
| Čeština + angličtina (podle jazyka telefonu) | ✅ | ✅ |
| „Co je nového“ po aktualizaci | ✅ | ✅ |

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
