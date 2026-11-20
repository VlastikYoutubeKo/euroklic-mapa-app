# Changelog

Změny v aplikaci **Euroklíč Mapa** (Android a iOS), nejnovější verze nahoře. Anglická verze je v `CHANGELOG.md`.

Při vydání verze (git tag `v*`) se text příslušné sekce (anglicky i česky) použije jako popis GitHub Release
a stejný soubor je zabalený i v aplikaci (Více → Co je nového; po aktualizaci se ukáže sám).
Nadpis sekce musí mít tvar `## <verze> — <datum>`, položky jsou odrážky `- `. Odrážka začínající
`[Android]` nebo `[iOS]` se ukáže jen v dané aplikaci (značka se skryje), odrážky bez značky v obou.

## 1.3.0 — 2026-11-20
- Offline režim: bez připojení aplikace funguje ze stažených míst a uložené mapy a nahoře na mapě to napíše. Mapu oblasti jde stáhnout předem (tlačítko stažení na mapě). Na iPhonu to funguje jen s mapou Mapy.com.
- Rychlé přepnutí mezi Euroklíčem a Odjezdy přímo na mapě (už ne přes Více). Na Androidu je i zástupce na ploše: dlouhé podržení ikony → Odjezdy.
- U přidání fotky jsou teď tři zdroje: vyfotit, vybrat z galerie nebo vybrat ze souborů.
- [Android] Při přidávání místa se mapa otevře na vaší poloze a tlačítko „Použít moji polohu“ tam přesune značku.
- [iOS] Formulář „Přidat místo“ je přepracovaný: mapa se otevře na vaší poloze s tlačítkem „Moje poloha“, přehlednější tlačítka fotky a odeslání.

## 1.2.2 — 2026-11-20
- [Android] Aplikace je teď česky i anglicky. Řídí se jazykem telefonu: v češtině česky, ve všech ostatních jazycích anglicky.
- [Android] „Co je nového“ se po aktualizaci ukáže samo, celá historie je v Více → Co je nového.
- [Android] Nový režim Odjezdy (veřejná doprava), přepíná se v Více → Režim Odjezdy: živá mapa vozidel s čísly linek a filtrem typu, karta vozidla se zpožděním, trasou, evidenčním číslem a údaji o voze, zastávky s odjezdy, hledání zastávek a oblíbené zastávky.
- [Android] Widget „Nejbližší WC“ na plochu (Více → Widget na plochu).
- [iOS] Krátké potvrzení po odeslání hlasu, komentáře, fotky nebo hlášení a hlas u místa se pamatuje i po restartu.
- [iOS] Přehlednější tlačítka hlasování („Funguje“ / „Nefunguje“), „Zatím bez hlasů“ už není zeleně, čitelnější upozornění a ve statistikách jsou čísla bez slova „míst“.

## 1.2.1 — 2026-11-20
- [Android] Aplikace je teď ke stažení jako podepsané APK na GitHub Releases (EuroklicMapa.apk). Není na Google Play ani v App Store, instaluje se ze souboru.

## 1.2.0 — 2026-11-19
- [iOS] Aplikace je teď česky i anglicky. Řídí se jazykem telefonu: v češtině česky, ve všech ostatních jazycích anglicky (včetně tohoto seznamu změn, widgetů a žádostí o oprávnění).
- [iOS] Vzdálenosti se v angličtině píšou s tečkou, v češtině s čárkou.

## 1.1.0 — 2026-11-19
- [iOS] Nový režim Odjezdy (veřejná doprava): živé polohy vozidel na mapě s číslem linky, filtr typů dopravy, karta vozidla se směrem, zpožděním a trasou, odjezdy ze zastávek, hledání zastávek i vozidel, oblíbené zastávky a aktuality. Přepíná se v Více → Režim aplikace.
- [iOS] Widgety: Nejbližší WC a Odjezdy poblíž (na ploše i na zamčené obrazovce).
- [iOS] Profilová fotka a jméno přihlášeného uživatele v Více.
- [iOS] Přidání komentáře a fotky k místu, nahlášení problému a (pro správce) fronta ke schválení.
- [iOS] Hledání adres na mapě Euroklíče.

## 1.0.1 — 2026-11-19
- [iOS] Fotku místa lze rozkliknout na celou obrazovku (přiblížení dvěma prsty nebo dvojitým klepnutím).
- [iOS] Odjezdy: opraveno načítání spojů DÚK (dopravce posílal čas v jiném tvaru, takže se zahazovaly) a přidán přepínač dopravců.
- [iOS] Přihlášení přes Discord a Google se teď po dokončení vrátí zpět do aplikace (dřív zůstalo na webu a nepřihlásilo).
- [iOS] Aplikace má vlastní ikonu.
- [iOS] Po aktualizaci se ukáže, co je nového; celý seznam změn je v Více → Co je nového.

## 1.0.0 — 2026-11-19
- [iOS] Detail místa jako karta nad mapou: velké tlačítko Navigovat, živé odjezdy z nejbližších zastávek (odjezdy.online), podrobnosti, hlasování a komentáře.
- [iOS] Mapa na celou obrazovku, vycentrovaná na vaši polohu, s panelem nejbližších míst.
- [iOS] Podkladová mapa je na výběr: Mapy.com nebo Apple Maps (Více → Mapa).
- [iOS] Tmavý režim a čitelnější odkazy na tmavém pozadí.
- [iOS] Zdroje pro SideStore, AltStore, Feather a LiveContainer najdete na euroklic.odjezdy.online.
