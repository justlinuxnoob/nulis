# Store listing

Final text for Google Play, ready to paste. Character limits are Play's; the counts are what the
text actually uses (checked with `wc -m`, emoji-free, plain ASCII punctuation except the dashes).

---

## App name *(max 30)*

> **Nulis Launcher** *(14)*

"Launcher" is in the name because it is what people type into the store's search box. The app
itself calls itself Nulis everywhere.

## Short description *(max 80)*

> **A calm home screen made of blocks. Deeply customisable, offline forever.** *(72)*

## Full description *(max 4000)*

*(3,348 characters.)*

---

Nulis is a home screen made of blocks.

A page is exactly one screen: a grid. A block is a rectangle on it - a clock, the date, your apps,
what is playing, today's steps, a note, your habits, a real Android widget. Put them where you
like, make them the size you like, and leave empty space where you want calm. Nothing scrolls,
nothing is taller than the screen.

It starts as a quiet dot-matrix clock on black. It can end up looking like almost anything.

EDIT THE REAL PAGE
Hold anywhere and the page itself becomes editable. Drag a block and its neighbours make room.
Pull a corner to resize. Tap for its styles and options. Undo takes back the last dozen changes.
There is no separate "edit screen" to learn - what you arrange is what you get.

27 KINDS OF BLOCK, MANY STYLES EACH
- Clock: dot matrix, flip, analog, roman numerals, binary, words, two time zones and more
- Date, from one quiet line to a whole month
- Apps: as many as the block holds, as a list or a grid
- Habits: a row of dots for the week, tap to tick today
- Steps, screen time (time in apps and time with the screen on), battery
- Music: whatever is playing, from any player, with its controls
- Notes, journal and tasks that live on the home screen
- Calendar agenda, countdowns, a quote, a calculator, contacts, one photo
- Focus timer and a breathing exercise
- A real Android widget, sized with its block
- And a few for fun: a dot-matrix cat, the Game of Life, a pet, an hourglass

LOOKS AND COLOURS
Two looks - Dot and Clean - on black, white, your own colour, or following your phone's dark mode.
Ready-made palettes, one drawn from your wallpaper. Six open-licensed typefaces, a text size
setting and a higher-contrast option. App icons as text, letters, icons or pixel art, with icon
pack support.

LAYOUTS AND PAGES
Up to five pages, with home on whichever one you choose. Eight ready layouts to start from.
Save a whole setup - pages, look and colours - under a name and come back to it.

APP DRAWER
Swipe up: search, an A-Z rail, your own groups and hidden apps. The search is also a
calculator and finds your notes and settings. Or give the drawer a page of its own.

LESS PHONE, IF YOU WANT IT
A mindful pause before the apps you choose, gentle daily limits, a focus mode and a weekly
summary. Nothing is blocked; it just gives you a breath.

GESTURES
Swipes, double taps and long presses do what you tell them: open an app, the drawer, the
notification shade, a new note, or nothing at all.

FOR EVERYONE
Screen reader labels throughout, large text that fits, and blocks that grow with a tablet's
screen.

OFFLINE FOREVER
Nulis has no internet permission. Not "we don't collect data" - it cannot send anything
anywhere, and Android enforces that. No ads, no analytics, no crash reporting, no account, no
sync. Everything it knows stays in its own storage, and back it up to a plain file whenever you
like.

Four permissions are optional, each asked for only when you turn on the block that needs it,
behind a screen that says what it is for: usage access (screen time), physical activity
(steps), calendar (agenda, read-only) and notification access (music controls, and an optional
notification dot that knows only which app something came from). Decline any of them and
everything else works.

Free and open source under the GPL. No ads. No subscription.

---

## Release notes for 1.0.0 *(max 500)*

> First release on Google Play. A home screen made of blocks: edit the real page, 27 kinds of block, two looks, habits, screen time, widgets, and no internet permission at all.

## Category and tags

- App category: **Personalization**
- Tags (pick up to five in Play Console): Launcher, Customization, Minimalist, Productivity,
  Digital wellbeing
- Contact email: the owner's; Play requires one. A website is optional: the GitHub repository.
- Privacy policy URL: `https://github.com/justlinuxnoob/nulis/blob/main/PRIVACY.md`

## Graphics

| Asset | Spec | File |
|---|---|---|
| App icon | 512 x 512, 32-bit PNG | [`icon-512.png`](icon-512.png) |
| Feature graphic | 1024 x 500, PNG | [`feature-graphic-1024x500.png`](feature-graphic-1024x500.png) - drawn by `StoreGraphics` from the app's own pages and type |
| Phone screenshots | 2-8, 1080 x 2400 | [`screenshots/`](screenshots) - eight, recorded by `StoreScreenshots` and `StoreScreenshotsApp` |

Screenshot order, as numbered: home, the stats page, the writing page, habits, the editor, the
block picker, the Clean look on white, the drawer. Regenerate them after a visual change with:

```
./gradlew recordRoborazziPlayDebug --tests '*Store*'
```

Every one shows the default layout (plus one habits page) at 9:41 on a demo day, with generic
apps that belong to nobody.
