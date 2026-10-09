# WhatsApp bot – postup nastavenia (checklist)

Stručný postup krok za krokom. Podrobnosti sú v [README → WhatsApp](README.md#whatsapp).

> ⚠️ Token, App Secret a Anthropic API kľúč patria **iba do Portainera**, nikam inam (ani do chatu, ani do repozitára).

## Hotovo

- [x] Meta Business portfólio vytvorené
- [x] Aplikácia na developers.facebook.com s use case „Connect with customers through WhatsApp“
- [x] **Step 1. Try it out** – testovacie číslo pridelené, „Hello World“ prišlo na mobil
  - Phone Number ID → `WHATSAPP_PHONE_NUMBER_ID` (je na stránke Step 1, vedľa testovacieho čísla)
  - Tlačidlo *Generate token* **nestláčať** – dočasný token platí len 24 h

## Zostáva

> 📍 **Stav:** body A–E hotové (stack beží z `refs/heads/main`, test verify URL vrátil `12345`). **Ďalej bod F – webhook v Mete.**

### A) Druhý mobil
- [x] Step 1 → *Send a test message* → pole **To / Add recipient** → pridať druhé číslo a zadať overovací kód z WhatsAppu (max. 5 čísel)

### B) Trvalý token (System User)
- [x] business.facebook.com → **Nastavenia → Users → System users → Add** (meno napr. `lojzo-bot`, rola **Admin**)
- [x] **Assign assets**: *Apps* → appka → **Full control**; *WhatsApp accounts* → WABA → **Full control**
- [x] **Generate new token** → appka, Expiration **Never**, oprávnenia `whatsapp_business_messaging` + `whatsapp_business_management`
- [x] Token skopírovať rovno do Portainera (Meta ho ukáže len raz) → `WHATSAPP_TOKEN`

### C) App Secret
- [x] developers.facebook.com → appka → **App settings → Basic → App secret → Show** → `WHATSAPP_APP_SECRET`

### D) Anthropic API kľúč
- [x] console.anthropic.com → **API Keys → Create key** → `ANTHROPIC_API_KEY`

### E) Portainer – stack `jellyfin-mcp`
- [x] Doplniť premenné:

| Premenná | Hodnota |
|---|---|
| `WHATSAPP_PHONE_NUMBER_ID` | Phone Number ID zo Step 1 |
| `WHATSAPP_TOKEN` | token z bodu B |
| `WHATSAPP_APP_SECRET` | secret z bodu C |
| `WHATSAPP_VERIFY_TOKEN` | vlastný náhodný reťazec (napr. `lojzo-` + 20 náhodných znakov) |
| `WHATSAPP_ALLOWED_NUMBERS` | obe čísla bez `+` a medzier, čiarkou: `421905123456,421911222333` |
| `ANTHROPIC_API_KEY` | kľúč z bodu D |

- [x] **Pull and redeploy** (aby sa stiahol aktuálny kód z `main`)

### F) Webhook (až po redeployi – Meta overuje, či server odpovedá)
- [ ] developers.facebook.com → **Step 2. Production setup → Webhooks** (resp. *WhatsApp → Configuration*)
  - Callback URL: `https://jellyfin-mcp.platypus-vimba.ts.net/<MCP_SECRET>/whatsapp`
  - Verify token: rovnaká hodnota ako `WHATSAPP_VERIFY_TOKEN`
  - **Verify and save**
- [ ] **Webhook fields → Manage** → zapnúť **`messages`**

### G) Test
- [ ] Napísať botovi (testovacie číslo +1 555 650-7675) napr. „čo práve hrá?“
- [ ] Ak nefunguje: screenshot (bez citlivých hodnôt) + posledné riadky logu kontajnera z Portainera

**Step 3 (Business verification) netreba** – testovacie číslo stačí pre max. 5 overených príjemcov.
