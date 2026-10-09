# jellyfin-mcp – ovládanie TV cez Claude

MCP server, cez ktorý Claude (appka na mobile aj PC) ovláda Jellyfin na TV. Spúšťa filmy a seriály, prepína titulky a dabing, pauzuje a posúva prehrávanie.

```
Claude (cloud) ──HTTPS──▶ Tailscale Funnel ──▶ jellyfin-mcp (NAS) ──▶ Jellyfin ──websocket──▶ Wholphinix (RPi5)
```

**Požiadavky na TV:** Wholphinix musí byť z vetvy, ktorá obsahuje commit „Accept remote PlayNow…“, a musí byť otvorený v popredí. Wholphin drží spojenie so serverom len vtedy, keď je appka aktívna.

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
  https://raw.githubusercontent.com/Yossarian222/Wholphinix/feature/remote-control/tools/jellyfin-mcp/serve.json
```

### 5. Portainer
*Stacks → Add stack → Repository*
- Repository URL: `https://github.com/Yossarian222/Wholphinix`
- Reference: `refs/heads/feature/remote-control` (po zlúčení `refs/heads/main`)
- Compose path: `tools/jellyfin-mcp/docker-compose.yml`
- Environment variables: `TS_AUTHKEY`, `JELLYFIN_API_KEY`, `JELLYFIN_USER` (tvoje meno v Jellyfine), `MCP_SECRET`

Po nasadení otvor `https://jellyfin-mcp.platypus-vimba.ts.net/health`. Má vrátiť `ok`.

### 6. Claude
V Claude pridaj vlastný konektor (*Settings → Connectors → Add custom connector*):
- Názov: `Jellyfin TV`
- URL: `https://jellyfin-mcp.platypus-vimba.ts.net/<MCP_SECRET>/mcp`

Konektor sa zobrazí aj v appke na mobile. Potom stačí napísať alebo povedať napríklad „pusti Pulp Fiction“, „daj slovenské titulky“, „prepni na český dabing“ alebo „vráť o pol minúty“.

## Bezpečnosť
- Verejne dostupný je len tento jeden endpoint (Funnel). NAS, Jellyfin ani ostatné služby verejné nie sú.
- Bez správneho tajného kľúča v ceste server vráti 404. Kľúč sa nezapisuje do logov.
- Server vie len vyhľadávať v knižnici a ovládať prehrávanie, nič nemaže ani nemení.
- Ak kľúč unikne, zmeň `MCP_SECRET` v Portaineri a uprav URL konektora v Claude.

## Vývoj
```bash
pip install ".[test]" && pytest -q
```
