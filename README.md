# Juras

An Android app for controlling **JURA coffee machines** over your local WiFi
network — a clean, modern alternative to JURA's official *J.O.E.* app.

Juras talks directly to a JURA machine fitted with a **WiFi Smart Connect v2**
module using its local TCP protocol, so brewing and status checks happen on your
own network with no cloud account required.

> **Status: early development.** The project currently builds and runs as a
> minimal app skeleton. Coffee-machine features are being added step by step —
> see [Roadmap](#roadmap).

---

## Planned features

- ☕ Brew products (espresso, coffee, cappuccino, …) with custom strength, water,
  temperature, and milk settings
- 🔧 Read maintenance status (cleaning / filter / descaling) and cycle counters
- 📊 Read per-product brew counters
- 📡 Discover the machine on the local network
- 🔌 Pair securely with the machine (PIN-based)

Reference machine for development: **JURA E6 (EF1030M)** with a TT237W Smart
Connect module. The underlying protocol is shared across all J.O.E.-supported
machines, so other models can be added over time.

---

## Requirements

- **Android 8.0 (API 26)** or newer
- A JURA machine with a **WiFi Smart Connect v2** module, on the same WiFi network
- The machine's setup PIN and a pairing token (obtained during pairing)

## Building from source

You'll need:

- **Android Studio** (recent stable) and a **JDK 21**
- The **Android SDK** (Android Studio installs this)

Then either:

**Android Studio** — open the project folder, let Gradle sync, choose a device or
emulator (API ≥ 26), and press Run.

**Command line** — the Gradle wrapper is included:

```bash
# Point JAVA_HOME at a JDK 21 first, then:
./gradlew :app:assembleDebug     # build a debug APK
./gradlew :app:installDebug      # install on a running emulator/device
```

The built APK lands in `app/build/outputs/apk/debug/`.

---

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gradle** (Kotlin DSL) with a version catalog
- minSdk 26 · target/compileSdk 36

## Project structure

```
app/src/main/java/audio/gpu/juras/   App code (entry point + UI theme)
app/src/main/res/                     Resources (strings, icons, themes)
gradle/libs.versions.toml             Dependency versions (version catalog)
```

For deeper technical and protocol notes (including the JURA WiFi protocol),
see [`CLAUDE.md`](CLAUDE.md).

---

## Roadmap

- [x] Project skeleton (builds & runs an empty app)
- [x] Project documentation
- [ ] Local network transport + pairing
- [ ] Brewing
- [ ] Maintenance & counters
- [ ] Polished UI

---

## Disclaimer

This is an independent, unofficial project. It is not affiliated with, endorsed
by, or supported by JURA. "JURA" and "J.O.E." are trademarks of their respective
owners. The protocol it speaks was reverse-engineered for interoperability; use
it with your own machine at your own risk.
