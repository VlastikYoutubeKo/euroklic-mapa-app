# Prompt pro AI — průzkum plánků stanic ČD (Euroklíč Mapa)

> Zkopíruj jako první zprávu do Claude Code (nebo jiné agentní AI s
> přístupem k internetu a schopností dívat se na obrázky). Je to
> **průzkumný/exploratorní úkol**, ne zadání "implementuj tohle" — cíl
> je zjistit, jestli se z plánků stanic dá spolehlivě vytáhnout
> užitečná informace (hlavně: kde přesně je WC, jestli je poblíž
> bezbariérový vstup/výtah), a napsat čestnou zprávu o tom, co jde a
> co ne. Nic se přímo nemění na živém backendu Euroklíč Mapy.

---

## Absolutní pravidla

- **Jen čtení.** Žádné psaní do žádné databáze, žádné volání
  zápisových API. Tohle je průzkum zdroje dat, ne implementace.
- **Zdroj (cd.cz) je cizí web, ne náš.** Neagresivní rychlost stahování
  (řádově desetiny sekundy mezi requesty, stejně jako existující
  `update_cd.php` v repu dělá `usleep(200000)` mezi stanicemi — drž se
  podobného tempa). Nestahuj víc stanic, než potřebuješ na ověření
  proveditelnosti (5–10 stačí na začátek, ne všech 109).
- **Nevymýšlej si, co se nedá ověřit.** Pokud se z plánku nedá
  spolehlivě poznat, kde přesně WC je (vs. jen že tam nějaké WC
  piktogramy jsou), napiš to jako limitaci — nehádej polohu jen aby
  výstup vypadal hotově.
- **Výstup je návrh/nález, ne kód nasazený na produkci.** Cokoliv
  navrhneš pro backend (nové pole, nový import krok), napiš jako
  návrh se zdůvodněním — needěl PHP změny v `euroklic` repu sám.

---

## 1) Kontext

Euroklíč Mapa (`euroklic.odjezdy.online`) agreguje mj. WC na
železničních stanicích Českých drah, které mají v popisu i otevírací
dobu a (nově) strukturovanou přístupnost budovy (`wheelchair`:
yes/no, z `cd.cz` sekce "Přístupnost stanice" — to už je hotové a
funguje). Chybí ale prostorová informace: **kde přesně v budově/na
nástupišti to WC je** a jestli cesta k němu vede kolem výtahu/rampy.
`cd.cz` má pro každou stanici orientační plánek — tenhle úkol zjišťuje,
jestli se z něj dá tahle informace vytáhnout automaticky.

## 2) Co už víme o struktuře dat (ověřeno 2026-09-02, popsáno přesně, ať to neobjevuješ znovu)

**Jak najít plánek ke stanici:** stránka stanice
(`https://www.cd.cz/stanice/{slug}/{id}`, appka/backend ji už stahuje
kvůli GPS/hodinám/přístupnosti — viz `update_cd.php` v `euroklic`
repu) obsahuje odkaz:
```html
<a class="link" href="/planek/{planekId}">Orientační plánek stanice</a>
```
`{planekId}` **není totéž co `{id}` stanice** (příklad: Bílina má
`id=5454819` na stránce stanice, ale `planekId=548198` na plánku) —
musí se vytáhnout z tohohle odkazu, ne odvodit.

**Co je na `https://www.cd.cz/planek/{planekId}`:** stránka obsahuje
`<script>` blok s `var model = {"noData": false, "data": "<base64>"};`
— `data` je **base64-zakódované SVG** (ne PNG/JPG odkaz). Po dekódování
(`base64.b64decode(...).decode('utf-8')`) je to plnohodnotné Inkscape
SVG (~1–1,5 MB), `viewBox="0 0 744.09507 1052.363"` (A4 na výšku).

**Co v tom SVG je (ověřeno na stanici Bílina):**
- **0** `<text>` elementů — plánek **nemá strojově čitelný text**
  (žádné popisky jako "WC" napsané jako text, i kdyby vypadaly jako
  text vizuálně).
- **~16** `<image>` elementů — malé (typicky `width="24.9" height="24.9"`)
  **base64 PNG piktogramy** vložené přímo v SVG (`xlink:href="data:image/png;base64,..."`),
  každý s vlastní pozicí `x`/`y` (v souřadnicích `viewBox`u, případně
  přes `transform="matrix(...)"` na rodičovské grupě — nezapomeň
  transform aplikovat, jinak pozice nebude sedět).
- Zbytek SVG (obrys budovy, nástupiště, koleje) je pravděpodobně
  vektorová geometrie (`<path>`/`<rect>`/`<line>`) BEZ popisků, co by
  řekly "tohle je nástupiště 1" – needěl si jistý, ověř na reálném
  souboru, jestli se tam najde aspoň číslování nástupišť.

**Legenda piktogramů:** `https://www.cd.cz/dalsi-sluzby/sluzby-ve-stanici/-40338/`
— má ~89 pojmenovaných ikon (`<img src="/images/cdosn/pikto/station-layout/{N}-{popis}-picto-nadrazi.svg">`
+ text vedle). Relevantní pro WC/bezbariérovost:
`111-wc-picto-nadrazi.svg` (Toalety), `113-zena` (WC ženy),
`114-muz` (WC muži), `131-vytah` (Výtah), `133-bez-barier-pristup`
(Bezbariérový přístup), `91-zved-plosina-invalid` (Mobilní zdvihací
plošina), `128-schodiste` (Schody). **Tohle jsou SVG na legendové
stránce, ale piktogramy VLOŽENÉ do plánku jsou PNG raster** — nemusí
být bajtově stejné soubory, i když vizuálně stejná ikona. Nespoléhej
na to, že se dají srovnat hash/bajty; musí se poznat vizuálně (proto
je tenhle úkol pro AI se schopností dívat se na obrázky, ne jen na
text).

## 3) Úkol

1. **Ověř postup na ~5–10 stanicích** (vezmi je z `https://www.cd.cz/stanice/Home/GetSR70`,
   stejný POST požadavek jako `update_cd.php` v repu používá — `aData: [null, "WC osazeno eurozámkem", "Všechny kraje", "cs"]`):
   - stáhni stránku stanice, vytáhni `planekId` z odkazu na plánek,
   - stáhni `/planek/{planekId}`, vytáhni a dekóduj base64 SVG,
   - vytáhni všechny `<image>` elementy (pozice + dekódovaný PNG obsah).
2. **Pro každý nalezený piktogram na plánku** urči (vizuálně, pohledem
   na dekódovaný PNG): co zobrazuje. Zaměř se hlavně na to, jestli je
   na plánku vůbec piktogram WC/toalety, případně výtahu/bezbariérového
   přístupu/mobilní plošiny.
3. **Zkus určit přibližnou polohu** nalezeného WC piktogramu vzhledem
   ke zbytku plánku (souřadnice v rámci `viewBox`u → aspoň hrubé
   "vlevo/vpravo/uprostřed", "blízko vchodu/blízko nástupiště", pokud
   se dá odvodit z okolní vektorové geometrie nebo jiných piktogramů
   nablízku jako je vchod/pokladna). **Buď upřímný, jak jistý si tím
   jsi** — je v pořádku napsat "pozici nejde spolehlivě určit bez
   lepšího porozumění vektorové geometrii budovy".
4. **Zkus, jestli je proveditelné vyrenderovat celé SVG jako obrázek**
   (headless prohlížeč / knihovna na SVG→PNG) a podívat se na něj vcelku
   místo skládání z jednotlivých `<image>` fragmentů — možná je to
   spolehlivější cesta k odpovědi na "kde je to WC" než parsování
   jednotlivých piktogramů zvlášť.

## 4) Výstup

Napiš zprávu (ne kód nasazený nikam) se třemi částmi:

1. **Proveditelnost** — jde z těchhle plánků spolehlivě vytáhnout
   "je/není WC" a "přibližná poloha"? Na kolika z otestovaných stanic
   to fungovalo?
2. **Návrh integrace** (jen návrh, needěl) — kdyby to fungovalo dost
   dobře, jak by se to dalo zapojit do `euroklic` backendu: nové pole
   v `locations` (např. `wc_location_hint` volný text), odkud by se
   volalo (rozšíření `update_cd.php`), jak často (ruční spouštění,
   stejně jako dnes).
3. **Ukázka** — pro 2–3 stanice z testovaného vzorku ukaž konkrétně, co
   se z plánku podařilo vytáhnout (nebo nepodařilo a proč).

Pokud se po prvních pokusech ukáže, že tohle je slepá ulička (piktogramy
nejdou spolehlivě rozpoznat, poloha nejde určit), **řekni to přímo** —
to je platný a užitečný výsledek průzkumu, ne neúspěch.
