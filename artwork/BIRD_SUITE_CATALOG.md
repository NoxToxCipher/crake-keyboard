# Crake Ecosystem — Avian Brand & Identity Catalog

A unified minimalist avian design language built for the **Crake Security & Privacy Application Suite**.

All icons feature:
* **Ground / Background**: `#0F1418` (Matte Obsidian Slate)
* **Silhouette / Foreground**: `#2DD4BF` (Electric Teal)
* **Monochrome Themed Layer**: `#FFFFFF`
* **Style**: Flat 2D minimalist vector geometry with high-contrast aerodynamic lines.

---

## 1. Application Roster & Species Assignment

| App Name | Species | Role in Ecosystem | Vector & Assets Folder |
| :--- | :--- | :--- | :--- |
| **Crake Messenger** | **Spotted Crake** | Primary E2EE Tox Messenger, Vault & Identity Core | [`artwork/birds/crake/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/crake/) |
| **Crake Keyboard** | **Peregrine Falcon** | High-velocity input, on-device neural IME & flick gestures | [`artwork/birds/falcon/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/falcon/) |
| **Crake Tunnel** | **Swift** | WireGuard / Tor / Obfuscated VPN & Encrypted Proxy | [`artwork/birds/swift/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/swift/) |
| **Crake Vault** | **Kingfisher** | Zero-knowledge password, key & identity vault | [`artwork/birds/kingfisher/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/kingfisher/) |
| **Crake Patrol** | **Raven** | Operational telemetry, shift chronograph & terminal | [`artwork/birds/raven/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/raven/) |
| **Crake Mesh** | **Swallow** | Ad-hoc P2P, BLE & Wi-Fi Direct nearby sync | [`artwork/birds/swallow/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/swallow/) |
| **Crake Armor** | **Osprey** | On-device packet filter, firewall & defense shield | [`artwork/birds/osprey/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/osprey/) |
| **Crake Radar** | **Night Owl** | Silent DHT health monitor, bootstrap & relay diagnostics | [`artwork/birds/owl/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/owl/) |
| **Crake Bridge** | **Albatross** | Long-range federation bridges & Pluggable Transports | [`artwork/birds/albatross/`](file:///C:/Users/lochr/crake-keyboard/artwork/birds/albatross/) |

---

## 2. Asset Structure per Bird

Each bird package inside `artwork/birds/<bird_id>/` contains:

* `<bird_id>_1024.png`: Master 1024×1024 high-res store icon (solid `#0F1418` edge-to-edge).
* `<bird_id>_512.png`: 512×512 store graphic.
* `<bird_id>_round_512.png`: 512×512 circular mask icon on transparent background.
* `<bird_id>_foreground_512.png`: 512×512 isolated bird contour on transparent background.
* `<bird_id>.svg`: Scalable vector SVG (100×100 viewport, `currentColor` or `#2DD4BF`).
* `ic_launcher_foreground.xml`: Android Vector Drawable (108dp adaptive icon foreground).
* `ic_launcher_monochrome.xml`: Android Vector Drawable for themed monochrome icons.
* `mipmap-*/`: Pre-scaled PNGs (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) for `ic_launcher.png`, `ic_launcher_round.png`, and `ic_launcher_foreground.png`.

---

## 3. Dropping an Identity into a New Crake App

1. Copy `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` into your app's `app/src/main/res/drawable/`.
2. Add the background color in `res/values/colors.xml`:
   ```xml
   <color name="ic_launcher_background">#0F1418</color>
   <color name="accent">#2DD4BF</color>
   ```
3. Set your `res/mipmap-anydpi-v26/ic_launcher.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@color/ic_launcher_background"/>
       <foreground android:drawable="@drawable/ic_launcher_foreground"/>
       <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
   </adaptive-icon>
   ```
4. Copy `mipmap-*/` directly into `res/` for legacy launchers.
