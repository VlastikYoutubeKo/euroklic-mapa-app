# Beta changelog

Změny v **beta** verzích aplikace **Euroklíč Mapa** (Android a iOS), nejnovější nahoře. Anglická verze je v `CHANGELOG.beta.md`.
Stabilní verze jsou v `CHANGELOG.cs.md`.

Beta se vydává tagem s příponou, např. `v1.4.0-beta.1` (nejdřív přidej její sekci sem i do anglického souboru).
Vyjde jako GitHub *pre-release* (nikdy ne „latest“, takže stabilní uživatele se netýká), text vydání se bere
z těchto souborů a uvede ji i iOS beta zdroj. Nadpis sekce `## <verze> — <datum>`, položky jsou odrážky `- `;
značky `[Android]` / `[iOS]` fungují stejně jako v `CHANGELOG.md`.

## 1.4.0-beta.1 — 2026-11-24
- První beta. Bety vychází před stabilní verzí, aby šly nové funkce vyzkoušet dřív; můžou obsahovat chyby.
- Dobrovolné anonymní měření používání (Více, ve výchozím stavu vypnuté). Počítá jen, které funkce se používají, nikdy polohu ani osobní údaje. Podrobnosti jsou v PRIVACY.md.
- [iOS] Beta je samostatná aplikace („Euroklíč β“, ikona s pruhem BETA), takže se nainstaluje vedle stabilní verze. V SideStore a dalších obchodech je jako druhá aplikace ve stejném zdroji.
- [iOS] Přenos dat mezi stabilní verzí a betou (Více → Přenos dat): v jedné appce exportujte oblíbená místa, oblíbené zastávky, hlasy a nastavení do souboru a v druhé ho naimportujte. Nic se nesmaže, oblíbené se jen doplní. Přihlášení se nepřenáší. Ve stabilní verzi bude export až od příští stabilní verze.
- [iOS] Odjezdy: panel dole na mapě s hledáním zastávky a zastávkami v okolí s nejbližšími odjezdy. Rozbalí se tažením úchytu nahoru nebo klepnutím na něj.
- [Android] Beta se nainstaluje přes stabilní verzi a data zůstanou.