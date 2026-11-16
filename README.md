# My App Blocker (Kotlin)

A personal Android app that lets you **block the internet connection of any app on your phone** — per app, and separately for Wi‑Fi vs mobile data. No root needed.

## How it works

The app runs a **local VPN service** (Android's built-in `VpnService` — no actual data leaves your phone):

- Only the apps you block are routed into the VPN tunnel.
- The tunnel simply drops their packets → no internet, no DNS, nothing loads.
- All other apps (and system services) bypass the VPN and run at full speed.
- Switching between Wi‑Fi and mobile data rebuilds the rules automatically.

Your phone shows the standard Android "VPN connected" key while it runs. Nothing is uploaded anywhere — this is a firewall, not a proxy.

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
2. Tap it → allow "Install unknown apps" if asked → install.
3. Open **My App Blocker** and flip the top switch ON.
4. Android will ask for **VPN permission** → tap **OK**. Also allow the notification permission if asked.
5. You now see every app that has internet permission. Tap a switch to block it.

## Using it

- **Top switch** — starts/stops the blocker.
- **Per-app switch** — blocks that app's internet (Wi‑Fi **and** data).
- **Long-press an app** — choose: block Wi‑Fi only / mobile data only / everything / allow.
- **Search box** — filter the list by name or package.
- The notification has a **Stop** button; the key icon in the status bar = VPN active.

## Important notes

- **Only one VPN can run at a time.** If NetGuard, AdGuard, Windscribe, etc. are on, turn them off (or use them for this instead 🙂).
- Blocking stays on until you turn it off in the app or via the notification. After a phone **reboot**, open the app once — it restarts automatically.
- A blocked app won't show errors immediately; it just hangs/times out (no data flows). Unblock to restore it instantly.
- Careful blocking system components like `com.android.vending` (Play Store) or Google Play Services — some things on the phone may misbehave.
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
        │   ├── MainActivity.kt      ← UI: app list + switches
        │   ├── AppAdapter.kt        ← list adapter + search
        │   ├── AppEntry.kt
        │   ├── Store.kt             ← saved settings
        │   └── BlockerVpnService.kt ← the actual firewall (VPN)
        └── res/                     ← layouts, icons, strings
```

## Requirements

- Android 8.0+ (API 26)
- Built with AGP 8.5.2 / Kotlin 2.0.21 / Gradle 8.7 (handled by the GitHub workflow)
"# APP-BLOCKER" 
