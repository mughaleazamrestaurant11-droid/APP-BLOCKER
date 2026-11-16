# My App Blocker (Kotlin)

A personal Android app that **blocks apps from opening** — with a full-screen lock cover and a clean multi-page UI (Dashboard / Apps / Settings), dark mode, TikTok downloader, fingerprint lock and more.

## How it works

**Launch Guard (app open blocker):**

1. You mark an app as **blocked** in the Apps page.
2. An accessibility service ("App Blocker Launch Guard") watches which app comes to the foreground.
3. The moment a blocked app is opened normally (launcher, recents, another app…), it is covered with a dark **"App blocked"** screen — with **no bypass button on it**.
4. The only ways to open a blocked app are from inside My App Blocker (Apps page → long-press an app → **Open now**, 1-minute bypass, guarded by the fingerprint lock) or by unblocking the app.

No VPN, no root, no special permissions — just the accessibility service you enable once.

## Build the APK on GitHub (no Android Studio needed)

1. Create a new repository on GitHub (e.g. `MyAppBlocker`) — public or private both work.
2. Upload/push this folder to the repo:

```bash
cd MyAppBlocker
git init
git add .
git commit -m "My App Blocker"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/MyAppBlocker.git
git push -u origin main
```

3. Open the **Actions** tab on GitHub — a workflow named **Build APK** starts automatically and builds the app in the cloud.
4. When it finishes (a few minutes), click the run → scroll to **Artifacts** → download **`MyAppBlocker-debug`**.
5. Unzip it — inside is **`app-debug.apk`**. This APK is already signed and installable.

> Tip: You can also click **Run workflow** in the Actions tab anytime to rebuild.
> If you push a tag like `v1.0`, the APK is attached to a GitHub Release too.

## Install on your phone

1. Copy `app-debug.apk` to your phone (USB, WhatsApp to yourself, Drive, etc.).
2. Tap it → allow "Install unknown apps" if asked → install (if Play Protect shows a warning, tap "More details" → press-and-hold "Install anyway").
3. Open **My App Blocker** → **Settings** → **Block apps from opening** → flip the switch ON.
4. Tap **Enable in Accessibility settings** and turn on **"App Blocker Launch Guard"**. One-time setup — done.

## Using it

- **Dashboard** — the Launch Guard switch, live status ("Active — N app(s) protected"), the count of apps that only open from here, and the menu (Apps / TikTok downloader / Settings).
- **Apps & blocking page** — per-app **Block** switch (blocks that app from opening):
  - **Long-press an app** to choose Allow / Block opening, or **"Open now (1 min bypass)"** for blocked apps.
  - **Search box** — filter the list by name or package.
  - **Block all / Allow all** buttons — bulk control in one tap.
  - **"Blocked only"** switch — show just the blocked apps.
- **Blocked cover screen** — shows the app name with only a **"Go home"** button. No bypass on the cover; nothing outside this app can open a blocked app.
- **Auto-block new apps** switch (Settings) — every app you install in the future gets blocked automatically.
- **Backup / Restore** — save/restore your block list as a JSON file (Settings → Your data), great when changing phones.
- **Fingerprint lock** switch — opening this app requires your fingerprint (Android 9+).
- **TikTok downloader** — paste any TikTok link and download the video **without watermark** into `Downloads/MyAppBlocker`.
- **Home-screen widget** — long-press your launcher → Widgets → "App Blocker" → a one-tap **Guard ON/OFF** widget.
- **Quick Settings tile** — add the "App Blocker" tile for one-tap on/off without opening the app.
- **Dark mode** — follows your phone's dark/light theme automatically.

## Important notes

- The accessibility service must stay enabled; Android may disable it on some devices after an update — the Settings page shows live **ACTIVE/OFF** status so you can re-enable it.
- With the fingerprint lock on, the 1-minute "Open now" bypass in the Apps page is safe: nobody can reach it without your finger. The cover screen itself offers no bypass.
- Android doesn't let a normal app *prevent* another app from launching — covering it instantly is exactly how commercial AppLock/blocker apps work.
- The "release" artifact is unsigned and can't be installed directly — use the **debug** APK.

## Project structure

```
MyAppBlocker/
├── .github/workflows/build.yml      ← builds the APK on GitHub
├── settings.gradle / build.gradle / gradle.properties / gradle/wrapper
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/classic/myappblocker/
        │   ├── MainActivity.kt          ← dashboard (status, switch, menu)
        │   ├── AppsActivity.kt          ← app list + per-app blocks
        │   ├── AppAdapter.kt            ← list adapter + search
        │   ├── AppEntry.kt
        │   ├── Store.kt                 ← saved settings
        │   ├── LaunchGuardService.kt    ← the actual open blocker
        │   ├── BlockedActivity.kt       ← full-screen "App blocked" cover
        │   ├── SettingsActivity.kt      ← guard, fingerprint, backup
        │   ├── TikTokDownloaderActivity.kt
        │   ├── BlockerTileService.kt    ← quick settings tile
        │   └── ToggleWidget.kt          ← home-screen widget
        └── res/                         ← layouts, icons, strings
```

## Requirements

- Android 8.0+ (API 26)
- Built with AGP 8.5.2 / Kotlin 2.0.21 / Gradle 8.7 (handled by the GitHub workflow)
