# Changelog

Změny v aplikaci **Euroklíč Mapa pro iOS**, nejnovější verze nahoře. Anglická verze je v `CHANGELOG.md`.

Při vydání verze (git tag `v*`) se text příslušné sekce (anglicky i česky) použije jako popis GitHub Release
a stejný soubor je zabalený i v aplikaci (Více → Co je nového; po aktualizaci se ukáže sám).
Nadpis sekce musí mít tvar `## <verze> — <datum>`, položky jsou odrážky `- `.

## 1.2.1 — 2026-11-20
- Android: aplikace je teď ke stažení jako podepsané APK na GitHub Releases (EuroklicMapa.apk). Není na Google Play ani v App Store, instaluje se ze souboru.

## 1.2.0 — 2026-11-19
- Aplikace je teď česky i anglicky. Řídí se jazykem telefonu: v češtině česky, ve všech ostatních jazycích anglicky (včetně tohoto seznamu změn, widgetů a žádostí o oprávnění).
- Vzdálenosti se v angličtině píšou s tečkou, v češtině s čárkou.

## 1.1.0 — 2026-11-19
- Nový režim Odjezdy (veřejná doprava): živé polohy vozidel na mapě s číslem linky, filtr typů dopravy, karta vozidla se směrem, zpožděním a trasou, odjezdy ze zastávek, hledání zastávek i vozidel, oblíbené zastávky a aktuality. Přepíná se v Více → Režim aplikace.
- Widgety: Nejbližší WC a Odjezdy poblíž (na ploše i na zamčené obrazovce).
- Profilová fotka a jméno přihlášeného uživatele v Více.
- Přidání komentáře a fotky k místu, nahlášení problému a (pro správce) fronta ke schválení.
- Hledání adres na mapě Euroklíče.

## 1.0.1 — 2026-11-19
- Fotku místa lze rozkliknout na celou obrazovku (přiblížení dvěma prsty nebo dvojitým klepnutím).
- Odjezdy: opraveno načítání spojů DÚK (dopravce posílal čas v jiném tvaru, takže se zahazovaly) a přidán přepínač dopravců.
- Přihlášení přes Discord a Google se teď po dokončení vrátí zpět do aplikace (dřív zůstalo na webu a nepřihlásilo).
- Aplikace má vlastní ikonu.
- Po aktualizaci se ukáže, co je nového; celý seznam změn je v Více → Co je nového.

## 1.0.0 — 2026-11-19
- Detail místa jako karta nad mapou: velké tlačítko Navigovat, živé odjezdy z nejbližších zastávek (odjezdy.online), podrobnosti, hlasování a komentáře.
- Mapa na celou obrazovku, vycentrovaná na vaši polohu, s panelem nejbližších míst.
- Podkladová mapa je na výběr: Mapy.com nebo Apple Maps (Více → Mapa).
- Tmavý režim a čitelnější odkazy na tmavém pozadí.
- Zdroje pro SideStore, AltStore, Feather a LiveContainer najdete na euroklic.odjezdy.online.
