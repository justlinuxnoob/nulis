# Data safety, permissions and policy answers

Everything Play Console asks about data and permissions, answered for Nulis 1.0.0, with the
reasoning so the answers can be re-checked when the app changes. The short version: Nulis has no
internet permission, so it cannot collect or share anything, and Play's form says exactly that.

## Data safety form

Play Console > your app > **Policy and programs > App content > Data safety**.

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | "Collect" in Play's sense means transmitting data off the device. Nulis does not declare `android.permission.INTERNET`; Android refuses it a network socket. Data processed only on the device is not "collected". |
| Is all of the user data collected by your app encrypted in transit? | *(not asked once you answer No)* | Nothing is transmitted. |
| Do you provide a way for users to request that their data is deleted? | *(not asked once you answer No)* | Uninstalling, or Settings > Apps > Nulis > Clear storage, deletes everything. In-app: Settings > Backup > Start again. |

The resulting label reads **"No data collected"** and **"No data shared with third parties"**.

Play's help page on this: data that stays on the device, and data an app hands to another app
through a system intent the user starts (the browser for the two links in About, the file picker
for backups), is not collection.

## Permissions and why each one is there

Only the four `uses-permission` lines below are in the release manifest (checked with
`aapt2 dump permissions` on both flavors). `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is added by
AndroidX itself, is private to the app, and needs no declaration.

### `android.permission.PACKAGE_USAGE_STATS` (usage access)

Not on Play's list of permissions that need a declaration form, but it is a special access and
reviewers ask about it. If asked, or in the app description of the **App access** section:

> Nulis is a home screen (launcher). With the user's permission, granted by hand in Android's
> "Usage access" settings after Nulis explains what it is for, it reads how long each app was in
> the foreground today and when each app was last opened. This powers the optional Screen time
> block (today's total and the apps that took it), the weekly summary, the optional per-app
> minutes in the app list, "rest apps unused for N days", and the optional daily limits and
> mindful pause. The data is read on the device, shown on the device, and never leaves it: the app
> has no internet permission. Declining the permission leaves every other feature working.

### `QUERY_ALL_PACKAGES`

**Not requested.** Nulis finds the apps to list through a `<queries>` element for
`MAIN`/`LAUNCHER` intents (plus icon-pack and default-app intents), which gives it exactly the apps
that have an icon in a launcher and nothing else. Play Console therefore does not show the
"All package visibility" declaration. If a future version ever needs the permission, launchers are
a permitted core use; the justification would be:

> Nulis is a launcher: listing, searching and opening every installed app a user can launch is
> the app's core purpose. It uses the list only on the device, to draw the app drawer and the Apps
> block, and never transmits it.

### `android.permission.ACTIVITY_RECOGNITION` (physical activity)

Runtime permission, asked the first time the user taps the Steps block, behind Nulis's own
explanation screen. Reads the phone's hardware step counter to show today's steps and a week of
totals. No location permission is declared at all.

### `android.permission.READ_CALENDAR`

Runtime permission, asked only for the Calendar block's agenda styles. Read-only; Nulis declares
no write permission. The week-strip style works without it.

### `android.permission.EXPAND_STATUS_BAR`

A normal permission granted at install. Lets a gesture pull down the notification shade or quick
settings. Reads nothing.

### Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE` on the service)

Not a permission the app holds: the system binds the service only after the user turns Nulis on
in "Notification access" settings, behind Nulis's explanation screen. It is used for the Music
block (the playing track's title, artist, cover and transport buttons, which Android only exposes
to notification listeners) and the optional notification dot, which takes only the package name
of the app a notification came from - never its title, text, sender or count - and keeps it in
memory only. Play does not require a declaration for notification listeners; the explanation
above is what to give if a reviewer asks.

### Not used

No Accessibility Service, no `SYSTEM_ALERT_WINDOW`, no `MANAGE_EXTERNAL_STORAGE`, no location, no
contacts permission (the Contacts block uses the system picker), no media permissions (the Photo
block uses the system document picker), no exact alarms (the nightly steps alarm is inexact:
`setAndAllowWhileIdle`), no foreground service, no `BIND_APPWIDGET` (widgets go through the
system's own picker).

## Other App content answers

| Section | Answer |
|---|---|
| Privacy policy | `https://github.com/justlinuxnoob/nulis/blob/main/PRIVACY.md` |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access (no login). Mention that usage access and notification access are optional and granted in system settings. |
| Content rating | Questionnaire category "All other app types"; answer No to every content question. Expected rating: Everyone / PEGI 3. |
| Target audience | 13 and over (do not select any under-13 age group: it pulls in the Families policy, which a launcher has no reason to be under) |
| News app | No |
| COVID-19 contact tracing or status | No |
| Data safety | As above |
| Government app | No |
| Financial features | None |
| Health apps | **No** - a step count and a habit tracker shown to the user on the device are not a health app under Play's definition; nothing is diagnosed, treated or transmitted |
| Advertising ID | No, the app does not use an advertising ID |

## Payments policy

The Play build (`play` flavor) contains no tip, donation or payment link of any kind; the
"Buy me a coffee" row and its URL exist only in the `github` flavor's source set
(`app/src/github`), so they are not in the Play APK at all (checked: the URL string is absent from
the Play build's dex). The "Source code on GitHub" link is allowed: it is not a payment flow.
