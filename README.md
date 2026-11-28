<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="brand/lockup-horizontal-color-dark.svg">
    <img src="brand/lockup-horizontal-color.svg" height="84" alt="Material Island">
  </picture>
</p>

<p align="center">
  <b>A dynamic island for Android, built the Pixel way.</b><br>
  Material You colour, Material 3 Expressive shapes and springs, wrapped around your camera hole.
</p>

<p align="center">
  <a href="https://github.com/JotnarDev/material-island/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/JotnarDev/material-island?color=1A73E8"></a>
  <a href="https://github.com/JotnarDev/material-island/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/JotnarDev/material-island/total?color=3DDC84"></a>
  <a href="https://github.com/JotnarDev/material-island/stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/JotnarDev/material-island?color=FFC21A"></a>
  <a href="https://github.com/JotnarDev/material-island/issues"><img alt="Issues" src="https://img.shields.io/github/issues/JotnarDev/material-island"></a>
  <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white">
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-0B57D0"></a>
</p>

<p align="center">
  <img src="docs/gifs/hero.gif" width="480" alt="Music card, an incoming call and the liquid drop animation on the island">
</p>

<p align="center">
  <a href="https://github.com/JotnarDev/material-island/releases/latest"><img alt="Download APK" height="40" src="https://img.shields.io/github/v/release/JotnarDev/material-island?style=for-the-badge&label=Download%20APK&logo=android&logoColor=white&color=1A73E8"></a>
  &nbsp;
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/JotnarDev/material-island"><img alt="Get it on Obtainium" height="40" src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png"></a>
  <br>
  <sub>Android 12+ · ~3 MB · no account · updates itself from GitHub</sub>
</p>

<p align="center">If you like it, a ⭐ helps more people find it.</p>

<p align="center">🇪🇸 <a href="README.es.md">Versión en español</a></p>

---

Material Island turns the camera cutout into a live surface for whatever is happening on your phone: music, calls,
timers, navigation, downloads, charging, verification codes, unread chats, plus weather, your next calendar event and scores with
**At a glance**. It runs on any phone with Android 12 or newer, including Xiaomi, Redmi and POCO.

## Why these permissions?

- **Notification access** reads the notifications of apps that are playing, calling, timing or navigating, so the island can show them. Nothing is stored or sent anywhere.
- **Accessibility service** is only used to draw the island over the status bar and to know which app is in front (to hide music while its own app is open). It never reads what's on your screen.
- **ADB is optional.** It only skips Android's "restricted settings" screen and enables extras like *Island first*.
- **You can check all of this.** The code is open: see [How it works](#how-it-works) and [`service/`](app/src/main/java/dev/jotnar/materialisland/service/).

## See it in motion

<table>
  <tr>
    <td width="50%" valign="top"><b>Music in three steps</b><br>Pill, card, then full album art with the controls on a progressive blur.<br><br><img src="docs/gifs/music.gif" alt="Music: compact pill, card and full album art with blurred controls"></td>
    <td width="50%" valign="top"><b>Liquid drop</b> (slow motion)<br>An optional open style: a drop stretches off the camera, breaks away and opens.<br><br><img src="docs/gifs/liquid-drop.gif" alt="Liquid drop open animation in slow motion"></td>
  </tr>
  <tr>
    <td valign="top"><b>Charging</b><br>Wavy ring, count-up and a light sweep. Tap for time to full, speed, cable or wireless, watts and temperature.<br><br><img src="docs/gifs/charging.gif" alt="Charging pill and detail card"></td>
    <td valign="top"><b>Shortcuts from the camera</b><br>Tap the camera at rest for a card of shortcuts you choose.<br><br><img src="docs/gifs/shortcuts.gif" alt="Shortcuts card opened from the camera"></td>
  </tr>
  <tr>
    <td valign="top"><b>Calls</b><br>Answer from the island, then keep the call timer in a compact pill.<br><br><img src="docs/gifs/call.gif" alt="Incoming call answered from the island"></td>
    <td valign="top"><b>Messages</b><br>A banner with reply and mark-as-read that goes back into the camera.<br><br><img src="docs/gifs/message.gif" alt="Message banner"></td>
  </tr>
</table>

## Screenshots

<table>
  <tr>
    <td width="50%"><img src="docs/screens/music-expanded.png" alt="Music card with morphing album art and wavy seek bar"></td>
    <td width="50%"><img src="docs/screens/call-expanded.png" alt="Incoming call with answer and decline"></td>
  </tr>
  <tr>
    <td><img src="docs/screens/timer-expanded.png" alt="Timer card"></td>
    <td><img src="docs/screens/nav-expanded.png" alt="Navigation card"></td>
  </tr>
  <tr>
    <td><img src="docs/screens/ride-expanded.png" alt="Ride card with route stages, driver and plate"></td>
    <td><img src="docs/screens/delivery-expanded.png" alt="Food delivery card with route stages"></td>
  </tr>
  <tr>
    <td><img src="docs/screens/buds-expanded.png" alt="Earbuds battery card with left, right and case"></td>
    <td><img src="docs/screens/speaker-expanded.png" alt="Bluetooth speaker card with battery"></td>
  </tr>
  <tr>
    <td><img src="docs/screens/privacy-expanded.png" alt="Camera and microphone in use"></td>
    <td><img src="docs/screens/liquid-bar.png" alt="Liquid bar at rest, then shrunk into the camera with the island below"></td>
  </tr>
  <tr>
    <td><img src="docs/screens/msg-expanded.png" alt="Message with quick reply"></td>
    <td rowspan="2" valign="top"><img src="docs/screens/compact.png" alt="Compact states: music, timer, navigation, download, two activities at once, verification code, charging"></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screens/app.png" width="300" alt="Settings app with live preview and demo activities"></td>
  </tr>
</table>

<sub>All screenshots use the app's built-in demo activities.</sub>

## Features

| Activity | Compact | Expanded |
|---|---|---|
| **Music** | Cookie-shaped album art that spins while playing, plus an equalizer | Art morphs to a square on pause, wavy seek bar you can drag, shape-shifting play button |
| **Calls** | Pulsing green icon with "Incoming" or a live timer | Answer / decline / hang up, styled like the Pixel dialer |
| **Timers** | Live countdown | Big digits plus the Clock app's own actions |
| **Navigation** | Turn arrow and distance | Instruction, arrival time, exit |
| **Downloads & Live Updates** | Progress ring | Wavy progress bar plus actions (Android 16 promoted notifications) |
| **Messages** | Unread chats as bubbles to the left of the island | Read and reply without leaving the app you're in |
| **Verification codes** | Wide pill showing the code; tap to copy | — |
| **Charging** | Wavy ring with the level counting up | Time to full, normal or fast, cable or wireless, watts, temperature |
| **At a glance** | Weather, next event, live score or alert count next to the camera; swipe to switch | Weather with the next hours, next event from Google Calendar with a join button, live scores, alerts |
| **Rides & deliveries** | Vehicle and arrival time | Route with stages, driver and plate (Uber, DiDi, Rappi, DoorDash, Amazon…) |
| **Bluetooth** | Device and battery | Animated earbuds, headphones, speaker or laptop with left, right and case levels |
| **Privacy** | Green dot while the camera or microphone is in use | Which sensor and the app that was open |
| **System** | Low battery, ringer mode, airplane mode, wired headphones | — |

And also:

- **Three open styles.** *Morph* stretches the camera hole into a pill, *Liquid drop* drips the island out of the camera, and *Liquid bar* covers the status bar edge to edge with a Pixel-style clock, battery, signal and the items you pick (network speed, temperature, RAM…), then shrinks into the camera when something happens.
- **English and Spanish.** Follows the phone's language, or pick it for the app alone in Android's settings.
- **Setup guide.** A short wizard on first launch walks you through each permission.
- **Island first.** Turns off the system's pop-up notifications so the island is the only alert surface. They come back by themselves if the island stops.
- A **second activity** sits next to the island as a small bubble. Tap it to swap.
- **Configurable gestures** on the island (tap, hold, swipe in every direction) and on the resting camera (flashlight, screenshot, quick settings…).
- **Hides itself** in landscape, in fullscreen video and games, and on the lock screen.
- Respects **Do Not Disturb**. Haptics use the Pixel's vibration primitives.
- **Zero frames when idle.** Nothing animates unless something is playing.
- **Everything is adjustable** live: size, position, colours, spring bounce and speed, timings and per-app blocking. Your settings export as JSON.

## Install

### Download

1. Get `material-island-<version>.apk` from the [latest release](https://github.com/JotnarDev/material-island/releases/latest) and open it on your phone. Android will ask you to allow installs from your browser or file manager.
2. Open **Material Island** and turn on what it asks for: **notification access** and the **accessibility service**.
3. On Android 13 and newer, sideloaded apps can't get accessibility right away. If the switch is greyed out, go to
   **Settings → Apps → Material Island → ⋮ → Allow restricted settings**, then try again.
   Or grant everything at once over ADB, as shown below.

Requires Android 12 or newer. A phone with a centred hole-punch camera looks best.

### Xiaomi, Redmi and POCO (HyperOS / MIUI)

HyperOS adds a few extra locks. The app shows a warning card and shortcuts for each of them, but here is the full list:

1. **Restricted settings.** If the accessibility switch is greyed out, try turning it on once. Then go to
   **Settings → Apps → Manage apps → Material Island → ⋮ → Allow restricted settings** and turn it on again.
2. **Accessibility** lives under **Settings → Additional settings → Accessibility → Downloaded apps**.
3. **Autostart.** Go to **Settings → Apps → Manage apps → Material Island** and turn on **Autostart**.
   Without it, HyperOS kills the island after a while.
4. **Battery saver.** On the same screen, set **Battery saver → No restrictions**.
5. **ADB on Xiaomi** can't change secure settings or grant permissions by default. To use the ADB commands below,
   enable **Developer options → USB debugging (Security settings)**. Xiaomi asks you to sign in with a Mi account
   and have a SIM card inserted. Without it, only `cmd notification allow_listener` and `appops set` work.

### Build from source

You need JDK 17 and the Android SDK with the `android-37.2` platform.

There are two versions:

- **foss** is the one on GitHub. It has no ads and no Google libraries, and it updates itself from GitHub releases.
- **play** is for Google Play. It shows ads only in the settings screens (never on the island) and is updated by Play.

```bash
./gradlew assembleFossRelease
adb install -r app/build/outputs/apk/foss/release/app-foss-release.apk
```

### Grant permissions over ADB

This also skips Android's "restricted setting" warning:

```bash
P=dev.jotnar.materialisland
adb shell cmd notification allow_listener $P/$P.service.IslandNotificationListener
adb shell settings put secure enabled_accessibility_services $P/$P.service.IslandAccessibilityService
adb shell dumpsys deviceidle whitelist +$P
adb shell pm grant $P android.permission.BLUETOOTH_CONNECT
adb shell appops set $P RECEIVE_SENSITIVE_NOTIFICATIONS allow   # verification codes on Android 15+
adb shell pm grant $P android.permission.WRITE_SECURE_SETTINGS     # "Island first"
```

> [!WARNING]
> `settings put … enabled_accessibility_services` **replaces** the whole list. If you already use
> other accessibility services, append them separated by `:`.

Optionally, hide Android's own notification icons from the status bar, since the bubbles replace them. This resets when the phone reboots:

```bash
adb shell cmd statusbar send-disable-flag notification-icons   # use "none" to bring them back
```

## Privacy and internet

- The island itself never goes online, never tracks you and never shows ads. There is no account and no analytics.
- **At a glance** is the only feature that uses the internet, and only if you turn it on: weather from Open-Meteo and scores from ESPN. The calendar is read on the phone and never leaves it. Offline, only weather and scores pause.
- The app can check GitHub for a new version when you open it. You can turn that off in **About**.
- On a Pixel, one button in At a glance settings takes you to the launcher setting where you can turn Google's own At a glance on or off.

## How it works

- **The window** is a `TYPE_ACCESSIBILITY_OVERLAY` owned by an accessibility service. That lets it
  draw over the status bar without the "display over other apps" permission. The service only
  watches which app is in the foreground, so it can hide music while that app is open. It never
  reads screen content.
- **Only the island takes touches.** The rest of the window is passed through using the hidden
  `ViewTreeObserver.OnComputeInternalInsetsListener`. That API is limited to apps targeting
  SDK 30 or lower, so it is unlocked with [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass).
- **The data** comes from a `NotificationListenerService` (calls, timers, navigation, Live Updates,
  codes and chats), from `MediaSessionManager` (music, with the album colour picked by Palette)
  and from system broadcasts.
- **The camera position** is read from the display cutout path, so the island lines up with the
  real hole on any device. You can fine-tune it with calibration mode.

## Project map

```
app/src/main/java/dev/jotnar/materialisland/
├── data/IslandSettings.kt     ← every setting and its default
├── model/IslandItem.kt        ← activity kinds and their priority
├── core/
│   ├── IslandStore.kt         ← source of truth: ongoing activities, alert queue, unread chats
│   ├── NotificationParser.kt  ← notification → activity (add new rules here)
│   ├── MediaMonitor.kt        ← media sessions and album colour
│   ├── SystemEventMonitor.kt  ← charging, Bluetooth, ringer…
│   ├── Actions.kt             ← opening apps, flashlight, haptics
│   └── Demo.kt                ← fake activities for testing
├── overlay/
│   ├── IslandView.kt          ← the island itself: modes, sizes, colours, cards
│   ├── Components.kt          ← wavy bar, morphing art, equalizer
│   ├── IslandOverlay.kt       ← window, touch region, gestures, expand/collapse
│   └── Geometry.kt            ← finds the camera hole
├── service/                   ← accessibility service and notification listener
└── ui/                        ← the settings app
```

**Quick recipes**

- **Change a default:** edit `data/IslandSettings.kt`.
- **Add a new kind of activity:**
  1. Add it to `Kind` in `model/IslandItem.kt`.
  2. Detect it in `NotificationParser.parse`.
  3. Draw it in `CompactLeft`, `CompactRight` and `Card` in `IslandView.kt`.
- **Tweak the motion:** see `IslandBody` in `IslandView.kt` (`sizeSpec`, `transitionSpec`).
- **Use a different album-art shape:** change `artMorph` in `Components.kt`. Any `MaterialShapes.*` works.

## Built with

Kotlin 2.4 · Jetpack Compose (BOM 2026.11) · Material 3 Expressive (`MaterialShapes`, `MotionScheme.expressive()`, `LoadingIndicator`) · `androidx.graphics.shapes` morphing · AGP 9.5 · kotlinx.serialization · HiddenApiBypass.

## Identity

The logo, **Split**, shows the island and the camera dot pulling apart, caught mid-spring. That is
the same gesture the island makes when a second activity pops out beside it. The logo files, colours
and usage guide are in [`brand/`](brand/).

## Contributing

Bug reports, ideas and pull requests are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md) and the
[good first issues](https://github.com/JotnarDev/material-island/labels/good%20first%20issue). The
[project map](#project-map) and its quick recipes show where each part lives.

## Support the project

Every feature is free, and the island will never show ads. The current goal is to cover the Google Play developer
fee so Material Island can be installed and updated from the Play Store. If ads are ever added to fund development,
they will only appear in the settings screens, never on the island.

Ways to help:

- **Star** the repo and share it.
- **Report bugs** with your phone model. Every new device helps.
- **Buy me a coffee** on [Ko-fi](https://ko-fi.com/jotnardev).

<p><a href="https://ko-fi.com/jotnardev"><img alt="Support on Ko-fi" height="40" src="https://img.shields.io/badge/Ko--fi-Support-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white"></a></p>

## Credits

These projects provided ideas. Material Island's code is its own.

- [Expressive Cutout](https://github.com/EvanKoe/expressive-cutout): accessibility-overlay window, touch region, Expressive look
- [Dynamic Island for Android](https://github.com/ajitpal/dynamic-island-android): notification parsing
- [LiveBridge](https://github.com/appsfolder/livebridge): verification-code detection
- [OmniLand](https://github.com/andrasulthan-alt/OmniLand): secondary bubble, system events, resting gestures
- [SmartIsland](https://github.com/agupta07505/SmartIsland): quick reply and expanded cards

## License

[MIT](LICENSE) © JotnarDev
