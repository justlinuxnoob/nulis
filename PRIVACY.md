# Privacy

**Nothing you do in Nulis leaves your phone.**

This is not a policy about how carefully your data is handled somewhere else. Nulis has no way to
send anything anywhere.

## No network, structurally

Nulis does not declare the `android.permission.INTERNET` permission. On Android that is not a
promise — it is enforced by the operating system. An app without it cannot open a socket, resolve a
hostname or make an HTTP request; the attempt fails at the system level. You can check this
yourself in the manifest, or with `adb shell dumpsys package com.nulis.launcher`.

It follows that there is:

- no analytics or telemetry,
- no crash or error reporting,
- no ads and no ad identifier,
- no account, no sign-in, no cloud sync,
- no update check,
- no remote configuration.

The app also bundles no third-party SDK that could do any of those. Its entire dependency list is
AndroidX core, activity, lifecycle and DataStore; kotlinx-serialization and coroutines; and Jetpack
Compose.

## What is stored, and where

Everything Nulis knows lives in its own private app storage, which no other app can read:

| What | Where |
|---|---|
| Your pages, blocks and their settings | DataStore, one file per page |
| Notes, journal entries, tasks and habits | DataStore |
| Look, colours, fonts, gestures, icon style, saved setups | DataStore |
| App categories, hidden apps, favourites | DataStore |
| Focus sessions, mindful-pause settings, the weekly summary | DataStore |
| Cached app icons | The app's own cache directory |

Uninstalling Nulis deletes all of it. Clearing its data resets it to a fresh install.

There is no hidden file, no database outside the sandbox and no log file written to shared storage.

## Permissions

Four permissions are declared, plus notification access, which is not a permission in the
manifest but a special access you grant in system settings. All but one are optional: Nulis asks
for each one only when you turn on the feature that needs it, behind a screen that explains what
it is for, and everything else keeps working if you decline.

**`PACKAGE_USAGE_STATS`** — the screen-time block, resting apps you have not opened in a while, and
the optional recents row in the drawer. This is a special permission that only you can grant, by
hand, in system settings. Nulis reads how long apps have been in the foreground and when each was
last opened. It reads nothing about what you did inside them.

**`ACTIVITY_RECOGNITION`** — the steps block. Nulis reads the phone's own hardware step counter.
No location is read, ever; Nulis declares no location permission at all.

**`READ_CALENDAR`** — the calendar block's agenda styles, read-only. The week strip works without
it. Nulis never writes to your calendar and declares no write permission.

**`EXPAND_STATUS_BAR`** — lets the "pull down the notification shade" gesture open the shade or
quick settings. Android classifies this as a normal permission and grants it at install with no
prompt. It opens a panel and reads nothing.

**Notification access** — the music block, and the optional notification dot. This is a special
access you grant by hand in system settings, behind Nulis's own explanation screen, and it is the
only way Android lets an app read the media controls a player publishes.

Nulis takes two things from it and nothing else:

- for the music block, the **title, artist and cover** of the track that is playing, and the
  transport buttons that player advertises;
- for the notification dot, **which app a notification came from** — the package name, and only
  while the notification is up.

It never reads the title, the text, the sender, the time or the count of a notification, and it
keeps no history. The set of packages with something waiting lives in memory for as long as the
launcher is running, is written to no file, and is not in your backups. The dot is off until you
turn it on, and turning notification access off in system settings puts every dot out.

Nulis does **not** ask for: contacts, storage, photos, microphone, camera, location, phone, SMS, or
an Accessibility Service.

## Features that touch your other data

**Contacts block.** Uses the system contact picker. You choose each person one at a time, in
Android's own UI, and Nulis receives only that one contact. It never gets access to your address
book, which is why it needs no contacts permission.

**Photo block.** Uses the system document picker. You choose one image; Nulis keeps a long-lived
read grant for that single file and nothing else. It cannot browse your gallery.

**Music block.** Reads the media session the currently playing app publishes — the title, the
artist and the transport controls it advertises. Any player on the phone publishes this to the
system; Nulis is a reader like any lock screen.

**Your wallpaper's palette.** Android works out a wallpaper's main colours for any app that asks,
and Nulis asks when you open Settings, to offer a palette in those colours. It receives two or
three colour values, never the image, and needs no permission to do it. Nothing is stored unless
you apply the palette, and then only as the colours themselves.

**Links in About.** "Source code on GitHub" (and, in the GitHub build only, "Buy me a coffee")
ask Android to open a web page in your browser, the way a link in a message does. The browser
makes that connection, not Nulis, which still has no internet permission.

**Icon packs.** If you install an icon pack, Nulis reads the `appfilter.xml` it ships. No
permission is involved and nothing is sent back to the pack.

**Widget block.** A widget is another app's view running inside Nulis's process, and it is the one
thing on a page that Nulis does not draw. It is bound by the system, on your say-so, through
Android's own widget picker — Nulis cannot add one by itself. What that widget shows, what it
reads and what it sends is governed by the app it came from and by the permissions **that** app
holds, not by Nulis's. A widget belonging to an app with network access can use it. If that
matters to you, the answer is not to place one; Nulis ships without any.

## Backup and restore

Backups are written and read through the system file picker: you choose the file and the folder,
so a backup goes exactly where you put it and nowhere else. The file is plain text you can open and
read. It contains your pages, block settings, writing, saved setups, gestures and preferences —
all the same data listed above. Treat it as you would any personal file: anything you back up to a shared
or synced folder is no longer covered by anything on this page.

## Children

Nulis collects nothing from anyone, of any age.

## Changes

If this ever changes, it changes in this file, in the repository's history, where you can see the
diff. Any future version that added a network permission would be a different app and should be
treated as one.

*Last reviewed for version 1.0.0.*
