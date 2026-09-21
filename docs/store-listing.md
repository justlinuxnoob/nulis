# Store listing (draft)

A draft for Google Play. Nothing here is published. Character limits are Play's; the counts in
brackets are what the text below actually uses.

---

## App name

> **Nulis** *(5 / 30)*

Alternative if the bare name is taken: **Nulis Launcher** *(14 / 30)*.

## Short description

*Limit 80 characters. This is the line people read in search results, so it has to say what the
app is and what makes it different, in that order.*

> **A home screen made of blocks. Deeply customisable, and fully offline.** *(69 / 80)*

Alternates:

- `Minimal launcher built from blocks. No internet permission, no accounts.` *(72)*
- `Build your home screen from blocks. Offline forever, no tracking.` *(65)*
- `A quiet, block-based launcher. Nothing it knows leaves your phone.` *(66)*

## Full description

*Limit 4000 characters.*

---

Nulis is a home screen made of blocks.

A page is exactly one screen — a grid — and a block is a rectangle on it: a clock, the date, your
apps, what is playing, a note you are in the middle of, a countdown, a real Android widget, a cat
walking across the screen. Each block has several styles.

Long-press anywhere and you are editing the real page: drag a block and its neighbours make room,
pull a knob or tap a stepper to resize it, and everything you can do to it is in the bar along the
bottom. Undo takes back the last twelve things you did.

It starts as a calm dot-matrix clock on black, and it can end up looking like almost anything.

**BLOCKS**
26 kinds, with several styles each.

• Clock — 18 styles: dot-matrix, stacked, mono, outline, vertical, flip, analog, ring, roman
numerals, binary, words, two timezones
• Date — 8, from one quiet line to a full-page ghost month
• Apps — as many of your apps as the rectangle holds, as a list or a grid
• Widget — one real Android widget, sized with the block, optionally in grey
• Battery, steps, screen time
• Music — whatever is playing, from any player
• Notes, journal, tasks — writing that lives on the home screen
• Countdown, quote of the day, calculator
• Calendar, contacts, one photo
• Focus timer
• Fun — dot cat, Game of Life, a pet, an hourglass

**PAGES**
Up to five, in swipe order, and home is a mark on whichever one you choose. Each has its own
alignment and spacing, and every page is a live miniature of itself before you touch it.

**TWO THINGS TO CHOOSE BETWEEN, NOT THREE**
A layout says what is on which page. A look and its colours say how it is drawn. They are
independent, so any layout works with any look — and "save this setup" keeps the pair under a name
you can come back to. Eight layouts ship: Minimal, Compact dashboard, Just a clock, Bottom dock,
Stats, Writer, Terminal, Paper.

**TYPOGRAPHY**
Six open-licensed faces. Pick the display face and the body face separately, scale every piece of
text at once, and decide whether small captions shout or whisper.

**APP DRAWER**
Search, an A–Z rail, your own groups and hidden apps. Apps you have not opened in months quietly
rest out of the way. The search field is also a calculator, and it finds your notes, your open
tasks and any settings screen by name. Keep it on the swipe up, give it a page of its own on
either side, or turn it off and have that gesture back.

**GESTURES**
Swipes, taps and long-presses are yours to assign — open an app, open the drawer, pull down
notifications, edit the page, or nothing at all.

**LESS PHONE**
A mindful pause before the apps you choose. A focus mode that dims everything else. A weekly
summary. No Accessibility Service is used for any of it.

**OFFLINE FOREVER**
Nulis has no internet permission. Not "we don't collect data" — it structurally cannot send
anything anywhere. No analytics, no crash reporting, no ads, no account, no sync, no update check.
Everything it knows sits in its own storage and disappears when you uninstall it.

Four permissions are optional and asked for only behind a screen explaining what they are for:
usage access for screen time, activity recognition for steps, read-only calendar access for the
agenda, and notification access for the music block and the optional notification dot — which
takes the name of the app a notification came from and nothing else, no count, no sender, no
preview. Decline any of them and everything else still works. The contacts and photo blocks use
Android's own pickers, so they need no permission at all.

Back everything up to a plain text file whenever you like, through the system file picker.

No ads. No subscription. No account.

---

## Feature bullets

*For the store's feature list and for a README-style summary. Short, concrete, no adjectives doing
work a noun could do.*

- A home screen built from blocks — 26 kinds, many styles each
- 18 clock styles, from dot matrix to Roman numerals to binary
- Real Android widgets, hosted in a block and resized with it
- Eight layouts and two looks, chosen separately, previewed live
- Up to five pages, with home wherever you mark it
- Six open-licensed fonts, display and body chosen separately
- Drawer with search, A–Z rail, your own groups, hidden and rested apps
- Search that is also a calculator and finds your notes and settings
- Remappable swipe, tap and long-press gestures
- Mindful pause, focus mode and a weekly summary — no Accessibility Service
- Backup and restore to a plain text file you own
- No internet permission. No analytics, no account, no ads.

## Categories and tags

- Category: **Personalisation**
- Tags: launcher, home screen, minimal, customisation, digital wellbeing, offline
- Content rating: everyone
- Contains ads: no
- In-app purchases: no

## Data safety form (Play)

Every answer is the same one:

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data encrypted in transit?** Not applicable — no data is transmitted.
- **Do you provide a way for users to request that their data is deleted?** Uninstalling the app
  deletes all of it; so does clearing its data.

Link the privacy policy to `PRIVACY.md` (or wherever it is hosted; it needs a public URL).

## Graphics needed

Play requires these before a listing can be submitted.

| Asset | Size | Status |
|---|---|---|
| App icon | 512 × 512 PNG | **[`play-icon-512.png`](play-icon-512.png)** — rendered from the same path data as `ic_launcher_foreground.xml`, so the two cannot drift |
| Feature graphic | 1024 × 500 | **[`feature-graphic-1024x500.png`](feature-graphic-1024x500.png)** — a draft: the mark, the wordmark in Doto, the short description in Geist Mono. Replace it if you want something with a screenshot in it |
| Phone screenshots | 2–8, min 1080 px on the short side | **[`screenshots/`](screenshots)** — six, 1080 × 2400, taken on a stock Android 16 emulator with no accounts and nobody's own apps on it |
| Tablet screenshots | optional | Not made |

Suggested screenshot order: default home · the editor · the block picker · the layout picker ·
the drawer · settings.

## Open

- **Licence.** GPL-3.0-or-later. A GPL app can be listed on Play, but the store's own terms and
  the licence have to be read together before submitting.
- The privacy policy URL is the `PRIVACY.md` in the public repository.
