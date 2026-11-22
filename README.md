# CAPJI Net (كابجي نت)

Modern dark-first (Material 3) Android Wi-Fi network manager: radar-style device scan, per-device block / speed limit / data quota / timed pause, animated speed test, shared-element transitions and haptics. Built with Kotlin + Jetpack Compose.

> Status: **UI + architecture complete, runs fully on a simulated router.** The MikroTik driver is experimental; OpenWrt and web-UI drivers are planned. See `docs/`.

## Run it (no Android Studio required)

1. Push this folder to a GitHub repository (branch `main`).
2. Open the **Actions** tab -> **Android CI** runs automatically (or press *Run workflow*).
3. When it finishes, open the run -> **Artifacts** -> download `CAPJI-Net-debug-apk` -> install `app-debug.apk` on your phone.
4. In the app tap **"Try the demo router"**.

Or locally: open the folder in Android Studio (Ladybug+), let it sync, run on a device/emulator (API 26+).

## Docs
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) - stack, packages, router-communication model, screens, motion spec
- [`docs/ENGINEERING_NOTES.md`](docs/ENGINEERING_NOTES.md) - permissions, discovery, OUI/randomized MACs, quotas, router compatibility (home routers vs MikroTik/OpenWrt)

## Key files
| What | File |
|---|---|
| Animated speed gauge | `ui/components/SpeedGauge.kt` |
| Radar scan animation | `ui/components/RadarPulse.kt` |
| Device card + status badges (shared element) | `ui/components/DeviceCard.kt`, `StatusBadge.kt` |
| Per-device control screen | `ui/screens/DeviceControlScreen.kt`, `DeviceSheets.kt` |
| Mock models / simulated router | `domain/model/Models.kt`, `data/driver/MockRouterDriver.kt` |
| Driver contract | `domain/driver/RouterDriver.kt` |
