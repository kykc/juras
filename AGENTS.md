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
| Package / applicationId | `automatl.juras` |

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

### Release build (signed, for sideloading to household phones)

Release signing reads a **git-ignored** `keystore.properties` (`storeFile`,
`storePassword`, `keyAlias`, `keyPassword`) pointing at `juras-release.jks` (also
git-ignored). Both must be kept to ship updates that install over the top (same
signature); if lost, updates need uninstall+reinstall. Build:

```powershell
$env:JAVA_HOME = "D:\Home\Kykc\Apps\scoop\apps\temurin21-jdk\current"
.\gradlew.bat :app:assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```

To publish an update, bump `versionCode` (and `versionName`) in
`app/build.gradle.kts`, rebuild, and reinstall on each device. Sideload installs
may be flagged by **Play Protect** ("Install anyway"), which is separate from the
"unknown sources" permission.

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
        ├── java/automatl/juras/
        │   ├── MainActivity.kt    # entry point (currently a Compose stub)
        │   └── ui/theme/          # Color.kt, Type.kt, Theme.kt (Material 3)
        └── res/
            ├── drawable/          # vector launcher foreground
            ├── mipmap-anydpi-v26/ # adaptive launcher icons (no PNGs; minSdk 26)
            └── values/            # strings, colors, themes
```

When the networking/protocol layer is added, keep it separate from UI — e.g. a
`automatl.juras.jura` package for transport/cipher/commands, with Compose UI
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

Pairing (subsequent device — confirmed from a JOE pairing packet capture): send
`@HP:<setupPIN>,<nameHex>,` (empty token) → machine returns `@hp4:<TOKEN>` and
shows a "pair with this device?" prompt → user confirms on the machine → reconnect
and send `@HP:<setupPIN>,<nameHex>,<TOKEN>` → `@hp4` (no colon) = paired; store the
token. There is **no** separate "pairing mode" and **no `@HW`/display-PIN** step on
this machine; `@HP:,,` just returns `@hp5:00`. (The `@HP:,,` → `@hp4:TOKEN` →
`@HW:01,PIN` sequence in `JURA_WIFI_PROTOCOL.md` is first-time Wi-Fi init, not
adding a device.) Easiest bootstrap for testing: reuse a known token via the app's
Advanced pairing tab.

### Sessions
Most commands must be wrapped in a **Remote Screen** session:
```
@TS:01   (ack @ts:01)  →  ...commands...  →  @TS:00 (ack @ts:00)
```
Brewing (`@TP:`) and counter reads (`@TR:32`) require this wrapper.

> **Unsolicited frames / desync (important):** the machine spontaneously emits
> `@TS` and `@TF:<status>` frames after auth and on state changes — *not only* in
> response to `@TM:50`. Reading "exactly one frame per command" therefore
> intermittently mis-pairs responses and shifts results (e.g. product counters land
> in the wrong slots). `JuraClient.request(cmd, expectedPrefix)` skips frames until
> the matching echo prefix (`@tr:32`, `@tg:c0`, `@tg:43`, `@ts`), which tolerates the
> pushes and self-heals leftover frames. Always match responses by prefix, never by
> position.

> **Brewing on isolated subnets — do NOT add a keepalive.** During a brew the
> machine streams progress (`@tv:`) and the phone goes silent (only ACKs). A
> firewall that closes the IoT->main return path after a short idle (~2 s, confirmed
> by capture) then drops the progress frames and the brew screen hangs. **Every
> app-side keepalive attempt made it worse and was reverted:** an `@TG:C0` read
> mid-brew stalls the machine's state machine (proven same-VLAN: works off, hangs
> on); even a **1-byte TCP urgent/OOB segment hung the machine hard (no buttons
> until a power cycle)** — the firmware can't tolerate *any* extra traffic during a
> brew. So there is no keepalive. The fix is **network-side**: a surgical firewall
> rule allowing the machine<->phone pair (or the phone on the IoT VLAN). Reads are
> unaffected (the phone is constantly sending requests). `forceQuit()` (long-press
> Stop) abandons locally without contacting the machine.

> **Shared token / single session (confirmed machine facts).** The machine accepts
> **one** TCP session at a time, and every device paired with the same setup PIN gets
> the **same token** — the device name is only a cosmetic label. So the machine
> *cannot tell two phones apart*: **concurrent/overlapping use is unsupportable by
> design** (JOE has the same wall); only clean sequential use works. It also appears
> to buffer outgoing status frames **per session token, not per socket**: when a
> session dies without being read to the end (hang, RST, force-quit, app killed), its
> queued `@tb`/`@tv`/`@tf` frames can be **re-delivered to the next connection** that
> authenticates with that token — so the next brew starts fine on the machine but the
> client immediately reads the *previous* brew's `@tf:00` and shows "enjoy" instantly
> (or desyncs and hangs).

> **"Locked keys" (alert bit 39) = the machine isn't hung, its keypad is locked.** A
> TCP remote session (`@TS:01`, and the `@TM:50` subscription) makes the machine **lock
> its physical buttons** while it serves the client — and it shows *nothing* on its own
> display to say so. Only the UDP status flag (bit 39) reveals it. So the earlier
> "machine hangs, no buttons until a power cycle" was almost certainly a **stuck
> remote-session lock**: a `@TS:01`/`@TM:50` session that never cleanly released (RST /
> uncleaned subscription) left the keys locked; the power cycle just cleared it. Keep
> remote sessions short and always released, and prefer UDP (which never locks keys).
>
> Consequence: **`readReport` reads neither `@TM:50` nor `@TS:01`.** The `@TM:50` push
> subscription waited for the machine to emit `@TF` frames and hung the whole stats read
> (up to 8 × the socket timeout) when it didn't; it's redundant now that the Status
> screen gets live flags over UDP. And the `@TS:01` Remote Screen session it ran under
> turned out unnecessary: **J.O.E. issues each stats command (`@TR`, `@TG:C0`, `@TG:43`)
> standalone, with no session** (confirmed by decompilation).
> Dropping `@TS:01` removed the keypad lock for stats and the interleaved `@TF` pushes
> that made `request()` skip-loop and occasionally hang. **The app now opens no remote
> session anywhere** — TCP is purely brief command/response (`@HP`, `@TP`, `@TG:FF`,
> `@TR`/`@TG` reads) with a short read timeout; all live state is UDP. Counters/
> maintenance are not in the UDP reply, so they stay TCP-on-demand.

> **⚠ Do NOT add drain-on-close (`closeGracefully`) or flush-on-open (`flushInput`).**
> Tried 2026-06: drain buffered input then FIN-close after each op, plus a 500 ms
> input flush before each op, to clear the per-token backlog above. **It made the
> machine hang on *every* operation, including status that worked fine before** — same
> fragility class as the `@TG:C0`/keepalive/OOB experiments. Reverted to plain
> `close()` (immediate, RST if data buffered) and no flush. This machine tolerates
> only the minimal request/response pattern; extra reads/half-closes destabilize it.
> The stale-backlog / two-phone problem is still **open** — solve it with packet
> captures first, not by adding TCP behavior blindly.

> **★ JOE streams live status/progress over UDP 51515, not TCP (key finding, decompiled).**
> JOE runs **two channels at once** per machine (one TCP channel, one UDP channel):
> - **TCP 51515 = commands only** — `@HP` auth, `@TP` brew start, `@TR`/`@TG` stats,
>   settings, language download. Brief request/response. JOE never holds a `@TS:01`
>   remote-screen stream for live data.
> - **UDP 51515 = live status + brew progress** — every **1000 ms** JOE unicasts a
>   plaintext datagram `0010A5F3` + *client IP* (4 bytes hex) + `0000000000000000`
>   to the machine, which replies with a plaintext binary status
>   packet. No cipher, no auth token in the poll. A **30 s** no-reply timeout drops the
>   connection. This is the *same* packet family as UDP discovery — the discovery
>   beacon reply already carries the full status payload.
>
> Reply layout (validate against a real capture): `[0:2]` total length; `[2:4]` marker, `& 0x0FFF == 0x05F3`, bit15 set,
> bit14 clear; `[4:20]` name; `[20:52]` machine id; `[52:68]` str; `[68:78]` version/
> counter words; byte `109` flags (`bit0==0` ⇒ product running → emit `@TV`, else
> `@TF`); `[110:len]` = the status payload bytes. JOE rebuilds `"@T"+("V"|"F")+":"+
> hex(payload)+"\r\n"` and runs it through the **same** `@TV`/`@TF` parser we already
> have. So our existing decoders work unchanged — only the transport differs.
>
> **Why this matters:** UDP is connectionless, so multi-phone status needs no single
> TCP session (each phone polls independently) — and keeping TCP minimal is exactly
> what this fragile firmware wants. Our current app streams status/progress over TCP
> (`@TM:50` push / `@TS:01`), the heavy path that serializes on the one session and
> stresses the machine. Fix direction: poll status over UDP like JOE; keep TCP for
> `@TP` and other commands only.
>
> **✓ VALIDATED on EF1030 / TT237W (2026-06).** The machine answers the UDP poll with
> **no prior TCP auth**, and **cross-VLAN** (phone on main net, machine on isolated IoT
> subnet) with no extra firewall rule beyond what already exists. Reply was 117 B =
> 110 header + 7-byte alert word; payload `000400000C0000` decoded to "Coffee ready"
> (bit 13) — byte-identical to our known-good TCP `@TF` capture, so the offsets in
> `UdpStatusReply` are correct for this hardware. The reply also carries module fw
> (`TT237W V08.27`), the custom name, and the machine model/fw (`EF1030…M V01.06`).
> Implemented in `protocol/transport/JuraUdpStatusClient` + `UdpStatusReply`; the
> **Status screen polls live over UDP** (`StatusViewModel.startLive`, ~1.5 s) while TCP
> is used only for the on-demand statistics read.
>
> **Brew = commands over TCP, progress over UDP (implemented; hardware test pending).**
> `BrewViewModel.start` does a brief TCP burst — `authenticate` then `JuraClient.startProduct`
> (`@TP`) — and **closes immediately** (no `@TS:01`, no stream read; the machine brews
> autonomously, confirmed by the old `forceQuit` behaviour). It then polls UDP (~1 s):
> while `productRunning` it feeds `@TV` payloads to `BrewProgressDecoder`; when the reply
> goes idle (`@TF`) after having run, the brew is done; if it never starts within
> `STARTUP_TIMEOUT_MS` it reports the blocking `@TF` flag. `stop` sends `@TG:FF` in its
> own short TCP burst. **Open question to verify on hardware:** does `@TP` start a product
> *without* a preceding `@TS:01`? If not, re-add `@TS:01` but keep draining/closing TCP so
> it can't backpressure-wedge the machine. `authenticate` now skips stray frames so a
> leftover TCP frame can't be read as the auth result.

### Key commands
| Command | Purpose |
|---|---|
| `@TP:<32 hex>` | Brew a product (16 fields × 2 hex). Field 0 = product code, 2 = strength (01–0A), 3 = water ml÷5, 5 = milk foam, 6 = temp (00/01/02), 8 = `01`. |
| `@TG:C0` | Maintenance status: 3 bytes (cleaning, filter, descale) as % to service; `FF` = n/a. |
| `@TG:43` | Maintenance counters: 6 × 2-byte big-endian cycle counts. |
| `@TR:32,P` | Product counter page P (4 products × 2 bytes); pages 0–15 cover all. |
| `@TM:50` | **Subscribe** to status. Machine acks (`@tm:D0`) then **pushes** `@TF:<14 hex>` frames periodically until disconnect. Each push is a 7-byte (56-bit) alert word; bit N = `byte[N/8]`, mask `0x80 >> (N%8)` (per-byte, MSB-first). Decode via the model's XML `<ALERTS>` (EF1030 table lives in `MachineStateDecoder`). Verified: tank out → bit 1 "fill water"; ready → bit 13 "coffee ready". |
| `@TG:FF` / `@TG:01` | Cancel / advance current product step. |
| `@TF:02` | Restart machine. |

### Product codes (EF1030 / E6)
`02` Espresso · `03` Coffee · `04` Cappuccino · `06` Espresso Macchiato ·
`08` Milk Foam · `0D` Hot Water · `0F` Powder (inactive) · `28` Caffé Barista ·
`29` Barista Lungo · `31` 2 Espressi · `36` 2 Coffee.
Water amounts must be multiples of 5 ml. **The in-code source of truth is
`:protocol`'s `product/Ef1030Catalog`, derived from protocol reverse engineering** — trust it over `../JURA_WIFI_PROTOCOL.md`
§7, which has errors (it wrongly tags Caffé Barista / Barista Lungo as milk drinks;
they are **coffee + a bypass-water stream `F10`**, no milk). Which parameters each
product even has varies: 2 Espressi / 2 Coffee have **no strength**; Milk Foam has
**only** a milk amount (no water/temp/strength); Caffé Barista / Barista Lungo add
**bypass**. Each adjustable parameter is a nullable `Range` on `Product`.

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

The build proceeds in steps (tracked in the workspace's `andoid_app.md`).

### Guiding strategy

**Thin vertical slices, protocol-first within each slice, ordered by risk and
dependency — not by user-facing flow order.** The protocol layer is a hard
dependency for everything, so it leads; but we never build the whole protocol or
the whole UI in isolation. Each slice goes connect → command → parse → display
and is verified against the real machine (or `../jura.py`) before the next.

Two consequences that are **architectural rules**, not just preferences:

- **Protocol/transport code stays free of Android imports** (pure Kotlin module/
  package) so it is JVM-unit-testable and cross-checkable against `../jura.py`.
- **The connection manager is the single stateful owner** of the one allowed TCP
  session (the machine permits only one connection at a time). Everything else is
  stateless commands/parsers layered over it.

Build-order ≠ user-flow order: credentials are bootstrapped by hand (reuse a
token, as `../jura.py` does) so **pairing UI is deferred**; **safe reads come
before brewing**.

### Steps

- [x] **Step 0** — Project skeleton that compiles in Android Studio and runs an
  empty APK on an emulator.
- [x] **Step 1** — Project docs: this `CLAUDE.md`, `README.md`, `.gitignore`.
- [x] **Step 2** — Protocol core as pure Kotlin (cipher + framing), with JVM unit
  tests against known-good vectors from `../jura.py`. *(Done — `:protocol` module;
  `JuraCipher`, `JuraFrame`, `KeySelector`; `JuraCipherTest` passes including
  byte-for-byte golden frames.)*
- [x] **Step 3** — Transport + connection manager + `@HP:` auth handshake.
  *(Done — `transport/JuraConnection` owns the single TCP socket; `client/JuraClient`
  does `authenticate()` → `AuthResult` and session-wrapped reads.)*
- [x] **Step 4** — First vertical slice: a read flow with minimal UI. *(Done —
  `JuraClient.readReport()` reads product counters `@TR:32`, maintenance `@TG:C0`/
  `@TG:43`, and machine state `@TM:50`; `ui/MainScreen` + `MainViewModel` show it,
  with IP/PIN/device-name/token inputs persisted via `data/JuraSettings`.)*
  **Current state:** verified on real hardware (Jura E6 / EF1030) — auth, product
  counters, maintenance status/counters, and machine state all return correct live
  data. Machine state was corrected: `@TM:50` is a *subscribe* command (the `D0`
  in `@tm:D0` was a mis-parsed echo); real status arrives in streamed `@TF:` push
  frames, decoded by `MachineStateDecoder` (tank-out → "fill water", ready →
  "coffee ready" confirmed, locked in by unit tests).
- [x] **Step 5** — **App scaffolding: navigation + state.** *(Done.)* Single-Activity
  **Navigation Compose** (`ui/JurasApp`, type-safe `ui/Routes`) with a **bottom-nav
  shell** (*Brew* / *Status* / *Settings*); *Preset editor*, *Brewing*, *Pairing* are
  full-screen pushes. Startup gate → Pairing if no device. State persists in
  **DataStore** as JSON via `data/AppStateRepository` over `domain/AppState`
  (`PairedDevice`, `BrewPreset`); `JuraSettings` removed. **OOBE:** default state has
  no presets; on first pairing (clean state) `DefaultPresets.forProducts` seeds one
  preset per catalogue product at factory defaults (via `AppViewModel.pairDevice` →
  `AppStateRepository.pairDevice`, which only seeds when presets are empty).
  `AppViewModel` (shared) owns state + mutations; `StatusViewModel` runs the read
  flow. Read flow moved to `screens/StatusScreen`; `screens/BrewScreen` lists
  presets; `screens/PairingScreen` is manual token entry (real pairing = Step 6).
  Product catalogue + `Temperature` added to `:protocol` (`product/Ef1030Catalog`).
  Brewing/PresetEditor are placeholders. *(Verified in the emulator: navigation +
  migrated read flow work.)*
- [x] **Step 6** — **Pairing** flow + **UDP discovery**. *(Implemented; not yet
  hardware-tested.)* `:protocol`: `discovery/JuraDiscovery` broadcasts the
  `0010A5F3…` scan on UDP/51515 and parses replies into `DiscoveredMachine`
  (derived from protocol analysis; unicast replies, so no extra
  permission); `JuraClient.submitPairingPin()` sends `@HW:01,<pin>`. `:app`:
  `PairingViewModel` runs scan + a held-connection handshake (`@HP:,,` →
  `@hp4:TOKEN`, keep socket open, `@HW:01,<displayPin>` → `@hw:01`) → builds a
  `PairedDevice`. `PairingScreen` is now guided (scan → pick → details → pair) with
  an **Advanced** manual-entry fallback (manual IP + token, as before).
  **Real handshake confirmed from a JOE pairing packet capture and reworked accordingly:**
  1. `@HP:<setupPIN>,<nameHex>,` (empty token) → machine replies `@hp4:<TOKEN>` and
     shows a "pair with this device?" prompt on its display.
  2. Hold the connection while the user confirms on the machine.
  3. Verify: reconnect and send `@HP:<setupPIN>,<nameHex>,<TOKEN>` → `@hp4` (no
     colon) = authenticated/paired; store the token.

  There is **no separate pairing mode and no `@HW`/display-PIN step** — `@HP:,,`
  just returns `@hp5:00` (unknown device). The issued token is effectively
  per-machine/PIN (it matched the existing token even for a new device name).
  `PairingViewModel.startPairing` sends the request and waits (90s read timeout) for
  the machine reply, which only arrives **after** the user confirms on the machine —
  receiving `@hp4:<TOKEN>` is the success signal (no separate Verify step). **Verified
  end-to-end on hardware.** `PairedDevice` distinguishes `label` (this phone's name,
  sent in `@HP` + shown on the machine prompt) from `machineName` (the machine's
  discovered name, for display via `displayName`).
- [x] **Step 7** — **Brew preset editor**. *(Done; builds, not yet hardware-tested —
  it's pure app-side so no machine needed.)* `screens/PresetEditorScreen`: label +
  product dropdown + strength/water/milk sliders (bounded by `Ef1030Catalog`,
  strength/milk shown only when the product supports them) + temperature chips;
  Save/Delete/Cancel via `AppViewModel.upsertPreset`/`deletePreset`. Brew cards now
  separate **edit** (tap or long-press the card) from **brew** (distinct "Brew"
  button) to avoid mis-taps; `+` FAB adds a new preset.
- [x] **Step 8** — **Brewing** (`@TP:`). *(Implemented; builds + payload unit tests
  pass. NOT yet hardware-tested — first real dispense.)* `:protocol`: `TpPayload`
  builds the 32-hex body (strength=F3, water=F4÷5, milk=F6 raw, temp=F7, F9=01,
  bypass=F10÷5; `TpPayloadTest` matches the §6 golden example); `JuraClient.brew`
  wraps `@TS:01`/`@TP`/`@TS:00`, streams `@tb`/`@tv:` via `BrewProgress`, ends on
  `@tf:`/`@tp:00`; stop via `@TG:FF`. `:app`: `BrewViewModel` (connect→auth→brew on
  IO) + `screens/BrewingScreen` (explicit **Start brewing** confirm → progress bar →
  **Stop** → Done). Reached by the card **Brew** button.
  **CAUTION / open question:** the JOE app's decompiled `@TP:` literal is **17
  bytes** (`@TP:0D00002C…`, 34 hex), but `../jura.py` + protocol §6 use **16 bytes**
  (32 hex) and that's what's implemented. If a real brew is rejected or
  mis-doses, capture a JOE brew pcap (like pairing) to confirm the exact field
  layout/length. Bypass placement (F10) and units (÷5) are unverified — test a
  barista product specifically.
  **Brew progress captions:** the `@tv:` state byte (byte 0) maps to names in
  `BrewStateNames`, derived from protocol reverse engineering on EF1030 hardware
  (`0x24` is "Coffee ready", `0x3E` is "Enjoy"; the reference client had these wrong). Active **dispensing**
  is not a caption — it's the product's `ProgressAdjust` value
  (`DISPENSING_STATES = {0x34,0x37,0x3C,0x41}`), which triggers the live ml/percent
  progress (so it now works for hot water/milk too, not just espresso). Confirmed on
  hardware: brewing works; `0x21`→"Heating up", `0x40`→"Fill water tank".
- [x] **Step 9 — CMP migration (Step 2: shared Compose layer).** *(Done; builds
  clean for both Android and JVM targets.)*  All Compose screens and theme moved
  from `:app` to `:shared/commonMain`; `:app` is now a thin Android shell
  (`MainActivity` + `JurasApp` + `AppViewModel` + `AppStateRepository`).
  Details:
  - `:protocol` converted to KMP (`commonMain` + `androidTarget` + `jvm`);
    sources in `src/commonMain/kotlin`.
  - `:shared` module (KMP + CMP 1.8.0): `commonMain` holds domain
    (`AppState`, `ConfigTransfer`, `Routes`), ViewModels (`PairingViewModel`,
    `StatusViewModel`, `BrewViewModel`), and all Compose screens.
    `androidMain`/`jvmMain` hold platform actuals.
  - Platform `expect`/`actual` in `shared/src/*/platform/`:
    - `FilePicker.kt` — `rememberOpenFileLauncher` / `rememberSaveFileLauncher`
      (Android: SAF; JVM: `JFileChooser`)
    - `DeviceName.kt` — `defaultDeviceName` (Android: `Build.MODEL`; JVM: `user.name`)
    - `DateFormat.kt` — `formatClock(millis)` (java.text.SimpleDateFormat in both JVM actuals)
    - `theme/PlatformTheme.kt` — `platformColorScheme(darkTheme)` (Android: dynamic
      color on API 31+; JVM: static)
  - CMP resources in `shared/src/commonMain/composeResources/drawable/`; generated
    accessor package is `juras.shared.generated.resources`.
  - Platform actual files use names different from the expect file (`PlatformTheme.kt`
    not `Theme.kt`) to avoid JVM duplicate-class collision.
  - Next: Step 10 — abstract `AppStateRepository` behind a KMP interface, move
    `AppViewModel` to shared; Step 11 — `:desktopApp` entry point.
- [x] **Step 10 — Move AppViewModel + JurasApp to shared.** *(Done; builds clean
  for both Android and JVM targets.)*  `:app` is now the absolute minimum shell:
  `MainActivity` + `AppStateRepository` (DataStore). Everything else is in `:shared`.
  Details:
  - `AppStateStore` interface in `shared/commonMain/domain/` — platform-agnostic
    persistence contract (`val state: Flow<AppState>` + suspend mutators).
  - `AppStateRepository` in `:app` now `implements AppStateStore`; unchanged internals.
  - `AppViewModel` moved to `shared/commonMain/ui/`; takes `AppStateStore` (no longer
    `AndroidViewModel`). Identical logic, depends on `ViewModel` from JetBrains lifecycle.
  - `JurasApp` moved to `shared/commonMain/ui/`; takes `store: AppStateStore`; creates
    `AppViewModel` via `viewModel { AppViewModel(store) }` (JetBrains lifecycle lambda
    factory). Uses `Res.drawable.ic_coffee` (CMP resources) for the Brew tab icon.
  - `MainActivity` creates `AppStateRepository(applicationContext)` as a lazy val and
    passes it to `JurasApp(store = repository)`.
- [x] **Step 11 — `:desktopApp` module (Compose for Desktop entry point).** *(Done;
  window launches and runs cleanly.)* New Gradle module `desktopApp/` (KMP, jvmMain
  only). Details:
  - `desktopApp/build.gradle.kts`: depends on `:shared`, `compose.desktop.currentOs`,
    `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `kotlinx-coroutines-swing`
    (required to provide `Dispatchers.Main` on JVM/AWT).
  - `FileAppStateStore` — JSON-file backed `AppStateStore` in
    `~/Library/Application Support/Juras/app_state.json` (mac),
    `%APPDATA%/Juras/` (win), `~/.config/juras/` (linux). Write-first-then-update
    pattern with `Mutex` for thread safety.
  - `Main.kt` — CMP `application { Window(...) }` entry point; 390×780 dp portrait
    window. Passes `FileAppStateStore()` to `JurasApp(store = …)`.
  - Two runtime fixes needed over Android:
    1. `kotlinx-coroutines-swing` provides `Dispatchers.Main` (AWT event loop).
    2. All `viewModel()` no-arg calls replaced with `viewModel { ClassName() }` —
       Android's default factory uses reflection to find no-arg constructors; the
       desktop implementation does not; explicit lambda factories work on both.
  - Launch: `./gradlew :desktopApp:run`.

### Architecture decisions (UI + state)

- **One Activity, Navigation Compose.** Screens are composable destinations, not
  separate Activities (shared ViewModels, back stack, transitions).
- **Persistence: DataStore + kotlinx.serialization JSON** holding one `AppState`
  (`pairedDevice: PairedDevice?`, `presets: List<BrewPreset>`), exposed as a
  reactive `Flow` by `AppStateRepository`. One paired machine for now.
- **Preset = a configured product.** `BrewPreset` (app domain) stores chosen
  values and references a product by code. The product **catalogue** (codes,
  ranges, which fields apply, defaults) is **per-model reference data in
  `:protocol`** — hardcode `Ef1030Catalog` now (from `../jura.py` PRODUCTS / the
  EF1030 XML), parse from machine XML later. `Temperature` (LOW/NORMAL/HIGH →
  wire 0/1/2) lives in `:protocol`; presets/state live in `:app`.
- Deps added along the way: `navigation-compose`, `kotlinx-serialization`
  (plugin + json), `datastore`, `sh.calvin.reorderable` (drag-to-reorder presets).
- Brew list (`screens/BrewScreen`) is a reorderable `LazyColumn`: each card has a
  **drag handle** (`longPressDraggableHandle` — hold to reorder so scrolling past it
  doesn't accidentally trigger it; persisted via `AppViewModel.reorderPresets`),
  **body tap/long-press** (edit), and a **Brew** button — three distinct actions.
  Local order state is synced from the persisted flow via `LaunchedEffect` and
  committed on drag stop.
- **Config import/export:** `domain/ConfigTransfer` (`ExportedConfig` envelope with
  required `format`/`version` validation fields + `ConfigCodec`) serializes device
  + presets to **YAML** via `kaml`. Settings uses Storage Access Framework pickers
  (`CreateDocument`/`OpenDocument`); import parses+validates, then replaces state
  after a confirm dialog (`AppViewModel.parseConfig`/`applyConfig` →
  `AppStateRepository.replaceConfig`). The file holds the pairing token — sensitive.
- **Quick brew (one-time, not saved):** a "Quick brew" header button on the Brew
  screen opens `screens/PresetEditorScreen` in quick-brew mode (it takes `onBrewNow`
  instead of `onSave`/`onDelete`; same screen, two modes). "Brew" stashes the
  ephemeral `BrewPreset` in `AppViewModel.pendingBrew` and navigates to
  `Route.Brewing(presetId = null)`, which resolves the preset from `pendingBrew`
  rather than the saved list. Saved-preset brews pass a non-null `presetId`.

When you complete a step, update this checklist and the "current state" note so
the next session knows where things stand.

