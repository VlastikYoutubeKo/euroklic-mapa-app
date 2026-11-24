# Beta verze

Beta je testovací verze s novinkami, které se ještě zkoušejí. Může obsahovat chyby. Je to stejná aplikace
(stejné ID balíčku i podpis), takže se instaluje přes stabilní verzi a dá se kdykoli vrátit zpět.
Stabilní uživatele se netýká: beta je na GitHubu označená jako *pre-release*, takže odkaz
`releases/latest` i hlavní zdroj pro SideStore dál vedou na stabilní verzi.

## Jak se beta vydává

1. Do `CHANGELOG.beta.md` a `CHANGELOG.beta.cs.md` přidat sekci `## 1.4.0-beta.1 — datum` (vlastní changelog bety,
   stabilní soubory se nemění).
2. Otagovat `git tag v1.4.0-beta.1 && git push origin v1.4.0-beta.1`.
3. GitHub Actions postaví `EuroklicMapa.apk` a `EuroklicMapa.ipa`, vytvoří pre-release s textem z beta changelogu
   a doplní beta zdroje pro iOS.

Číslování je podle [SemVer](https://semver.org/lang/cs/): `1.4.0-beta.1`, `1.4.0-beta.2`, … a nakonec stabilní
`1.4.0` (jeho sekce se přidá do `CHANGELOG*.md`). Beta je vždy nižší než stabilní verze se stejným číslem.
V aplikaci je beta vidět v Více → O projektu / Co je nového: beta sestavení nahoře ukáže i změny z beta changelogu.

## Jak beta nainstalovat

**iOS:** každý obchod má vlastní beta zdroj (v obchodě se přidá přes Sources → +). Appka je v nich jako
„Euroklíč Mapa BETA“ s oranžovým pruhem BETA na ikoně. Je to stejná aplikace (stejné bundle ID) jako stabilní,
takže ji nahradí, ne vedle sebe.

| Obchod | Adresa zdroje |
|---|---|
| SideStore | `https://raw.githubusercontent.com/VlastikYoutubeKo/euroklic-mapa-app/beta-source/beta-sidestore.json` |
| AltStore | `https://raw.githubusercontent.com/VlastikYoutubeKo/euroklic-mapa-app/beta-source/beta-altstore.json` |
| Feather | `https://raw.githubusercontent.com/VlastikYoutubeKo/euroklic-mapa-app/beta-source/beta-feather.json` |
| LiveContainer | `https://raw.githubusercontent.com/VlastikYoutubeKo/euroklic-mapa-app/beta-source/beta-livecontainer.json` |
| ESign | `https://raw.githubusercontent.com/VlastikYoutubeKo/euroklic-mapa-app/beta-source/beta-esign.json` |

Zdroje ukazují na IPA v GitHub Releases a u každé verze mají vlastní changelog. Generuje je workflow `ios.yml`
do větve `beta-source`, ručně se needitují. Ikona s pruhem je `beta/icon-beta-512.png`.

**Android:** stáhnout `EuroklicMapa.apk` z pre-release na stránce Releases, nebo použít
[Obtainium](https://obtainium.imranr.dev/): přidat repozitář `https://github.com/VlastikYoutubeKo/euroklic-mapa-app`
a zapnout „Zahrnout předběžné verze“ (beta se pak aktualizuje samo).
