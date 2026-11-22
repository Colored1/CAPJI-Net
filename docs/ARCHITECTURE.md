# NetPulse - Architecture, Screens & Motion Plan

## 1. Stack

| Layer | Choice | Why |
|---|---|---|
| Language / UI | Kotlin 2.0, Jetpack Compose (BOM 2024.12), Material 3 | Declarative UI; `SharedTransitionLayout` (Compose 1.7+) gives real shared-element transitions |
| Motion | Compose Animation (spring / tween / infinite transitions), `Canvas` | No Lottie needed: radar + gauge are drawn, so they stay crisp and react to live state |
| Async | Coroutines + Flow / StateFlow | Router polling, speed-test progress and UI state are all streams |
| Pattern | MVVM + a light Clean split (`domain` / `data` / `ui`) | Drivers are swappable behind one interface |
| Network | OkHttp | Router REST calls and the speed test share one client; Retrofit is optional, add it per driver if a router has a big JSON API |
| DI | Manual `AppGraph` object | Zero boilerplate for a first version; swap to Hilt/Koin later |

## 2. Package structure

```
com.capji.netpulse
  AppGraph.kt                  manual DI + Application class
  MainActivity.kt
  core/                        GatewayDetector (default-gateway lookup)
  domain/
    model/Models.kt            NetDevice, SpeedLimit, DataQuota, Capability, ConnectionState, Speed*
    driver/RouterDriver.kt     THE contract every router family implements
    identify/DeviceIdentifier  OUI vendor lookup + hostname fingerprinting + randomized-MAC check
  data/
    RouterRepository.kt        current driver + connection state + device flow
    ProfileStore.kt            saved router profiles (no passwords)
    driver/                    MockRouterDriver, MikroTikRestDriver (experimental), UnsupportedDriver
    speedtest/                 RealSpeedTestEngine (Cloudflare), SimulatedSpeedTestEngine
  ui/
    theme/                     dark-first M3 scheme, status colours, gradient background
    components/                GlassCard, StatusBadge, DeviceCard, RadarPulse, SpeedGauge, ConnectionPill, Motion helpers
    screens/                   Login, Home, Devices, DeviceControl (+ sheets), SpeedTest, Router
    viewmodel/                 DevicesViewModel, SpeedTestViewModel, LoginViewModel
```

For a larger team, split into Gradle modules later: `:core`, `:domain`, `:driver-mikrotik`, `:driver-openwrt`, `:feature-devices`, `:feature-speedtest`. The `RouterDriver` interface already isolates them.

## 3. How router communication is structured

```
 UI (Compose) -> ViewModel -> RouterRepository -> RouterDriver (interface)
                                                    |-- MockRouterDriver       (simulated)
                                                    |-- MikroTikRestDriver     (RouterOS 7 REST, HTTP+Basic)
                                                    |-- OpenWrtDriver (planned)(ubus JSON-RPC over HTTP, /cgi-bin/luci/rpc)
                                                    '-- WebSessionDriver (planned) (login form + cookie/token, per-vendor scraper)
```

Two very different capability layers exist:

1. **Router API / web-session layer** - the only layer that can *enforce* block, limits and quotas, because the router is the gateway. Every real feature goes through a driver.
2. **Local-network layer on the phone** - ARP/mDNS/DHCP discovery, gateway detection, ping. Read-only: an Android app cannot block or throttle other devices by itself (that needs root + ARP spoofing, which is out of scope and fragile).

`Capability` flags let each driver say what it really supports; the UI disables unsupported controls and explains why (see the Device control screen).

## 4. Screens

| Screen | Contents | Key motion |
|---|---|---|
| **Login / Router setup** | Router type chips, gateway field with "detect" button, credentials, saved profiles (chips), connection pill, "Try demo" | Staggered spring entrance; pill cross-fades Not connected -> Connecting (pulsing dot) -> Connected / Failed; button morphs into a spinner |
| **Home dashboard** | Radar, Online/Limited/Blocked counters, live traffic sparkline, top 3 consumers | Radar ripples + sweep, blips light up as the sweep passes; counters slide-roll; consumer bars spring |
| **Devices** | Live list of cards: name, vendor/model, IP, MAC, band (2.4/5/LAN), live up/down, status badge, quota bar, "Private MAC" tag | `animateItem()` for add/remove/reorder; badge colour + icon spring on status change |
| **Device control** | Shared-element header, Block switch (confirm dialog), Speed-limit sheet, Quota sheet, timed pause chips + countdown | Card **morphs** into the screen (`sharedBounds` + `sharedElement`, spring bounds transform); sections stagger in; haptics on every action |
| **Speed test** | Arc gauge with needle, phase stepper, Ping/Jitter/Down/Up tiles, GO/Stop button, history | Needle + arc share one low-bounce spring; dial scale grows 25->50->100...; active tile border glows |
| **Router** | Connection details, capability matrix, notes, disconnect | Staggered cards |

## 5. Motion spec

| Element | Spec |
|---|---|
| Card <-> control screen | `sharedBounds` key `card-<mac>`, `sharedElement` key `avatar-<mac>`, spring(damping 0.82, stiffness MediumLow); cross-fade 260 ms for the rest |
| Section entrance | fade + slide-up 1/3, spring(LowBouncy, Low), 70 ms stagger |
| Radar | sweep 2.8 s linear; 3 ripples 2.4 s ease-out, phase-shifted; intensity animates 0.35 <-> 1 over 600 ms when scanning starts/stops |
| Gauge | progress spring(LowBouncy, StiffnessLow); number tween 250 ms |
| Status badge | colour spring; content swap = fade + bouncy scale-in from 0.7 |
| Sheets | M3 `ModalBottomSheet` (spring-based anchored draggable), hide animation before dismiss |
| Haptics | `LongPress` on block / apply / pause / GO; `TextHandleMove` (light tick) on slider steps, chips, tab changes |
| Status colours | Online green, Blocked red, Limited amber, Quota-capped violet, Paused blue - fixed (not dynamic) so meaning never changes |

## 6. Speed test implementation

- **Ping / jitter**: 8 sequential tiny requests to `speed.cloudflare.com/__down?bytes=0`; ping = best RTT, jitter = mean absolute difference of consecutive RTTs.
- **Download**: stream `__down?bytes=100000000`, read 64 KB chunks, update every 100 ms, stop at 8 s.
- **Upload**: repeated 1 MB POSTs to `__up` for 6 s.
- **Limitation**: a single TCP stream under-reports on fast links. Production: 4-6 parallel streams and a warm-up discard. The "Simulate" switch drives the same UI offline.
