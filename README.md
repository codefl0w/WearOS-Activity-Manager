
<img width="360" height="360" alt="ActMan_LOGO" src="https://github.com/user-attachments/assets/5856e855-945d-4035-9a0d-4dd353c2ecc4" />

# Wear Activity Launcher

Wear-first Android activity launcher for Wear OS devices.

It scans installed packages, surfaces exported activities, and lets you launch them from a watch-friendly UI with search, expandable app groups and shortcut creation.
## Features

- Browse installed apps and expand each app to inspect exported activities.
- Launch individual activities directly from the watch.
- Long-press an activity to request a pinned launcher shortcut.
- Open contact and donate links on the paired phone.
- Wear-optimized layout with centered header, compact action zone, and round-screen-friendly spacing.


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

- Runs a verification compile on push, pull request, and manual runs
- Builds a signed release APK on push and manual runs
- Uploads the signed release APK as an artifact

See [`.github/workflows/android.yml`](.github/workflows/android.yml).

GitHub secrets required for signed release builds:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

To create `ANDROID_KEYSTORE_BASE64` locally:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\your-release.keystore"))
```

## Extras

Enjoy my work? Please consider a small donation!

<a href="https://buymeacoffee.com/fl0w" target="_blank" rel="noopener noreferrer">
  <img width="350" alt="yellow-button" src="https://github.com/user-attachments/assets/2e6d44c8-9640-4cb3-bcc8-989595d6b7e9"/>
</a>
