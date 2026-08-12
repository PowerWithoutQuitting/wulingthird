# Wuling Vehicle Control APP

An independent open-source Android app for controlling Wuling/Baojun new energy vehicles. Connects directly to the official telematics API — no Home Assistant required.

> Originally started at [wlsqqq/wuling-automotive-control](https://gitee.com/wlsqqq/wuling-automotive-control), now maintained on GitHub. Will be removed immediately if there are any IP concerns.

---

## Features

### Remote Control

| Feature | Description |
|---------|-------------|
| Lock / Unlock | One-tap remote door lock/unlock |
| Climate Control | On/off, temperature (17-33°C), fan speed (1-7), quick cool/heat |
| Remote Start | Remote engine start (ignition authorization) |
| Trunk | Remote trunk release |
| Find Car | Remote horn + lights |
| Window Control | Close/open all windows at once |

### Vehicle Status Monitoring

| Category | Data |
|----------|------|
| Battery & Range | SOC, EV range, fuel range, hybrid mileage, fuel remaining |
| Battery Health | SOH, temperature range, voltage, current, low-voltage battery |
| Mileage | Total, yesterday's, average energy consumption |
| Tire Pressure | Four-tire pressure (bar) + temperature |
| Doors | Per-door open/locked status, trunk status |
| Windows | Per-window open status |
| Lights | Fog, turn signals, clearance, high/low beam |
| Charging | Status, power, remaining time |
| Temperature | Cabin, A/C, motor, inverter |
| Driving | Gear, steering angle, brake/gas pedal, key status |
| Diagnostics | Powertrain, engine temp, ABS, power steering faults |
| Seats | Heating/ventilation status |

### MQTT Real-time Push

- Vehicle status push via Eclipse Paho MQTT v3
- Protobuf message parsing with incremental state updates
- Auto-reconnect with exponential backoff (max 60s, up to 10 retries)
- Connection status indicator in UI

### Vehicle Location

- Amap (Gaode Maps) WebView integration with real-time vehicle position
- One-tap navigation to vehicle
- Share vehicle location

### BLE Proximity Control

- Digital Bluetooth key: fetch BLE key from server and connect to vehicle
- RSSI-based auto lock/unlock: unlock on approach, lock on departure
- Configurable RSSI thresholds, duration, and cooldown
- Foreground service for background operation
- Nearby BLE device scanner

### Customization

- API Token binding (DataStore persistence)
- Themes: Light / Dark / Follow System
- Custom color scheme (primary, background, card, text colors)
- Custom background image with blur + overlay
- Card transparency control
- Debug log viewer (filter, copy, clear)
- Amap Web JS API Key configuration

---

## Tech Stack

```
com.open.wuling/
├── data/
│   ├── api/          # HTTP API (OkHttp + Gson)
│   ├── local/        # Local prefs (theme, BLE, map key)
│   ├── model/        # Data models
│   ├── mqtt/         # MQTT layer (Paho + Protobuf)
│   ├── repository/   # Data repository
│   └── store/        # Token persistence
├── ui/
│   ├── components/   # Shared Compose components
│   ├── screens/      # Screens (Home/Detail/Profile/Location)
│   └── theme/        # Material3 theming
├── ble/              # BLE control
└── util/             # Utilities
```

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt
- **Networking**: OkHttp + Gson
- **MQTT**: Eclipse Paho Client v3
- **Bluetooth**: Android BLE API
- **Storage**: DataStore Preferences
- **Min SDK**: Android 9 (API 26)

---

## Supported Vehicles

| Vehicle | Status |
|---------|--------|
| Wuling New Energy (all models) | Compatible (generic API) |
| Baojun New Energy | Experimental (same API domain) |

---

## Installation

1. Download APK from the [Releases page](https://github.com/haocat/wulingthird/releases)
2. Go to Profile → API Token to configure your token
3. Obtain the token by intercepting traffic from the official Wuling/Baojun app
4. Use the token bound to your authorized phone number
5. For location features, configure your own Amap Web JS API Key

---

## Building

```bash
# Configure these in local.properties
wuling.client.id=YOUR_CLIENT_ID
wuling.client.secret=YOUR_CLIENT_SECRET
wuling.app.code=YOUR_APP_CODE
wuling.app.version=YOUR_APP_VERSION
wuling.base.url=YOUR_API_BASE_URL
wuling.device.imei=DEVICE_IMEI
wuling.device.model=DEVICE_MODEL
wuling.device.brand=DEVICE_BRAND
wuling.api.version=API_VERSION
wuling.api.version.code=API_VERSION_CODE

# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

---

## Roadmap

- [ ] Home screen widget (lock/unlock/AC shortcuts, battery/range display)
- [ ] Quick Settings tile (notification shade controls)
- [ ] Local notifications (low battery, charge complete)

> Contributions welcome — feel free to open issues or submit PRs.

---

## Disclaimer

1. This is an **unofficial third-party open-source tool** for personal learning and research only. Commercial use is prohibited.
2. This app does not store any user accounts, vehicle, or sensitive information. All risks are borne by the user.
3. "Wuling" and "Baojun" are registered trademarks of their respective owners. This project has no affiliation with the official companies.

---

## License

[MIT License](LICENSE) — free to use, modify, and distribute.
