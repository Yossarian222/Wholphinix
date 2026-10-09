# Zoznam opráv na hromadný update

Opravy a nápady z testovania Wholphinix v1.0.0 na rpi5. Zbierajú sa postupne, implementujú sa naraz v ďalšej verzii.

| # | Oblasť | Popis | Stav |
|---|---|---|---|
| 1 | Rebríčky | Filmy s ČSFD ID, ale bez ČSFD hodnotenia (neúplné metadáta, napr. „The Big Blue“, „The Alpinist“) sa radia medzi top filmy. V rebríčku vynechať položky bez hodnotenia (prípadne ich dať na koniec) a v logu/nastaveniach ukázať, koľkým chýbajú ČSFD metadáta. | nové |
| 2 | TV tipy dňa | Film/seriál z TV tipov, ktorý nie je v Jellyfin knižnici, odlíšiť: karta s červeným lemom (ČSFD červená). Filmy z knižnice ostávajú bez lemu. | nové |
| 3 | WhatsApp bot | Hlasové správy: bot stiahne hlasovku z WhatsAppu (Graph API media), prepíše ju na text (Whisper alebo iná STT služba, voliteľne podľa env premennej) a spracuje ako textový príkaz. V odpovedi zopakuje, čo rozumel („Rozumel som: pusti Pelíšky“). | nové |
| 4 | TV tipy dňa | Niektoré tipy (napr. staršie filmy „Vrah skrýva tvár“ 1966, „Dobrodruhovia“ 1967) sú bez plagátu – Seerr nevráti poster a ČSFD detail zo sidecaru tiež nie. Doplniť záložný plagát: obrázok priamo zo stránky TV tipov ČSFD (`article-poster-78` má náhľad), prípadne vyhľadať TMDb poster podľa názvu+roku; ako posledné riešenie pekný placeholder s názvom namiesto ikony kamery. | nové |
| 5 | Vzhľad | Vždy zobrazovať pozadie (backdrop) náhodného filmu/seriálu z knižnice – na každej obrazovke: kategórie, podkategórie, knižnice, nastavenia, TV tipy, rebríčky… Tam, kde nie je vybraná konkrétna položka, použiť náhodný backdrop (meniť napr. pri vstupe na obrazovku / každých pár minút), stlmený tmavým prechodom, aby text ostal čitateľný. | nové |
| 6 | Objavovať (Seerr) | Film, ktorý už je v Jellyfin knižnici (zhoda podľa TMDb/IMDb ID), nesmie ponúkať „Žiadosť“ – namiesto toho tlačidlo „Prehrať“ a odkaz na položku v knižnici (slovenský popis, ČSFD hodnotenie). Pozn.: v Seerr zapnúť aj Jellyfin sync/scan knižnice. | nové |
| 7 | Vyhľadávanie | Jedna lupa vpravo hore vedľa hodín (namiesto dvoch položiek „Hľadať“/„Objavovať“ v bočnom menu). Hľadá len vo vlastnej knižnici (filmy + seriály). Živé návrhy od 3. zadaného znaku, priebežne sa spresňujú počas písania. Položku „Objavovať“ z menu odstrániť (žiadosti cez Seerr ostávajú v TV tipoch a cez WhatsApp/Claude). | nové |
| 8 | ČSFD plugin – Chcem vidieť | Cache „Chcem vidieť“ skrátiť zo 6 h na 3 h (`CsfdWatchlistClient.CacheTtl`). Ostatné cache ostávajú (metadáta/zaujímavosti 30 dní, TV tipy 3 h, rebríčky 7 dní). | nové |
| 9 | ČSFD plugin – nočné prednačítanie | Plánovaná úloha v Jellyfine (predvolene ~3:00): pomaly (≈1 film / 3 s) prednačíta zaujímavosti pre všetky filmy/seriály s ČSFD ID, ktorým cache chýba alebo expiruje do 3 dní; pripraví TV tipy na nasledujúci deň a obnoví „Chcem vidieť“. Čas a tempo nastaviteľné v nastaveniach pluginu, úloha sa dá spustiť aj ručne (Dashboard → Naplánované úlohy). | nové |
