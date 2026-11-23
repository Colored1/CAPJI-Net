# CAPJI Net — Firebase (Remote Config + Push) setup

The app builds and runs WITHOUT Firebase. Remote features switch on automatically once `google-services.json` is added.

## 3 steps
1. Firebase Console → create a project → **Add app → Android**, package name exactly `com.capji.netpulse`.
2. Download `google-services.json`.
3. On GitHub (Colored1/CAPJI-Net): open the `app` folder → **Add file → Upload files** → upload `google-services.json` → Commit. Actions rebuilds the APK with Firebase.

## Remote Config parameters (Firebase Console → Remote Config)
| Key | Type | Meaning |
|---|---|---|
| latest_version_code | Number | newer than installed versionCode → update dialog |
| update_message | String | dialog text |
| update_url | String | Play Store link (market:// or https://play.google.com/...) or APK page |
| force_update | Boolean | true → dialog cannot be dismissed |
| announcement_banner_enabled / announcement_text / announcement_link | Boolean / String / String | dismissible card on Home |
| maintenance_mode / maintenance_message | Boolean / String | full-screen lock screen |

## Push
All users are subscribed to topic `all_users`. Firebase Console → Messaging → New campaign → target topic `all_users`.
Optional custom data keys: `url` (https only, opened on tap), `tab` (HOME, DEVICES, SPEED, ROUTER, SETTINGS, HOTSPOT) or deep link `capji://tab/devices`.

## Google Play compliance
For Play-distributed builds `update_url` must point to the Play Store listing (Play forbids updating the app by other means). Other URLs are fine for direct-APK distribution.
