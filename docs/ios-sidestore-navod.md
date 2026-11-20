# iOS: instalace přes SideStore (obnova v telefonu, bez počítače)

Euroklíč Mapa není v App Store (placené a zatím se nevyplatí), proto se instaluje ze souboru `EuroklicMapa.ipa`.
Nejpohodlnější je **SideStore**: počítač potřebuješ **jen jednou** při první instalaci. Potom se aplikace
jednou za 7 dní obnovuje přímo v telefonu (přes místní VPN `LocalDevVPN`), bez kabelu a bez počítače.
Zdarma stačí běžné Apple ID. Limit: 3 aplikace a 10 App ID za týden (widgety se počítají samostatně).

## Co potřebuješ
- iPhone/iPad s iOS 15 nebo novějším a nastaveným kódem
- Apple ID (stačí zdarma)
- Wi-Fi (mobilní data na obnovu nestačí)
- Počítač (Windows, macOS, Linux) jen na první instalaci

## První instalace (jednorázově, s počítačem)
1. V telefonu nainstaluj z App Store **LocalDevVPN** a zapni v něm *Connect* (povol VPN konfiguraci).
2. V počítači nainstaluj **iloader** (na Windows nejdřív iTunes nebo Apple Devices): https://github.com/nab138/iloader
3. Připoj telefon kabelem, potvrď „Důvěřovat tomuto počítači“, v iloaderu se přihlas Apple ID, vyber zařízení a dej *Install SideStore (Stable)*.
4. V telefonu: Nastavení → Obecné → VPN a správa zařízení → důvěřuj svému Apple ID. Na iOS 16+ zapni ještě Nastavení → Soukromí a zabezpečení → Režim vývojáře (telefon se restartuje).
5. Otevři LocalDevVPN (Connect), pak SideStore a přihlas se stejným Apple ID.

## Přidání Euroklíč Mapy
1. V SideStore → Sources → + a vlož adresu zdroje z webu (euroklic.odjezdy.online/app), případně stáhni `EuroklicMapa.ipa` z GitHub Releases a otevři ji v SideStore (Sdílet → SideStore).
2. Klepni na **Free/Get** u Euroklíč Mapy.

## Obnova jednou týdně (už bez počítače)
- Zapni LocalDevVPN (Connect), otevři SideStore a klepni na **Refresh All** (nebo na počítadlo „7 DAYS“ u aplikace).
- SideStore umí obnovu spouštět i na pozadí, když je VPN zapnutá a telefon na Wi-Fi. Spolehlivější je ale kouknout do aplikace jednou za pár dní.
- Když obnova hlásí chybu, nejčastěji pomůže: restartovat LocalDevVPN, být na Wi-Fi, případně vyměnit „pairing file“ (to už vyžaduje počítač s iloaderem).

## Videa
- SideStore bez počítače po úvodním nastavení (EN, 8 min): https://www.youtube.com/watch?v=usN502asfwk
- Instalace SideStore + bezdrátová obnova (EN, 8 min): https://www.youtube.com/watch?v=kaYBV9_uvaA
- Automatická obnova bez PC (EN, 3,5 min): https://www.youtube.com/watch?v=usj0WYanB40
- Když se nic neobnovuje (EN, 1,5 min): https://www.youtube.com/watch?v=_lbSsN8WuQM

Oficiální dokumentace: https://docs.sidestore.io/docs/installation/prerequisites
