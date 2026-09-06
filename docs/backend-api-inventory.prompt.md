# Prompt pro Claude Code na serveru s webem euroklic.odjezdy.online

Spusť Claude Code v adresáři, kde je zdrojový kód webu `euroklic.odjezdy.online`
(PHP + SQLite) a přístup k živé/kopii databáze. Vlož následující:

---

Jsi v repu PHP + SQLite backendu webu `euroklic.odjezdy.online`. Potřebuju **kompletní,
přesný a aktuální inventář celého HTTP API**, abych podle něj mohl stavět nativní Android
appku, která tenhle backend jen konzumuje.

## Absolutní pravidla
- **Nic neměň.** Žádné úpravy PHP, configu, `.htaccess`, schématu ani dat. Žádné `git commit`.
- Do DB smíš jen **read-only `SELECT`** (ideálně přes `sqlite3 -readonly` nebo kopii souboru).
  Nikdy `INSERT/UPDATE/DELETE/PRAGMA writable`.
- Neposílej data nikam ven, nevolej žádné externí služby.
- Pokud něco nejde zjistit ze zdrojáku ani z read-only DB, napiš to explicitně jako
  „NEznámé / nutno ověřit" — nehádej.

## Co zmapovat

### 1. Seznam endpointů
Projdi web root i případný router (`index.php`, `.htaccess`, `RewriteRule`, framework routy).
Vypiš **každý `*.php` (a každou routu), který je dosažitelný přes HTTP**, včetně těch, co se
zdají nepoužité. Pro každý: cesta/URL, podporované HTTP metody, jednořádkový účel.
Zvlášť vypíchni všechny `api_*.php`.

### 2. Request kontrakt (pro každý endpoint)
- Všechny **query parametry** a **body/multipart pole**: název, typ, povinné?, default,
  validace, max. velikost (u uploadů typy souborů + limit).
- Zdroj: grep `\$_GET`, `\$_POST`, `\$_REQUEST`, `\$_FILES`, `php://input`, `getallheaders`.
- Očekávaný `Content-Type` requestu.

### 3. Autentizace / session / CSRF
- Které endpointy vyžadují přihlášení (Discord/Google OAuth přes `auth.php` / `auth_google.php`)?
- Které vyžadují `X-CSRF-Token` (nebo jiný název)? **Jak se token získává** (který endpoint /
  cookie / meta tag), jeho životnost, jestli je vázán na session.
- Názvy cookies, `SameSite`, `Secure`, `HttpOnly`, doba platnosti session.
- Co endpoint vrátí při chybě auth (status + tělo).

### 4. Response kontrakt (pro každý endpoint)
- `Content-Type` odpovědi.
- **Přesné JSON schéma** (všechny klíče, typy, nullability, vnořené struktury) + **reálný
  příklad odpovědi** (velká pole zkrať na 1–2 prvky a označ `…`).
- Všechny stavové kódy, které může vrátit, a tvar chybové odpovědi (envelope: `{status, error}`?).
- HTTP hlavičky, které nastavuje: CORS (`Access-Control-Allow-Origin`, `-Methods`, `-Headers`,
  `-Credentials`), `Cache-Control`/`ETag`/`Last-Modified`, jakýkoli rate-limit hlavička.
- Podpora **stránkování / filtrování / řazení** (i kdyby byla nevyužitá) — parametry a chování.

### 5. Datový slovník vrstev, které appka zobrazuje
Pro `api_locations.php` a `api_pickup_points.php` (a jakýkoli další „list" endpoint):
- Vypiš **každý klíč v `properties`**, jeho typ, jestli může být `null`, a **skutečné enum
  hodnoty z DB**. Konkrétně spusť read-only dotazy typu:
  - `SELECT DISTINCT source FROM <tabulka>;` + počty přes `GROUP BY`
  - `SELECT DISTINCT precision FROM <tabulka>;`
  - rozsah `id`, počet řádků, kolik má `photo_url IS NOT NULL`, apod.
- Formát souřadnic v GeoJSON (potvrď `[lon, lat]`), SRID/datum (WGS84?).
- Která pole jsou „mrtvá" (vždy `null`, deprecated) — např. `amenity`.
- Jak se počítají `likes`/`dislikes` (agregace z jaké tabulky).
- Jestli list obsahuje i neschválené/skryté záznamy nebo jen `approved`.

### 6. DB schéma
- `.schema` všech tabulek relevantních pro API (WC lokace, výdejní místa, hlasy, komentáře,
  uživatelé — u uživatelů **nevypisuj žádná osobní data**, jen strukturu).
- Vztahy mezi tabulkami, indexy, triggery.

### 7. Vedlejší efekty a infrastruktura
- Endpointy, co zapisují: do jakých tabulek/sloupců, jaké soubory (adresář uploadů, cesta,
  veřejná URL vzoru), posílají e-maily, volají externí API.
- Cron/scheduled skripty, které plní/aktualizují data (import z ČD/OSM/Mapotic) — co dělají,
  jak často, jaká pole přepisují.
- Proxy na mapové dlaždice: přesný URL template(y), jak se řeší apikey (hardcoded? env?),
  povolené styly (`basic`/`outdoor`/`aerial`), povinná attribution a odkaz na podmínky,
  případné omezení refereru/originu.
- Jaké statické assety má appka reusovat (ikony `icons/…`, placeholder obrázek) a jejich URL.

### 8. Provozní
- Verzování API / politika breaking changes, jestli existuje changelog.
- Znáš plánované změny endpointů (TODO/FIXME v kódu, zakomentované routy)?

## Výstup
Zapiš vše do **`API.md` v kořeni repa** (přehledně, jeden `##` blok na endpoint, tabulky
parametrů, fenced JSON příklady) a zároveň celý obsah vypiš do odpovědi. Na konec přidej
sekci **„Otevřené otázky / nutno ověřit"** se vším, co se nepodařilo spolehlivě zjistit.
