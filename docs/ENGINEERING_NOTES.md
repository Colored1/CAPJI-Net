# Engineering notes

## Permissions
| Permission | Why |
|---|---|
| `INTERNET` | router REST calls, speed test |
| `ACCESS_NETWORK_STATE` | find the default gateway via `ConnectivityManager.getLinkProperties` |
| `ACCESS_WIFI_STATE` | optional extras (SSID/RSSI of the phone itself) |

- Reading the Wi-Fi **SSID** needs location permission on Android 8.1+ and a location-on state; NetPulse does not need it.
- `usesCleartextTraffic="true"` is set because home routers speak plain HTTP on the LAN. For production restrict it with a `network-security-config` limited to private ranges (192.168.*, 10.*, 172.16-31.*).
- Android 14+: local network access is not gated, but Android 16/17 is introducing a "local network" permission - re-check before targeting it.

## Discovering devices
1. **Preferred: ask the router.** DHCP leases + ARP + wireless registration table give name, IP, MAC, band and signal in one call.
2. **Fallback on the phone**: scan the /24 with parallel TCP/ICMP probes, read `/proc/net/arp` (blocked on Android 10+ for apps), use mDNS (`NsdManager`) for names. Results are incomplete - treat as a hint, never as the source of truth.

## Identifying the brand / model
1. **OUI** = first 3 bytes of the MAC -> vendor (IEEE list; sample subset is embedded in `DeviceIdentifier`). Bundle the full list as a compressed asset.
2. **Hostname fingerprinting**: `Ahmed-iPhone`, `Galaxy-S23`, `Redmi-Note-12`, `DESKTOP-xxxx`... gives a *family*, rarely an exact model.
3. **Richer signals** (optional): DHCP option 55/60 fingerprints (Fingerbank API), mDNS service names, UPnP/SSDP device descriptions.
4. **Randomized MACs**: if bit `0x02` of the first octet is set the address is locally administered (private Wi-Fi address on Android 10+/iOS 14+/Windows). Vendor lookup is meaningless, the device may appear as a new client after reconnecting, and MAC-based rules can be bypassed. The app flags these ("Private MAC") and tells the user to disable private addresses for the home network if they need reliable control. Per-device rules keyed by IP have the same weakness when DHCP leases change - prefer static leases.

## Quotas - be honest in the UI
- Block and speed limits are *router-side rules* and keep working when the app is closed.
- A **data quota** needs byte counters. Few home routers expose per-client quotas natively. Options: router-side script (MikroTik scheduler + queue counters, OpenWrt `nlbwmon`/`sqm` + cron), or app-side polling that applies BLOCK/THROTTLE when the cap is reached - which only works while the app/service runs (use a foreground service + WorkManager for resilience).
- The `Capability.QUOTA` flag and the hint under the quota section communicate this per driver.

## Router compatibility
| Family | API | Block | Limit | Quota | Notes |
|---|---|---|---|---|---|
| **MikroTik RouterOS 7** | REST (`/rest/...`, enable `www` service, HTTP Basic) | firewall address-list + drop rule | `queue/simple` | script/scheduler | Best candidate; `MikroTikRestDriver` is a reference implementation (not tested on hardware). RouterOS 6 uses the binary API (port 8728) - needs a different driver. |
| **OpenWrt** | ubus JSON-RPC (`/ubus`, needs `rpcd` session) or SSH | `nft`/firewall rule per MAC | `sqm-scripts` per IP or `tc` | `nlbwmon` counters | Most flexible; write a driver using `session.login` then `call` on `luci-rpc`, `hostapd.*`, `file`/`uci`. |
| **TP-Link / Huawei / ZTE / Tenda home routers** | proprietary web session (login form, encrypted password, token) | MAC filter / parental control | "bandwidth control" page if present | rarely | One scraper driver per model/firmware; they break on firmware updates, so isolate each in its own class and mark capabilities strictly. |
| **ISP-locked routers** | often none | - | - | - | Surface as "read-only" or unsupported. |

Add a driver by: (1) implementing `RouterDriver`, (2) adding a `RouterType` entry, (3) one `when` branch in `AppGraph`.

## Security
- Never store the admin password in plain text. If "remember password" is added: Android Keystore-backed `EncryptedSharedPreferences` / DataStore + Tink, and biometric gate.
- Warn when the router URL is plain HTTP over a non-private address.
- Prefer a dedicated low-privilege router account for the app where the router supports it.

## Testing the UI without a router
Tap **"Try the demo router"**. `MockRouterDriver` simulates 8 devices (one with a quota that fills in ~1 minute, one limited, one blocked, one private-MAC), live traffic, quota enforcement, timed pause, and a guest device that appears/disappears when you scan.

## Next steps
- Persist speed-test history (Room/DataStore).
- Real OUI database asset + Fingerbank lookups.
- Foreground service for app-side quota enforcement + notifications (`POST_NOTIFICATIONS`).
- Hilt, Gradle modularisation, Compose UI tests with the mock driver, baseline profiles.
- Arabic localisation + RTL pass (strings are currently English; `supportsRtl` is already on).
