# Soukromí

Euroklíč Mapa je nezávislý projekt. Ve zkratce: **aplikace bez tvého souhlasu nic nepočítá ani neodesílá o tom, jak ji používáš.**
Úplné zásady jsou na webu: <https://euroklic.odjezdy.online/soukromi.php>, podmínky použití na
<https://euroklic.odjezdy.online/podminky>.

## Poloha

Poloha se používá jen v telefonu k seřazení míst podle vzdálenosti a k vycentrování mapy. Nikam se neodesílá.
Aplikace funguje i bez povolené polohy.

## Dobrovolné anonymní měření

V nastavení (Více → Soukromí) je přepínač **„Pomoci vylepšit aplikaci anonymním měřením“**.
**Ve výchozím stavu je vypnutý.** Dokud ho nezapneš, aplikace neodešle žádnou statistiku.

Když ho zapneš, aplikace pošle na `https://euroklic.odjezdy.online/api_app_event.php` jen tři údaje:

```json
{"event": "place_open", "platform": "android", "version": "1.3.0"}
```

- `event` je jedna z těchto událostí: `app_open` (otevření aplikace), `place_open` (otevření místa),
  `navigate` (klepnutí na Navigovat), `search` (hledání adresy), `vote` (hlas u místa), `place_add` (přidání místa),
  `comment_add` (komentář), `report` (nahlášení problému), `mode_departures` (přepnutí na režim Odjezdy).
- `platform` je `android` nebo `ios`, `version` je číslo verze aplikace.

**Co se neposílá:** poloha, hledaný text, název ani ID místa, obsah komentáře, ID účtu, ID zařízení, reklamní ID, IP adresa
se neukládá, žádné cookies ani přihlašovací údaje. Požadavek jde mimo přihlášení a mimo ostatní komunikaci aplikace.

Server uchovává pouze **denní počty** (den, událost, platforma, verze). Z nich nejde poznat jednotlivý člověk ani zařízení.
Chyby při odeslání se ignorují. Měření lze kdykoli vypnout stejným přepínačem.

Kód měření je v repu: `app/src/main/java/cz/euroklicmapa/data/analytics/Analytics.kt` (Android)
a `ios/EuroklicMapa/Services/Analytics.swift` (iOS).

## Kontrola nové verze

Aplikace se instaluje ze souboru, takže se sama neaktualizuje. Proto se nejvýš jednou za 12 hodin zeptá serveru
`https://euroklic.odjezdy.online/api_app_version.php` na číslo nejnovější stabilní verze. Je to obyčejný dotaz bez
účtu, bez ID zařízení a bez jakýchkoli dat o tobě (server jako každý web vidí jen to, že se někdo zeptal). Když je
verze vyšší než tvoje, ukáže se ve Více karta s odkazem na stažení; „Později“ ji pro tu verzi schová.
Betaverze se na novou verzi neptá. Kód: `data/update/UpdateChecker.kt` (Android) a `Services/UpdateChecker.swift` (iOS).

## Ostatní komunikace

Aplikace stahuje seznam míst z `euroklic.odjezdy.online` a dlaždice mapy z Mapy.com (na iPhonu volitelně Apple Maps).
Přihlášení (Discord nebo Google) je dobrovolné a potřebuješ ho jen k přidání místa, komentáře, fotky nebo nahlášení.
