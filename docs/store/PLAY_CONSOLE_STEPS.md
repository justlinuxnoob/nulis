# From nothing to Nulis on Google Play

Every step, in order, with where to click. Written for a personal developer account in 2026.
Play Console moves its buttons around now and then; if a label below does not match, the
section it lives in usually still does. Allow about three weeks end to end: most of it is the
14-day closed test Google requires of new personal accounts.

What you need before starting: a Google account you are happy to publish under, a
government-issued ID, a phone for the Play Console app, a card for the one-off fee, a computer
with a JDK (17+) and this repository, and twelve people with Android phones who will try the app.

---

## 1. Create the developer account (day 1, about 30 minutes, then a wait)

1. Sign in to the Google account you will publish under. A separate account for publishing is
   sensible: it is the account that owns the app forever.
2. Go to **https://play.google.com/console/signup**.
3. **Choose an account type**: pick **Yourself** (a personal account). An organisation account
   needs a D-U-N-S number and a company; you do not need one for Nulis.
4. **Developer name**: what appears under the app on its store page, e.g. your name or "Nulis".
5. Fill in **contact details**: a contact email (shown on the store page), a phone number and a
   contact address. Verify the email and phone when the codes arrive.
6. Answer the questions about your experience with Android and Play, and say you plan to publish
   one app, free.
7. **Pay the registration fee** (US$25, one-off).
8. **Verify your identity**: upload a photo of your ID when asked. Your legal name and address
   must match it.
9. **Verify you have a real Android device**: install the **Google Play Console** app on your
   phone, sign in with the same account, and complete the prompt it shows.
10. Wait for verification. This usually takes a day or two; you get an email.

## 2. Make the release key and the bundle (on your own computer, 10 minutes)

Do this on your PC, never on a shared machine and never in a cloud session.

1. `cd` into the repository.
2. Run `scripts/make-release-key.sh`. Choose a password; it writes `~/.nulis-release/release.jks`
   and `~/.nulis-release/keystore.properties`.
3. **Back up `~/.nulis-release/`** somewhere offline and safe (a password manager's file vault, an
   encrypted USB stick). This is your *upload key*; Google holds the key the store signs with.
4. Build the bundle Play wants:
   ```
   ./gradlew bundlePlayRelease
   ```
   The file is `app/build/outputs/bundle/playRelease/app-play-release.aab`.
5. Optional sanity check: `./gradlew assemblePlayRelease` and install
   `app/build/outputs/apk/play/release/app-play-release.apk` on your phone. It is signed with a
   different key from your usual perf build, so uninstall that first, or install it on another phone
   (your pages can travel over with Settings > Backup > Export, then Import).

## 3. Create the app (15 minutes)

1. Play Console > **All apps** > **Create app**.
2. **App name**: `Nulis Launcher`.
3. **Default language**: English (United Kingdom) - the texts use British spelling.
4. **App or game**: App. **Free or paid**: Free.
5. Tick the two **Declarations** (Developer Program Policies, US export laws) and **Create app**.

## 4. Fill in "Set up your app" (1 hour)

Play Console > your app > **Dashboard**. The "Set up your app" list links to each of these; the
answers are all in [`DATA_SAFETY.md`](DATA_SAFETY.md).

1. **Privacy policy**: `https://github.com/justlinuxnoob/nulis/blob/main/PRIVACY.md` (the file must
   be on `main` and the repository public before you submit).
2. **App access**: "All functionality is available without special access". In the note, say that
   usage access and notification access are optional system settings the app explains in-app.
3. **Ads**: No.
4. **Content rating**: Start questionnaire > email > category **"All other app types"** (utility) >
   answer **No** to everything > Save > Submit. Expected: Everyone / PEGI 3.
5. **Target audience and content**: **13-15, 16-17 and 18+** only. Do not tick under-13 groups.
   "Could the app unintentionally appeal to children?" No.
6. **News apps**: No.
7. **Data safety**: "Does your app collect or share any of the required user data types?" **No** >
   Next > Save > Submit. The preview should read "No data collected".
8. **Government apps**: No. **Financial features**: None. **Health**: No.
9. **Advertising ID**: No.

## 5. Store listing (30 minutes)

Play Console > **Grow users** > **Store presence** > **Main store listing**. Everything to paste
is in [`LISTING.md`](LISTING.md).

1. **App name**, **Short description**, **Full description**: paste.
2. **App icon**: upload [`icon-512.png`](icon-512.png).
3. **Feature graphic**: upload [`feature-graphic-1024x500.png`](feature-graphic-1024x500.png).
4. **Phone screenshots**: upload the eight files in [`screenshots/`](screenshots), in number order.
5. Tablet screenshots: optional; skip for the first release.
6. **Save**.
7. **Store settings** (same menu): App category **Personalization**, tags as in `LISTING.md`,
   contact email (required), website `https://github.com/justlinuxnoob/nulis` (optional). **Save**.

## 6. Internal test first (optional, 15 minutes, recommended)

A private track only you and a few named people can install from, with no review wait.

1. **Test and release** > **Testing** > **Internal testing** > **Create new release**.
2. The first time, Play asks about **app signing**: choose **Use Google-generated key** (Play App
   Signing, recommended) and continue. Your key from step 2 becomes the upload key.
3. **Upload** `app-play-release.aab`. Release name: `1.0.0 (1)`. Release notes: from `LISTING.md`.
4. **Next** > **Save** > **Go to overview** > **Send changes for review** if asked, then
   **Testers** tab: create an email list with yourself, **Save**, copy the **opt-in link**.
5. Open the link on your phone, accept, install from Play. Check: it installs, it becomes your home
   screen, a backup from your current Nulis imports cleanly.

## 7. Closed test with 12 testers for 14 days (required for new personal accounts)

Google will not let a personal account created after November 2023 publish to production until
an app has had **at least 12 testers opted in to a closed test for at least 14 days in a row**.

1. **Test and release** > **Testing** > **Closed testing** > **Create track** (or use the default
   **Alpha**) > **Manage track**.
2. **Countries / regions**: add the countries your testers are in (or all).
3. **Testers**: **Create email list** > add at least 12 Google account emails (15 gives slack for
   anyone who drops out) > **Save**. Copy the **opt-in link** and the **Play Store link**.
4. **Releases** > **Create new release** > **Add from library** (the bundle from step 6) or
   upload it > release notes > **Next** > **Save** > **Send for review**. The first review of a
   closed test can take a few days.
5. Once approved, send every tester the opt-in link. Each one must open it, tap **Become a
   tester**, and install the app from Play. Ask them to keep it installed and use it now and then;
   Google looks at engagement, not just installs.
6. Watch **Closed testing** > the track > **Testers**: the count of opted-in testers. Keep it at
   12 or more for 14 consecutive days. Fix anything they find with a new release to the same track
   (bump `versionCode` in `app/build.gradle.kts` first; see step 10).

## 8. Apply for production (day 15+, 15 minutes)

1. **Dashboard** > **Apply for production** (appears once the 14 days are met).
2. Answer the questions about the closed test: how you recruited testers, what feedback you got,
   what you changed. Honest short answers are fine.
3. Submit. Google usually answers within a week.

## 9. Release to production

1. **Test and release** > **Production** > **Countries / regions** > add countries (or all).
2. **Create new release** > **Add from library** > the tested bundle > release notes > **Next**.
3. **Rollout percentage**: start at **20%** (a staged rollout lets you halt it if something goes
   wrong), or 100% if the closed test went well.
4. **Save** > **Go to overview** > **Send changes for review**. Production review usually takes
   from a few hours to a few days.
5. When it is live: check **Quality** > **Pre-launch report** for crashes found on Google's
   robot devices, and **Android vitals** after a few days.

## 10. Every later update

1. Raise `versionCode` by one (and `versionName`, e.g. `1.0.1`) in `app/build.gradle.kts`.
2. `./gradlew bundlePlayRelease` with the same key.
3. **Production** (or a test track first) > **Create new release** > upload > notes > review.

## For the GitHub build

Nothing here involves Play. `./gradlew assembleGithubRelease` makes a signed APK with the same
key; push a tag like `v1.0.0` and the CI workflow attaches the APK to a GitHub release (add the
four `NULIS_RELEASE_*` secrets the key script prints to have CI sign it). Before the first one,
replace `REPLACE_ME` in `app/src/github/java/com/nulis/launcher/Store.kt` with your real
Buy Me a Coffee page.

Google has announced developer verification for apps installed outside Play on certified Android
devices, rolling out by country from 2026. If it applies where your users are, register the
package name `com.nulis.launcher` and your signing key in the Android Developer Console so the
GitHub APK keeps installing without warnings. Check Google's current timeline before the first
GitHub release; the Play build is unaffected.
