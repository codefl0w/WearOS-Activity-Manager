# Wear Activity Launcher

Wear-first Android activity launcher for Wear OS devices.

It scans installed packages, surfaces exported activities, and lets you launch them from a watch-friendly UI with search, expandable app groups, shortcut creation, and companion-phone actions.

## Features

- Browse installed apps and expand each app to inspect exported activities.
- Launch individual activities directly from the watch.
- Long-press an activity to request a pinned launcher shortcut.
- Open contact and donate links on the paired phone.
- Wear-optimized layout with centered header, compact action zone, and round-screen-friendly spacing.

## Tech Stack

- Kotlin
- Jetpack Compose
- Compose for Wear OS
- Gradle Kotlin DSL

## Local Build

Requirements:

- Android Studio or Android SDK with API 34 installed
- JDK 17

Build a debug APK:

```powershell
./gradlew assembleDebug
```

Compile Kotlin only:

```powershell
./gradlew :app:compileDebugKotlin
```

## CI

GitHub Actions workflow:

- Compiles and assembles the debug build on push, pull request, and manual runs
- Uploads the generated debug APK as an artifact

See [`.github/workflows/android.yml`](.github/workflows/android.yml).

## Notes

- The app uses `QUERY_ALL_PACKAGES` intentionally because its core feature requires inspecting arbitrary installed packages. This is appropriate for sideload/internal use and may require policy review for Google Play distribution.
- `Contact Me` and `Donate` URLs live in [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml).
- No license file is included yet. Choose a license before publishing the repository publicly.
