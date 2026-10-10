# jellyfin-mcp – ovládanie TV cez Claude

MCP server, cez ktorý Claude (appka na mobile aj PC) ovláda Jellyfin na TV. Spúšťa filmy a seriály, prepína titulky a dabing, pauzuje a posúva prehrávanie.

```
Claude (cloud) ──HTTPS──▶ Tailscale Funnel ──▶ jellyfin-mcp (NAS) ──▶ Jellyfin ──websocket──▶ Wholphinix (RPi5)
```

**Požiadavky na TV:** Wholphinix musí byť z vetvy, ktorá obsahuje commit „Accept remote PlayNow…“ (`main`), musí byť otvorený v popredí a prihlásený ako používateľ `JELLYFIN_USER`. Wholphin drží spojenie so serverom len vtedy, keď je appka aktívna.

## Nástroje, ktoré Claude dostane

| Nástroj | Čo robí |
|---|---|
| `search_library` | hľadá filmy, seriály a epizódy podľa názvu |
| `play` | spustí film alebo epizódu (pokračuje tam, kde si skončil), pri seriáli spustí ďalšiu nepozretú epizódu |
| `play_episode` | spustí konkrétnu epizódu, napr. S02E05 |
| `continue_watching` | rozpozerané videá a ďalšie epizódy |
| `whats_playing` | čo práve beží, pozícia, dostupné zvukové stopy a titulky |
| `set_subtitles` | titulky: `off`, jazyk (`slovenčina`, `cz`, `eng`) alebo číslo stopy |
| `set_audio` | dabing (zvuková stopa) podľa jazyka alebo čísla |
| `control` | pause / resume / stop / seek / forward / back / next / previous |
| `show_message` | napíše správu na TV (bublina v rohu, aj cez bežiaci film, nepreruší ho; max 300 znakov, 1–30 s) |
| `tv_tips_today` | čo dnes dávajú v TV (ČSFD „TV tipy“) a čo z toho máš v knižnici – to sa dá hneď pustiť |
| `recommend_tonight` | kandidáti na večer: nevidené filmy z knižnice podľa ČSFD hodnotenia, nálady (žánru) a dĺžky; vyberá Claude |
| `request_on_seerr` | vyžiada film/seriál, ktorý nemáš, cez Seerr/Jellyseerr (len ak sú nastavené `SEERR_URL` a `SEERR_API_KEY`) |

`tv_tips_today` potrebuje na Jellyfine plugin ČSFD vo verzii, ktorá pri `/Csfd/TvTips` prijme parameter `userId` (volanie s API kľúčom nemá používateľa).

## Nasadenie

### 1. Jellyfin API kľúč
Jellyfin → *Dashboard → API Keys → +*, názov `Claude`.

### 2. Tajný kľúč pre URL (PowerShell)
```powershell
[Convert]::ToBase64String((1..32 | % {[byte](Get-Random -Max 256)})) -replace '[+/=]',''
```
Výsledok je `MCP_SECRET`. Nikam ho necommituj. Kto pozná celú URL, môže ovládať tvoju TV.

### 3. Tailscale (admin konzola)
- *DNS*: zapni **HTTPS Certificates**.
- *Access controls*: politika musí povoľovať Funnel. Ak tam ešte nie je, pridaj:
  ```json
  "nodeAttrs": [{ "target": ["autogroup:member"], "attr": ["funnel"] }]
  ```
- *Settings → Keys → Generate auth key*: nie ephemeral, nie reusable. To je `TS_AUTHKEY`.

### 4. Priečinky na NAS (SSH)
```bash
mkdir -p /volume1/docker/jellyfin-mcp/ts-state
curl -fsSL -o /volume1/docker/jellyfin-mcp/serve.json \
  https://raw.githubusercontent.com/Yossarian222/Wholphinix/main/tools/jellyfin-mcp/serve.json
```

### 5. Portainer
*Stacks → Add stack → Repository*
- Repository URL: `https://github.com/Yossarian222/Wholphinix`
- Reference: `refs/heads/main`
- Compose path: `tools/jellyfin-mcp/docker-compose.yml`
- Environment variables: `TS_AUTHKEY`, `JELLYFIN_API_KEY`, `JELLYFIN_USER` (tvoje meno v Jellyfine), `MCP_SECRET`
- Voliteľne: `SEERR_URL` (napr. `http://192.168.1.201:5055`) a `SEERR_API_KEY` (Seerr → *Settings → General → API Key*) pre nástroj `request_on_seerr`; bez nich nástroj odpovie „Seerr nie je nastavený“
- Voliteľne: `TARGET_DEVICE` (časť názvu zariadenia, ak máš viac TV), `MCP_HOST` (adresa, na ktorej server počúva; predvolene `127.0.0.1`, čo stačí, lebo kontajner zdieľa sieť s Tailscale kontajnerom a Funnel posiela požiadavky na `127.0.0.1:8765`; `0.0.0.0` nastav len pri inom sieťovom zapojení)

Po nasadení otvor `https://jellyfin-mcp.platypus-vimba.ts.net/health`. Má vrátiť `ok`.

### 6. Claude
V Claude pridaj vlastný konektor (*Settings → Connectors → Add custom connector*):
- Názov: `Jellyfin TV`
- URL: `https://jellyfin-mcp.platypus-vimba.ts.net/<MCP_SECRET>/mcp`

Konektor sa zobrazí aj v appke na mobile. Potom stačí napísať alebo povedať napríklad „pusti Pulp Fiction“, „daj slovenské titulky“, „prepni na český dabing“ alebo „vráť o pol minúty“.

## Ovládanie z mobilu (hlasom)

Server je obyčajný vzdialený MCP konektor, takže funguje aj v mobilnej Claude appke (Android/iOS), vrátane hlasového režimu.

1. Na [claude.ai](https://claude.ai) otvor *Settings → Connectors → Add custom connector*.
2. Názov napr. `Jellyfin TV`, URL: `https://<ts-host>/<MCP_SECRET>/mcp`
   – `<ts-host>` je Funnel adresa kontajnera (u nás `jellyfin-mcp.platypus-vimba.ts.net`), `<MCP_SECRET>` tajný kľúč z kroku 2. Cesta je presne `/<MCP_SECRET>/mcp` (bez lomky na konci nevadí ani s ňou).
3. Konektor sa synchronizuje do všetkých Claude áp prihlásených pod tým istým účtom. V mobilnej appke ho zapni v konverzácii (ikona nástrojov / *Connectors*), prípadne mu povoľ nástroje natrvalo, aby sa nepýtal pri každom príkaze.
4. Klepni na ikonu hlasového režimu a hovor. Príklady:
   - „Pusti Pelíškov s českým dabingom.“
   - „Čo dnes dávajú v telke? Čo z toho mám doma?“
   - „Napíš na telku, že večera je hotová.“
   - „Mám chuť na nejakú komédiu, max hodinu a pol. Čo si pozrieť?“
   - „Daj slovenské titulky.“ / „Vráť o pol minúty.“ / „Pauza.“
   - „Pelíšky nemám, stiahni ich.“ (cez Seerr)

Wholphinix musí byť na TV otvorený a prihlásený, inak Claude odpovie, že TV nie je pripojená.

## Appka v mobile („Telka“)

Najjednoduchší spôsob bez Mety a bez Claude appky: server má vlastnú malú webovú appku (PWA), ktorú si pridáš na plochu telefónu ako normálnu aplikáciu.

- **Chat s Claude** (rovnaké nástroje ako konektor), odpovede si môžeš nechať čítať nahlas 🔊
- **Diktovanie** – tlačidlo mikrofónu (rozpoznávanie reči v Chrome, po slovensky)
- **Čo beží** – názov, pozícia, pauza; obnovuje sa každých 10 s
- **Diaľkové ovládanie** – ⏮ −30 s ⏯ +30 s ⏭ ⏹ (priamo, bez Clauda, takže okamžite a zadarmo)
- rýchle tlačidlá („Čo dnes dávajú v TV?“, „Tip na večer“…) a „Nový rozhovor“ v menu ⋮

Inštalácia:
1. V Portaineri musí byť nastavený `ANTHROPIC_API_KEY` (rovnaký ako pre WhatsApp; voliteľne `CLAUDE_MODEL`, `CLAUDE_EFFORT`). Bez neho funguje len ovládanie a stav, chat povie, že kľúč chýba.
2. Na telefóne otvor v **Chrome** `https://<ts-host>/<MCP_SECRET>/app` (u nás `https://jellyfin-mcp.platypus-vimba.ts.net/<MCP_SECRET>/app`).
3. Menu ⋮ v Chrome → **Pridať na plochu / Inštalovať aplikáciu**. Na ploche pribudne ikona „Telka“, otvára sa na celú obrazovku.
4. Pri prvom klepnutí na mikrofón povoľ prístup k mikrofónu.

Rozhovor si server pamätá 30 minút (posledných 10 výmen), história správ ostáva aj v telefóne. Appku vypneš premennou `PHONE_APP=0`. Odkaz obsahuje `MCP_SECRET` – neposielaj ho nikomu; po zmene `MCP_SECRET` treba appku otvoriť z novej adresy a znovu pridať na plochu.

## WhatsApp

Voliteľne môžeš TV ovládať aj správami na WhatsApp: napíšeš „pusti Pelíšky“ na číslo bota a odpovie ti Claude, ktorý použije tie isté nástroje ako MCP konektor. Používa oficiálne **WhatsApp Business Cloud API** od Mety a **Anthropic API** (platíš za tokeny podľa [cenníka](https://www.anthropic.com/pricing), bežný príkaz sú zlomky centa).

```
WhatsApp ──▶ Meta Cloud API ──webhook──▶ Tailscale Funnel ──▶ jellyfin-mcp /<MCP_SECRET>/whatsapp ──▶ Claude API
                                                                        └──▶ nástroje (Jellyfin, Seerr) ──▶ odpoveď cez Graph API
```

### Nastavenie v Mete
1. Na [developers.facebook.com](https://developers.facebook.com) → *My Apps → Create App* → typ **Business** (use case „Connect with customers through WhatsApp“), priraď ju k svojmu Business portfóliu (ak ho nemáš, vytvorí sa).
2. V appke pridaj produkt **WhatsApp** → *API Setup*:
   - Na skúšanie stačí **testovacie číslo** od Mety (zadarmo; správy môže posielať len na max. 5 overených čísel, ktoré pridáš v *To* → *Manage phone number list*).
   - Na trvalé použitie pridaj **vlastné číslo** (*Add phone number*). Číslo nesmie byť zaregistrované v bežnom WhatsAppe ani WhatsApp Business appke (najprv by si ho musel odtiaľ zmazať) – ideálne nová SIM/eSIM alebo pevná linka s overením hovorom.
   - Z *API Setup* si zapíš **Phone number ID** → `WHATSAPP_PHONE_NUMBER_ID` (nie samotné telefónne číslo).
3. **Trvalý token** (dočasný z *API Setup* vyprší za 24 h): [business.facebook.com](https://business.facebook.com) → *Settings → Users → System users → Add* (rola Admin) → *Assign assets* → tvoja appka (Full control) a WhatsApp účet (Full control) → *Generate new token* → appka, expirácia **Never**, oprávnenia `whatsapp_business_messaging` a `whatsapp_business_management` → `WHATSAPP_TOKEN`.
4. *App settings → Basic → App secret → Show* → `WHATSAPP_APP_SECRET` (ním Meta podpisuje webhooky, server bez platného podpisu vráti 403).
5. Vymysli si náhodný reťazec (napr. rovnakým príkazom ako `MCP_SECRET`) → `WHATSAPP_VERIFY_TOKEN`.
6. Doplň premenné v Portaineri (nižšie) a stack nasaď znova, až potom pokračuj.
7. *WhatsApp → Configuration → Webhook → Edit*:
   - Callback URL: `https://<ts-host>/<MCP_SECRET>/whatsapp` (u nás `https://jellyfin-mcp.platypus-vimba.ts.net/<MCP_SECRET>/whatsapp`)
   - Verify token: hodnota `WHATSAPP_VERIFY_TOKEN` → *Verify and save* (server vráti `hub.challenge`)
   - *Webhook fields → Manage* → zapni **`messages`**.
8. Pre trvalú prevádzku s vlastným číslom prepni appku do režimu **Live** (*App Mode*; Meta pýta URL zásad ochrany súkromia – stačí jednoduchá stránka). Na skúšanie s testovacím číslom stačí režim Development.
9. Napíš z povoleného čísla na číslo bota, napr. „čo beží na telke?“.

### Premenné (Portainer)
| Premenná | Povinná | Popis |
|---|---|---|
| `WHATSAPP_TOKEN` | áno | trvalý token System Usera |
| `WHATSAPP_PHONE_NUMBER_ID` | áno | Phone number ID z *API Setup* |
| `WHATSAPP_APP_SECRET` | áno | App secret (overenie podpisu `X-Hub-Signature-256`) |
| `WHATSAPP_VERIFY_TOKEN` | áno | tvoj náhodný reťazec pre overenie webhooku |
| `WHATSAPP_ALLOWED_NUMBERS` | áno | čísla, ktoré smú ovládať TV, čiarkou oddelené, s predvoľbou bez `+`, napr. `421905123456,421911222333` |
| `ANTHROPIC_API_KEY` | áno | kľúč z [platform.claude.com](https://platform.claude.com) → *API Keys* |
| `CLAUDE_MODEL` | nie | predvolene `claude-sonnet-5-5` (dobrý pomer cena/výkon pre nástroje); lacnejšie `claude-haiku-5-5` |
| `CLAUDE_EFFORT` | nie | `low` (predvolené, rýchle odpovede), `medium`, `high` |
| `GRAPH_VERSION` | nie | verzia Graph API, predvolene `v23.0`; ak ju Meta označí za zastaranú, nastav novšiu (vidno ju v *API Setup* v ukážke `curl`) |
| `STT_URL` | nie | endpoint na prepis hlasoviek (OpenAI-kompatibilné `/v1/audio/transcriptions`), predvolene `https://api.openai.com/v1/audio/transcriptions`; pre lokálny Whisper napr. `http://192.168.1.201:8000/v1/audio/transcriptions` |
| `STT_API_KEY` | nie | API kľúč pre `STT_URL` (pri OpenAI povinný, lokálny server ho zvyčajne nepotrebuje) |
| `STT_MODEL` | nie | model prepisu, predvolene `whisper-1` (OpenAI); lokálne napr. `Systran/faster-whisper-small` |
| `STT_LANGUAGE` | nie | jazyk hlasoviek (ISO 639-1), predvolene `sk` |

Kým nie je nastavených prvých šesť, endpoint `/whatsapp` vracia 404 a zvyšok servera beží ako doteraz. Hlasovky sú zapnuté, len keď je nastavený `STT_API_KEY` alebo vlastný `STT_URL`.

### Hlasové správy
Bot rozumie aj hlasovkám: stiahne ich cez Graph API (max. 16 MB), prepíše cez Whisper a spracuje rovnako ako text. Odpoveď začína riadkom „Rozumel som: „…““, aby si hneď videl, či ťa počul správne. Bez nastaveného prepisu odpovie, že hlasovky nemá zapnuté a nech napíšeš textom.

- **OpenAI** (najjednoduchšie): na [platform.openai.com](https://platform.openai.com) → *API keys* vytvor kľúč → `STT_API_KEY`. Viac netreba (`whisper-1`, slovenčina). Cena je približne 0,006 $ za minútu zvuku.
- **Lokálny Whisper** (zadarmo, zvuk neopustí NAS): spusti OpenAI-kompatibilný server, napr. [speaches](https://github.com/speaches-ai/speaches) (nástupca `fedirz/faster-whisper-server`), a nastav `STT_URL` (kľúč netreba):

  ```yaml
  services:
    whisper:
      image: ghcr.io/speaches-ai/speaches:latest-cpu   # alebo fedirz/faster-whisper-server:latest-cpu
      container_name: whisper
      ports:
        - "8000:8000"
      volumes:
        - /volume1/docker/whisper/cache:/home/ubuntu/.cache/huggingface
      restart: unless-stopped
  ```

  `STT_URL=http://192.168.1.201:8000/v1/audio/transcriptions`, `STT_MODEL=Systran/faster-whisper-small` (na slabšom CPU `…-base`, presnejšie `…-medium`; prvý prepis model stiahne, takže potrvá dlhšie). speaches môže vyžadovať model najprv stiahnuť: `curl -X POST http://192.168.1.201:8000/v1/models/Systran/faster-whisper-small`.

Zvuk ani prepis sa nikam neukladajú a do logu ide len dĺžka prepisu.

### Ako sa správa
- Odpovedá len na čísla z `WHATSAPP_ALLOWED_NUMBERS`, ostatné ticho ignoruje (v logu je len posledné trojčíslie).
- Pamätá si posledných 10 výmen s každým číslom; po 30 minútach ticha začína odznova (pamäť je len v procese, reštart ju zmaže).
- Prijatú správu označí ako prečítanú (modré fajky), Meta dostane odpoveď hneď a spracovanie beží na pozadí; opakované doručenie tej istej správy sa ignoruje.
- Rozumie textu a hlasovkám (ak je zapnutý prepis, viď vyššie); na obrázok či nálepku odpovie „Zatiaľ rozumiem len textu 🙂“.
- Pri chybe Clauda alebo Jellyfinu pošle krátke ospravedlnenie.

### Cena
- **WhatsApp**: keď píšeš ty botovi, otvorí sa 24-hodinové okno zákazníckej starostlivosti a odpovede (service/free-form správy) v ňom sú **zadarmo**. Bot sám nikdy nezačína konverzáciu (na to by potreboval platené šablóny), takže pri bežnom používaní neplatíš Mete nič. Aktuálne podmienky: [developers.facebook.com/docs/whatsapp/pricing](https://developers.facebook.com/docs/whatsapp/pricing).
- **Claude**: každá správa = 1 až 6 volaní API. Systémový prompt a definície nástrojov sa cachujú (prompt caching), takže opakované volania sú lacnejšie.

### Funnel
`serve.json` posiela celý koreň (`/`) na `127.0.0.1:8765`, takže cesta `/<MCP_SECRET>/whatsapp` prejde bez zmeny. Webhook je chránený tajnou cestou aj podpisom Mety.

## Bezpečnosť
- Verejne dostupný je len tento jeden endpoint (Funnel). NAS, Jellyfin ani ostatné služby verejné nie sú.
- Bez správneho tajného kľúča v ceste server vráti 404. Kľúč sa nezapisuje do logov.
- Nástroje vedia len vyhľadávať v knižnici a ovládať prehrávanie, nič nemažú ani nemenia. **Jellyfin API kľúč však má plné administrátorské práva** (vie mazať, meniť používateľov, nastavenia servera…). Chráň ho rovnako ako heslo admina a drž v tajnosti aj `MCP_SECRET` – kto pozná URL, dostane sa k nástrojom.
- Id položiek od Clauda sa pred vložením do URL Jellyfin API overujú (musia to byť Jellyfin GUID), ostatné hodnoty idú ako query parametre.
- Server ovláda len reláciu Wholphinixu prihláseného používateľa `JELLYFIN_USER`. TV ostatných členov domácnosti (iní Jellyfin používatelia) neovláda; ak taká relácia nie je, nástroj hlási, že Wholphinix nie je pripojený.
- WhatsApp webhook prijme len požiadavky s platným podpisom Mety (`WHATSAPP_APP_SECRET`) a vykoná len správy z `WHATSAPP_ALLOWED_NUMBERS`. Tokeny, celé čísla ani text správ sa na úrovni INFO nelogujú.
- Appka v mobile (`/<MCP_SECRET>/app`) je chránená tým istým tajným kľúčom v ceste; posiela `Referrer-Policy: no-referrer`, aby sa adresa nedostala ďalej, a text správ sa nezapisuje do logov.
- Ak `MCP_SECRET` unikne, zmeň ho v Portaineri a uprav URL konektora v Claude (a Callback URL webhooku v Mete, ak používaš WhatsApp; appku v mobile pridaj na plochu z novej adresy). Ak unikne API kľúč, zmaž ho v Jellyfine (*Dashboard → API Keys*) a vytvor nový.

## Vývoj
```bash
pip install ".[test]" && pytest -q
```
