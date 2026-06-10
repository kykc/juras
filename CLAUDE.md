# CLAUDE.md

Context and working notes for LLM agents (and humans) working on **Juras**, an
Android app for controlling JURA coffee machines over WiFi.

---

## 1. What this project is

Juras is a native Android app that talks to a **JURA coffee machine** fitted with
a **WiFi Smart Connect v2** module, over the machine's local TCP protocol (port
51515). The goal is a clean, modern alternative to JURA's own *J.O.E.* app:
brew products, read maintenance status, and read product counters directly from
the machine on the local network.

This is being built **step by step**. See [Roadmap](#7-roadmap--status) for the
current state — do not assume features exist; check the code.

---

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language | **Kotlin** |
| UI | **Jetpack Compose** + Material 3 |
| Build | **Gradle (Kotlin DSL)** with a version catalog (`gradle/libs.versions.toml`) |
| AGP | 8.10.1 |
| Kotlin | 2.1.0 (Compose compiler via `org.jetbrains.kotlin.plugin.compose`) |
| Gradle wrapper | 8.13 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| JVM bytecode target | 11 |
| Package / applicationId | `audio.gpu.juras` |

Versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
Prefer adding dependencies there (the catalog) rather than inline in
`app/build.gradle.kts`.

---

## 3. Build & run

### Local dev environment (developer's machine)

These paths are specific to the current developer's Windows machine — adjust if
yours differs:

- Android SDK: `E:\Bg\android-sdk` (also recorded in `local.properties`, which is
  git-ignored)
- Android Studio: `D:\Home\Kykc\Apps\Jetbrains\Android Studio`
- JDK 21: `D:\Home\Kykc\Apps\scoop\apps\temurin21-jdk\current`
- System images available: `android-33`, `android-34-ext9`, `android-36`

### From Android Studio

Open the `juras/` folder as a project, let Gradle sync, pick an emulator (any
API ≥ 26; `android-36` matches target best), and Run.

### From the command line

The Gradle **wrapper** is committed, so no system Gradle is needed — but
`JAVA_HOME` must point at a JDK 21 (the bundled wrapper uses it). On PowerShell:

```powershell
$env:JAVA_HOME = "D:\Home\Kykc\Apps\scoop\apps\temurin21-jdk\current"
cd juras
.\gradlew.bat :app:assembleDebug      # build debug APK
.\gradlew.bat :app:installDebug       # install on a running emulator/device
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

> Note: a system Gradle (e.g. 9.x via scoop) exists on the dev machine but is
> **only** used to (re)generate the wrapper. Day-to-day builds go through
> `./gradlew`, pinned to 8.13, which is compatible with AGP 8.10.1.

---

## 4. Project layout

```
juras/
├── settings.gradle.kts            # root project + module includes, repositories
├── build.gradle.kts               # top-level plugin declarations (apply false)
├── gradle.properties              # JVM args, AndroidX, config cache, etc.
├── gradle/
│   ├── libs.versions.toml         # version catalog — single source of versions
│   └── wrapper/                    # committed Gradle wrapper (8.13)
├── local.properties               # sdk.dir — NOT committed
└── app/
    ├── build.gradle.kts           # module config + dependencies
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/audio/gpu/juras/
        │   ├── MainActivity.kt    # entry point (currently a Compose stub)
        │   └── ui/theme/          # Color.kt, Type.kt, Theme.kt (Material 3)
        └── res/
            ├── drawable/          # vector launcher foreground
            ├── mipmap-anydpi-v26/ # adaptive launcher icons (no PNGs; minSdk 26)
            └── values/            # strings, colors, themes
```

When the networking/protocol layer is added, keep it separate from UI — e.g. a
`audio.gpu.juras.jura` package for transport/cipher/commands, with Compose UI
consuming it via a ViewModel.

---

## 5. JURA WiFi protocol — essentials

This is a condensed reference so an agent can work without the external docs.
The **full** protocol reference and a **known-working Python client** live in the
parent workspace (outside this git repo):

- `../JURA_WIFI_PROTOCOL.md` — full protocol spec (reverse-engineered from J.O.E.)
- `../jura.py` — working CLI client (cipher, auth, brew, stats, maintenance, shell)
- `../jura_creds.txt` — example credentials line (PIN, hex device name, auth token)

Reference target machine: **JURA E6 (EF1030M)**, Smart Connect module TT237W.

### Transport
- TCP, port **51515**, **one** session at a time (machine refuses/drops a 2nd).
- Machine is found via UDP broadcast beacon (article number at beacon bytes 68–69),
  or just use a known IP / mDNS.

### Wire framing
Every message (both directions) is framed:
```
0x2A | <key byte> | <encrypted payload> | 0x0D 0x0A
```
- `0x2A` (`*`) start sentinel; `0x0D 0x0A` (`\r\n`) end sentinel.
- A random **key byte** per message; pick `key` where `(key & 0x0F)` ∉ {14,15}.
- Payload is the ASCII command text (including trailing `\r\n`), encrypted.
- **SPECIAL bytes** `{0x00,0x0A,0x0D,0x26,0x1B}` are escaped anywhere they appear
  (key byte or ciphertext) as `0x1B, (B XOR 0x80)`.

### Cipher (symmetric nibble cipher — same function encrypts & decrypts)
```python
LUT_A = [1,0,3,2,15,14,8,10,6,13,7,12,11,9,5,4]
LUT_B = [9,12,6,11,10,15,2,14,13,0,4,3,1,8,7,5]
def cipher_nibble(nibble, counter, kH, kL):
    iB   = (nibble + counter + kH) % 256 % 16
    i11  = counter >> 4
    idx1 = (i11 + LUT_A[iB] + kL - counter - kH) % 256 % 16
    idx2 = (LUT_B[idx1] + kH + counter - kL - i11) % 256 % 16
    return (LUT_A[idx2] - counter - kH) % 256 % 16
```
Each byte = two nibbles; `counter` starts at 0, +2 per byte;
`kH=(key>>4)&0xF`, `kL=key&0xF`.

### Authentication (first exchange after connect)
Send `@HP:<b_val>,<hex_d_val>,<a_val>`:
- `b_val` — machine setup PIN (numeric, from initial registration)
- `hex_d_val` — device display name as hex ASCII (e.g. "Pixel 9 Pro" → `506978...`)
- `a_val` — 64-hex auth token issued by the machine during pairing (stored verbatim)

Responses: `@hp4` (no colon) = accepted, stay open · `@hp4:TOKEN` = PIN challenge ·
`@hp5:00` = unknown device, machine closes TCP.

Pairing flow: machine in pairing mode → send `@HP:,,` → get `@hp4:TOKEN` (save
TOKEN as `a_val`) → send `@HW:01,<4-digit-PIN>` → `@hw:01` on success. Easiest
bootstrap: sniff one J.O.E. session and reuse its token.

### Sessions
Most commands must be wrapped in a **Remote Screen** session:
```
@TS:01   (ack @ts:01)  →  ...commands...  →  @TS:00 (ack @ts:00)
```
Brewing (`@TP:`) and counter reads (`@TR:32`) require this wrapper.

### Key commands
| Command | Purpose |
|---|---|
| `@TP:<32 hex>` | Brew a product (16 fields × 2 hex). Field 0 = product code, 2 = strength (01–0A), 3 = water ml÷5, 5 = milk foam, 6 = temp (00/01/02), 8 = `01`. |
| `@TG:C0` | Maintenance status: 3 bytes (cleaning, filter, descale) as % to service; `FF` = n/a. |
| `@TG:43` | Maintenance counters: 6 × 2-byte big-endian cycle counts. |
| `@TR:32,P` | Product counter page P (4 products × 2 bytes); pages 0–15 cover all. |
| `@TM:50` | Machine state bitmask (alerts: water, grounds, no beans, heating, ready…). |
| `@TG:FF` / `@TG:01` | Cancel / advance current product step. |
| `@TF:02` | Restart machine. |

### Product codes (EF1030 / E6)
`02` Espresso · `03` Coffee · `04` Cappuccino · `06` Espresso Macchiato ·
`08` Milk Foam · `0D` Hot Water · `0F` Powder · `28` Café Barista ·
`29` Barista Lungo · `31` 2 Espressi · `36` 2 Coffee.
Water amounts must be multiples of 5 ml. Per-product defaults/ranges are in the
machine XML (`assets/documents/xml/EF1030/1.4.xml` inside the J.O.E. APK) and
summarized in `../JURA_WIFI_PROTOCOL.md` §7.

### Model portability
The cipher, framing, auth, and command set are **identical across all J.O.E.-
supported machines**. What varies per model is data, defined in that model's
XML (`assets/documents/xml/<modelId>/<version>.xml`): product catalogue, the
count/order of maintenance `TEXTITEM`s, and the `@TM:50` alert-bit meanings. To
support a new model, parse its XML rather than hardcoding.

> The Python client (`../jura.py`) is the fastest way to validate any protocol
> assumption against a real machine before porting logic to Kotlin.

---

## 6. Conventions & guidance for agents

- **Match the surrounding style.** This is a standard Android Studio Compose
  project layout; keep it idiomatic.
- **Versions live in the catalog** (`gradle/libs.versions.toml`). Don't hardcode
  dependency versions in `app/build.gradle.kts`.
- **Don't commit secrets.** `local.properties`, keystores, and `jura_creds.txt`
  are not part of the repo. Credentials/tokens belong in app storage at runtime,
  not in source.
- **Keep transport/protocol code testable and UI-independent** so it can be unit-
  tested on the JVM without an emulator (the cipher in particular has known test
  vectors via the Python client).
- **Verify against reality.** When implementing protocol behavior, cross-check
  with `../jura.py` and, for edge cases, the reference decompiled app sources.
- The reference materials in the parent directory are **outside this git repo** —
  they won't be present after cloning. Treat the condensed spec in §5 as the
  in-repo source of truth and copy anything else you need into the repo (docs or
  tests) rather than relying on `../`.

---

## 7. Roadmap & status

The build proceeds in steps (tracked in the workspace's `andoid_app.md`):

- [x] **Step 0** — Project skeleton that compiles in Android Studio and runs an
  empty APK on an emulator. *(Done — current state: a Compose "Hello" stub.)*
- [x] **Step 1** — Project docs: this `CLAUDE.md`, `README.md`, `.gitignore`.
- [ ] **Later** — Networking/transport layer (cipher, framing, auth), machine
  discovery, brew/maintenance/counter features, and UI. Build and verify each
  increment against a real machine (or the Python client) before moving on.

When you complete a step, update this checklist and the "current state" note so
the next session knows where things stand.
