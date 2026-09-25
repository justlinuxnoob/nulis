# F-Droid

Nulis meets F-Droid's inclusion criteria as of 1.0.0. What was checked, what was changed, and
what submitting would take.

## Criteria

| Requirement | Status |
|---|---|
| Free/open-source licence | GPL-3.0-or-later, every source file carries an SPDX line |
| All dependencies free software | AndroidX, Jetpack Compose, Kotlin, kotlinx-serialization and -coroutines only. Robolectric, Roborazzi and JUnit are test-only and never in the APK |
| No proprietary Google services, no tracking, no ads | None of any kind; no `INTERNET` permission at all |
| Builds from source with standard tools | `./gradlew assembleGithubRelease`, JDK 17+, Android SDK 36 |
| No pre-built binaries in the repository | None (the Gradle wrapper jar is the one F-Droid accepts) |
| No encrypted dependency-info block in the APK | **Fixed**: `dependenciesInfo.includeInApk = false` in `app/build.gradle.kts` |
| Anti-features | None apply. A donation link is not an anti-feature |
| Store metadata | `fastlane/metadata/android/en-US/` (title, descriptions, changelog, icon, feature graphic, screenshots), the layout F-Droid reads |

## Which build

F-Droid should build the **github** flavor: the Play flavor exists only because of Play's
payments policy, and F-Droid is happy to show a donation link (it even has a field for it).

## Submitting

1. Replace `REPLACE_ME` in `app/src/github/java/com/nulis/launcher/Store.kt` first.
2. Tag the release (`v1.0.0`) and push the tag.
3. Fork https://gitlab.com/fdroid/fdroiddata, add `metadata/com.nulis.launcher.yml` along these
   lines, and open a merge request:

```yaml
Categories:
  - Theming
  - System
License: GPL-3.0-or-later
SourceCode: https://github.com/justlinuxnoob/nulis
IssueTracker: https://github.com/justlinuxnoob/nulis/issues
Donate: https://buymeacoffee.com/REPLACE_ME

RepoType: git
Repo: https://github.com/justlinuxnoob/nulis.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - github

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

F-Droid signs its builds with its own key, so its APK cannot update a GitHub or Play install (and
vice versa); that is normal for every app on F-Droid. Optional later: reproducible builds, which
would let F-Droid ship the APK signed with your own key.
