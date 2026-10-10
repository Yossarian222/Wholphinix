# Privacy Policy – Wholphinix TV (WhatsApp bot)

Last updated: 10 October 2026

Wholphinix TV is a private, non-commercial home project. Its WhatsApp bot lets the owner and up to a few family
members control their own media server and TV at home (e.g. "play a movie", "pause", "what is playing?").

## What data is processed

- **WhatsApp messages** sent to the bot's number by allow-listed phone numbers (text and, optionally, voice notes),
  together with the sender's phone number.
- Messages from phone numbers that are not on the owner's allow-list are ignored and not stored.

## How the data is used

- Messages are used only to understand and carry out the request on the owner's home media server and to send back
  a reply.
- To understand the request, the message text (and a transcript of a voice note) is sent to an AI service
  (Anthropic Claude API; optionally a speech-to-text service). It is not used for advertising or sold to anyone.

## Storage and retention

- The bot runs on the owner's home server. It keeps only a short in-memory conversation history (last 10 exchanges,
  expires after 30 minutes) so follow-up questions make sense; nothing is written to a database.
- Server logs contain technical information (time, a shortened phone number, request type) and are rotated
  regularly.

## Sharing

Data is not shared with third parties except the services needed to operate the bot: Meta (WhatsApp Cloud API) and
the AI services named above, under their own privacy policies.

## Your rights / data deletion

To stop using the bot or have any data about you removed, contact the owner of this project via GitHub:
https://github.com/Yossarian222. Your number is removed from the allow-list; since no message history is stored
permanently (it expires after 30 minutes), nothing else needs to be deleted.

## Contact

Project owner: https://github.com/Yossarian222
