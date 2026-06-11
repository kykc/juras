# Juras

An Android app for controlling **JURA coffee machines** over your local Wi-Fi
network — a clean, modern alternative to JURA's official *J.O.E.* app.

Juras talks directly to a JURA machine fitted with a **WiFi Smart Connect v2**
module using its local TCP protocol, so discovery, pairing, brewing, and status
checks all happen on your own network with **no cloud account required**.

> **Status: v0.1 — feature complete.** Discovery, pairing, brewing, presets, and
> status reads all work and are verified on real hardware (a **JURA E6 / EF1030M**
> with a TT237W Smart Connect module). The underlying protocol is shared across
> all J.O.E.-supported machines, so other models can be added over time.

---

## Features

- 📡 **Discover** your machine on the local network (UDP broadcast — no IP typing).
- 🔌 **Pair** with the machine: enter your setup PIN, confirm on the machine's
  screen, done. (An "Advanced" path lets you enter a known IP + token directly.)
- ☕ **Brew presets** — one per product, with the parameters each product actually
  supports (strength, water, temperature, milk foam, and bypass for the Barista
  drinks), bounded to the machine's valid ranges.
- 🎛️ **Customise & organise** — add/edit/delete presets, and **drag to reorder**
  them (hold the handle).
- ⚡ **Quick brew** — a one-time custom brew for a guest, without saving a preset.
- 🟢 **Live brewing** — start a brew and watch real-time progress (heating,
  grinding, dispensing with a water gauge), with a **Stop** button.
- 📊 **Status** — per-product brew counters, maintenance status (cleaning / filter
  / descaling), maintenance cycle counters, and live machine flags (e.g. "Fill
  water tank", "Coffee ready").

On first pairing the preset list is **seeded with every product** the machine
supports, so it's usable immediately.

---

## Requirements

- **Android 8.0 (API 26)** or newer.
- A JURA machine with a **WiFi Smart Connect v2** module, on the **same Wi-Fi
  network** as the phone.
- The machine's **setup PIN** (the one configured during its initial registration).

> Note: machine discovery uses UDP broadcast, which doesn't traverse an Android
> emulator's NAT — test discovery on a real device. The "Advanced" manual pairing
> works anywhere.

---

## Using the app

1. **Settings → Connect a machine** (or the startup prompt on first launch).
2. **Scan**, pick your machine, enter the **setup PIN**, tap **Start pairing**.
3. Confirm *"pair with this device?"* on the machine's display.
4. You're in — the **Brew** tab is populated with default presets. Tap **Brew** on
   a card to make it, tap a card to edit it, or use **Quick brew** for a one-off.

---

## Building from source

You'll need **Android Studio** (recent stable), a **JDK 21**, and the **Android
SDK** (Android Studio installs it). The Gradle wrapper is included.

### Debug build (for development / emulator)

```bash
# Point JAVA_HOME at a JDK 21 first.
./gradlew :app:assembleDebug     # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug      # install on a running emulator/device
./gradlew :protocol:test         # run the protocol unit tests
```

Or just open the project in Android Studio, sync, pick a device (API ≥ 26), Run.

### Release build (signed, for sideloading to your phones)

To install on real phones and be able to update them later, build a **release APK
signed with your own keystore**. This is a one-time setup.

1. **Create a keystore** (keep it safe — you need it to ship updates):

   ```bash
   keytool -genkeypair -v -keystore juras-release.jks \
     -alias juras -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Create `keystore.properties`** in the project root (this file is git-ignored):

   ```properties
   storeFile=juras-release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=juras
   keyPassword=YOUR_KEY_PASSWORD
   ```

3. **Build:**

   ```bash
   ./gradlew :app:assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
   ```

   When `keystore.properties` is present, the release build is signed
   automatically; otherwise it builds unsigned.

### Installing the APK on a phone

Copy `app-release.apk` to the phone (USB, cloud drive, etc.) and open it. Allow
**"install unknown apps"** for whatever app opens it. If Android shows **"Blocked
by Play Protect"**, tap **More details → Install anyway** — that's separate from
the unknown-sources permission and is the usual reason a sideload appears to fail.

To ship an **update**, bump `versionCode` (and `versionName`) in
`app/build.gradle.kts`, rebuild the release APK, and install it over the existing
one (same keystore = installs in place, keeping presets and pairing).

---

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3), single-Activity **Navigation
  Compose** with a bottom-nav shell (Brew / Status / Settings).
- **DataStore** + kotlinx-serialization for persistence; reactive state via
  Kotlin coroutines/Flow.
- **Gradle** (Kotlin DSL) with a version catalog.
- Two modules: a pure-Kotlin **`:protocol`** library (no Android dependencies,
  JVM-unit-tested) and the **`:app`** UI.
- minSdk 26 · target/compileSdk 36.

## Project structure

```
app/                                  Android app (Compose UI)
  src/main/java/automatl/juras/
    ui/        screens, view models, navigation
    data/      DataStore persistence (AppStateRepository)
    domain/    persisted models (PairedDevice, BrewPreset)
protocol/                             Pure-Kotlin JURA protocol library
  src/main/kotlin/automatl/juras/protocol/
    JuraCipher / JuraFrame            nibble cipher + wire framing
    transport/  JuraConnection        single TCP session
    discovery/  JuraDiscovery         UDP machine discovery
    client/     JuraClient            auth, reads, brewing, progress decoding
    product/    Ef1030Catalog         per-model product definitions
  src/test/                           JVM unit tests (cipher vectors, @TP payload, …)
gradle/libs.versions.toml             Dependency versions (version catalog)
```

For deep technical and reverse-engineered **protocol** notes (cipher, framing,
auth/pairing, command reference, brew state codes), see [`CLAUDE.md`](CLAUDE.md).

---

## Roadmap

v0.1 is feature complete:

- [x] Project skeleton, build, and documentation
- [x] Protocol core — cipher & framing (unit-tested against the reference client)
- [x] Transport, authentication, and machine discovery
- [x] Status reads — product counters, maintenance, machine flags
- [x] App scaffolding — navigation, persistence, preset model
- [x] Pairing (guided discovery + on-machine confirmation, and manual fallback)
- [x] Preset editor (add / edit / delete / reorder) + one-time quick brew
- [x] Brewing with live progress
- [x] Signed release builds for sideloading

Possible future work: support for additional machine models (parse the machine's
definition XML at runtime instead of the bundled EF1030 catalogue), home-screen
widgets, and richer maintenance flows (cleaning / descaling guidance).

---

## Disclaimer

This is an independent, unofficial project. It is not affiliated with, endorsed
by, or supported by JURA. "JURA" and "J.O.E." are trademarks of their respective
owners. The protocol it speaks was reverse-engineered for interoperability; use
it with your own machine at your own risk.
