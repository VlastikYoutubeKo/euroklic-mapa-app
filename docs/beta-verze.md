# Beta verze

Beta je testovací verze s novinkami, které se ještě zkoušejí. Může obsahovat chyby.
Stabilní uživatele se netýká: beta je na GitHubu označená jako *pre-release*, takže odkaz
`releases/latest` vede dál na stabilní verzi.

## Jak se beta vydává

1. Do `CHANGELOG.beta.md` a `CHANGELOG.beta.cs.md` přidat sekci `## 1.4.0-beta.1 — datum` (vlastní changelog bety,
   stabilní soubory se nemění).
2. Otagovat `git tag v1.4.0-beta.1 && git push origin v1.4.0-beta.1`.
3. GitHub Actions postaví `EuroklicMapa.apk` a `EuroklicMapa.ipa` a vytvoří pre-release s textem z beta changelogu.
   K IPA přiloží `ios-build.txt` (jen číslo `CFBundleVersion` toho sestavení).

Číslování je podle [SemVer](https://semver.org/lang/cs/): `1.4.0-beta.1`, `1.4.0-beta.2`, … a nakonec stabilní
`1.4.0` (jeho sekce se přidá do `CHANGELOG*.md`). Beta je vždy nižší než stabilní verze se stejným číslem.
V aplikaci je beta vidět v Více → O projektu / Co je nového: beta sestavení nahoře ukáže i změny z beta changelogu.

## iOS: beta je samostatná aplikace

Beta IPA se staví s jiným bundle ID, názvem, ikonou a URL schématem (přepisuje je `ios.yml`, výchozí hodnoty
jsou v `ios/project.yml`):

| | Stabilní | Beta |
|---|---|---|
| Bundle ID | `cz.euroklicmapa.EuroklicMapa` | `cz.euroklicmapa.EuroklicMapa.beta` |
| Název | Euroklíč Mapa | Euroklíč β |
| Ikona | `AppIcon` | `AppIconBeta` (oranžový pruh BETA) |
| URL schéma | `euroklicmapa` | `euroklicmapa-beta` |

Obě verze tak můžou být nainstalované zároveň a beta nikdy nepřepíše stabilní. Přihlášení funguje v obou:
callback `euroklicmapa://auth-callback` zachytí přímo `ASWebAuthenticationSession`, systém ho nesměruje.

Data se nesdílí (každá appka má vlastní úložiště). Přenáší se ručně: **Více → Přenos dat → Exportovat data do
souboru** v jedné appce a **Importovat data ze souboru** v druhé. Přenesou se oblíbená místa, oblíbené zastávky,
hlasy a nastavení; oblíbené se jen doplní, nic se nesmaže. Přihlášení (token) se nepřenáší.
Export musí umět i appka, ze které se přenáší, takže přenos ze stabilní verze jde až od první stabilní verze
s touto funkcí.

## Jak betu nainstalovat

**iOS:** zdroje pro sideloading generuje web z GitHub Releases. Beta je v nich druhá aplikace („Euroklíč β“)
vedle stabilní, takže stačí jeden zdroj. Verze v JSON je číselná (`1.4.0`, beta je poznat z názvu a popisu),
jednotlivé bety odlišuje `buildVersion` z `ios-build.txt`.

**Android:** stáhnout `EuroklicMapa.apk` z pre-release na stránce Releases, nebo použít
[Obtainium](https://obtainium.imranr.dev/): přidat repozitář `https://github.com/VlastikYoutubeKo/euroklic-mapa-app`
a zapnout „Zahrnout předběžné verze“ (beta se pak aktualizuje samo). Na Androidu má beta stejný balíček
i podpis, takže se instaluje přes stabilní verzi a data zůstanou.
