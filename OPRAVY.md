# Zoznam opráv na hromadný update

Opravy a nápady z testovania Wholphinix v1.0.0 na rpi5. Zbierajú sa postupne, implementujú sa naraz v ďalšej verzii.

| # | Oblasť | Popis | Stav |
|---|---|---|---|
| 1 | Rebríčky | Filmy s ČSFD ID, ale bez ČSFD hodnotenia (neúplné metadáta, napr. „The Big Blue“, „The Alpinist“) sa radia medzi top filmy. V rebríčku vynechať položky bez hodnotenia (prípadne ich dať na koniec) a v logu/nastaveniach ukázať, koľkým chýbajú ČSFD metadáta. | nové |
| 2 | TV tipy dňa | Film/seriál z TV tipov, ktorý nie je v Jellyfin knižnici, odlíšiť: karta s červeným lemom (ČSFD červená). Filmy z knižnice ostávajú bez lemu. | nové |
