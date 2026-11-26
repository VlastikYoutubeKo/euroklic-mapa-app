# Changelog

Změny v aplikaci **Euroklíč Mapa** (Android a iOS), nejnovější verze nahoře. Anglická verze je v `CHANGELOG.md`.

Při vydání verze (git tag `v*`) se text příslušné sekce (anglicky i česky) použije jako popis GitHub Release
a stejný soubor je zabalený i v aplikaci (Více → Co je nového; po aktualizaci se ukáže sám).
Nadpis sekce musí mít tvar `## <verze> — <datum>`, položky jsou odrážky `- `. Odrážka začínající
`[Android]` nebo `[iOS]` se ukáže jen v dané aplikaci (značka se skryje), odrážky bez značky v obou.

## 1.5.0 — 2026-11-26
- Cesta k místu: u tlačítka Navigovat je volba mezi pěšky a MHD. Pěšky se nabídne jen tam, kde to dává smysl (do zhruba 45 minut), u delší cesty je jen MHD. Časy jsou odhady a aplikace říká, že bezbariérovost cesty není ověřená.
- [iOS] Trasa pěšky se kreslí přímo na mapě po skutečných cestách, s časem a vzdáleností. U MHD aplikace ukáže odhad a trasu s přestupy pak ukážou Mapy.
- [iOS] Odjezdy víc připomínají zbytek aplikace: stejná lišta záložek a ikona záložky jako v Euroklíči, filtry s názvem a žlutou značkou, měkčí karty a plynulé přepnutí mezi oběma režimy. Cedule zastávky, štítky linek a barvy zpoždění zůstaly beze změny.
- [iOS] Odjezdy: zastávky v okolí se načítají rychleji (jeden dotaz místo šesti).
- [iOS] List s detailem místa se otevírá o něco výš, ať je tlačítko Vyrazit pořád vidět.

## 1.4.1 — 2026-11-26
- Upozornění na novou verzi: aplikace se nejvýš jednou za 12 hodin zeptá serveru, jestli existuje novější verze, a když ano, ukáže nahoře ve Více kartu s tlačítkem ke stažení („Později“ ji pro tu verzi schová). Betaverze se na nic neptá. Jde o anonymní dotaz, viz PRIVACY.md.
- Poznámka k místu z OpenStreetMap se už nebere z vyřazených míst, takže u nádraží (například Praha hl.n.) už není zastaralá poznámka o zavření. Data jsou opravená na serveru, kvůli tomu nemusíte aktualizovat.

## 1.4.0 — 2026-11-25
- Dobrovolné anonymní měření používání (Více → Soukromí). Ve výchozím stavu je vypnuté, počítá jen, které funkce se používají, a nikdy nesbírá polohu ani osobní údaje. Podrobnosti jsou v PRIVACY.md.
- [iOS] Krátká úvodní obrazovka při prvním spuštění. Znovu ji otevřete ve Více → Jak aplikace funguje.
- [iOS] Přepracovaný detail místa: poznámka k místu (například dlouhodobé uzavření) je hned pod názvem, tlačítko Navigovat je kompaktnější, přístupnost stanice je před odjezdy, odjezdy jdou sbalit a místo se ukládá záložkou.
- [iOS] Klidnější mapa: jedno výrazné tlačítko „Nejbližší WC“, ostatní akce (Přidat místo, Stáhnout mapu) jsou v menu a přepínač Odjezdy je vedlejší tlačítko. Ovládání mapy i seznam nejbližších míst fungují i při největší velikosti písma.
- [iOS] Odjezdy: panel dole na mapě se zastávkami v okolí a jejich nejbližšími odjezdy (zastávky se ukážou hned, odjezdy se doplní, jak se načtou), filtry dopravy s názvem a fajfkou a hledání zastávky nebo vozidla.
- [iOS] Více → Přenos dat: oblíbená místa, oblíbené zastávky, hlasy a nastavení se dají exportovat do souboru a naimportovat v jiné kopii aplikace, například v betě. Statistiky jsou o úroveň níž.
- [iOS] Oblíbené mají srozumitelnější prázdný stav.

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
