# Jura WiFi Smart Connect v2 — Protocol Reference

Reference of the protocol implemented in `./protocol` obtained by reverse-engineering (traffic sniffing).

Verified against a **Jura E6 (EF1030M V01.06)** with Smart Connect v2 module **TT237W V08.27**.

> **Read this first — the two-channel model.** The machine talks on **two channels, both
> on port 51515**: **TCP for commands** and **UDP for live status/progress** (and
> discovery). The official app keeps TCP to short command bursts and reads all live state
> over UDP. This is not a stylistic choice — the firmware is **fragile** if you hold TCP
> open, stream status over it, or send extra TCP traffic (see §5, §11). Building a client
> the TCP-only way (as the original of this document assumed) leads to hangs, a locked
> keypad, and cross-client conflicts. The sections below reflect the corrected model.

---

## 1. Transport — two channels

Both channels use **port 51515**.

### 1.1 TCP command channel

| Parameter | Value |
|---|---|
| Protocol | TCP, port **51515** |
| Framing | custom nibble cipher (§2–3) |
| Max sessions | **1** — the machine accepts a single TCP connection at a time |
| Usage | **commands only**, as short request/response bursts |

TCP carries commands — authenticate (`@HP`), start product (`@TP`), cancel (`@TG:FF`),
statistics reads (`@TR`/`@TG`) — and nothing else. A well-behaved client **connects,
authenticates, sends the command(s), and closes**; it does **not** keep the connection
open, does **not** open a Remote Screen session (`@TS:01`), and does **not** stream live
status over TCP. Our client opens a fresh short-lived TCP connection per command burst.

### 1.2 UDP status / progress channel

Live machine state **and brew progress** are delivered over **UDP by polling**. This is
how the official app drives its live UI. It is **connectionless**: no handshake, no authentication,
no single-client limit — any number of clients can poll independently.

**Poll (client → machine), 16 bytes, plaintext (no cipher):**

```
0010A5F3 | <machine IPv4: 4 bytes> | 00 00 00 00 00 00 00 00
```

- `0010A5F3` — magic (shared with discovery, §1.3).
- machine IPv4 — the **machine's own** address as 4 raw bytes. It is a payload field that
  the official app copies in; the reply is routed by the UDP source address, not this field (the
  machine appears to ignore/echo it).
- 8 trailing zero bytes.

Send from an ephemeral UDP socket to `machine:51515`; the machine **unicasts a reply to
the sender**. The official app polls **~1×/second** and treats **30 s** with no reply as a
disconnect. Poll repeatedly and display the latest reply — **each reply is a complete,
self-contained snapshot**, not a delta, so "latest wins" and nothing needs to be tracked
across polls. UDP loss is harmless: a missed poll just means one slightly-stale second.

**Reply (machine → client), plaintext:**

| Offset (bytes) | Field |
|---|---|
| 0–1 | total length (big-endian u16) |
| 2–3 | marker; `value & 0x0FFF == 0x05F3`, bit 15 set, bit 14 clear |
| 4–19 | module name + firmware, ASCII space/NUL-padded (e.g. `TT237W V08.27`) |
| 20–51 | machine id — the user's custom name (e.g. `Juras`) |
| 52–67 | model + firmware string (e.g. `EF1030…M V01.06`) |
| 68–77 | version / counter words (article number at 68–69; see §8) |
| 109 | status flags — **bit 0 == 0 ⇒ a product is running**, else idle |
| 110 … *length* | **status payload** |

The status payload is exactly the `@TV`/`@TF` content the TCP channel would push, just
wrapped in this identity header. Rebuild it and reuse the ordinary decoders — **no new
parsing needed**:

```
running (byte109 bit0 == 0):  "@TV:" + hex(payload)   → brew progress  (§6, @TP)
idle    (byte109 bit0 == 1):  "@TF:" + hex(payload)   → alert bitmask  (§6, @TM:50 table)
```

An idle reply carries the 7-byte alert word (same bits as `@TM:50`); a running reply
carries the 16-byte brew-state structure (same as a `@tv:` frame).

> **Validated on EF1030 / TT237W.** The machine answers the poll **with no prior TCP
> auth** and **across VLANs** (phone on the main LAN, machine on an isolated IoT subnet)
> with no special firewall rule. An idle payload `000400000C0000` decoded to "Coffee
> ready" (bit 13) — byte-identical to the TCP `@TF` push. Reference implementation:
> `protocol/transport/JuraUdpStatusClient.kt` + `UdpStatusReply.kt`.

### 1.3 UDP discovery (broadcast)

Discovery is the same packet family, broadcast instead of unicast:

```
scan (broadcast → 255.255.255.255:51515):  0010A5F3 + 12 zero bytes
```

Each machine replies in the **exact format of §1.2** — so the discovery beacon reply
already carries the machine's full live status, identity, and the **article number**
(bytes 68–69, big-endian uint16) used to select its machine catalog (§8). For direct
scripting the IP can instead be hardcoded or taken from a DHCP lease.

---

## 2. Wire Format

Every message (both client→machine and machine→client) is framed identically:

```
0x2A | [escaped key byte] | [encrypted payload] | 0x0D 0x0A
```

- **`0x2A`** (`*`) — frame start sentinel
- **Key byte** — random single byte chosen per message; encodes the cipher key. If the key byte itself belongs to the SPECIAL set (see §3), it is escaped before sending.
- **Encrypted payload** — the ASCII command/response text (including the trailing `\r\n`), encrypted byte-by-byte.
- **`0x0D 0x0A`** (`\r\n`) — frame end sentinel (unescaped, always literal)

### SPECIAL byte escaping

Five byte values are reserved as framing characters and must be escaped wherever they appear (in the key byte or payload):

```
SPECIAL = {0x00, 0x0A, 0x0D, 0x26, 0x1B}
           null   \n    \r    &    ESC
```

Escaping: replace byte `B` with `0x1B, (B XOR 0x80)`.  
The cipher is designed so that `0x0E` and `0x0F` in the low nibble of the key are avoided (to prevent SPECIAL values appearing in the key byte itself).

---

## 3. Cipher

A **symmetric nibble cipher** — the same function is used for both encryption and decryption. This means the decryption routine in a client is identical to encryption.

```python
LUT_A = [1, 0, 3, 2, 15, 14, 8, 10, 6, 13, 7, 12, 11, 9, 5, 4]
LUT_B = [9, 12, 6, 11, 10, 15, 2, 14, 13, 0, 4, 3, 1, 8, 7, 5]

def cipher_nibble(nibble, counter, kH, kL):
    iB   = (nibble + counter + kH) % 256 % 16
    i11  = counter >> 4
    idx1 = (i11 + LUT_A[iB] + kL - counter - kH) % 256 % 16
    idx2 = (LUT_B[idx1] + kH + counter - kL - i11) % 256 % 16
    return (LUT_A[idx2] - counter - kH) % 256 % 16
```

Each plaintext byte is processed as two nibbles; `counter` starts at 0 and increments by 2 per byte. `kH = (key >> 4) & 0xF`, `kL = key & 0xF`.

**Key selection**: pick a random byte where `(key & 0x0F) not in {14, 15}`.

---

## 4. Authentication

Authentication is the first exchange after TCP connect. The client sends:

```
@HP:<b_val>,<hex_d_val>,<a_val>
```

| Field | Description |
|---|---|
| `b_val` | Machine setup PIN — the numeric PIN configured when the machine was first registered |
| `hex_d_val` | Device display name, hex-encoded ASCII (e.g. `"Pixel 9 Pro"` → `506978656C20392050726F`) |
| `a_val` | 64-char hex auth token issued by the machine in the `@hp4:TOKEN` response during pairing |

> **The token is per-machine/PIN, not per-device.** Every device paired with the same
> setup PIN receives the **same** `a_val` — the machine reissues the existing token
> regardless of the `hex_d_val` device name. So `hex_d_val` is cosmetic, and the machine
> cannot distinguish two clients that share a token. This is the root of the
> single-session / multi-phone behaviour in §5.

### Auth responses

| Response | Meaning | TCP state |
|---|---|---|
| `@hp4` (no colon) | Known device — credentials accepted | **Stays open**, proceed with commands |
| `@hp4:TOKEN` | PIN challenge — credentials not yet registered | Stays open, must complete PIN flow |
| `@hp5:00` | Anonymous / unknown device | Machine **closes TCP** immediately after sending |

### PIN pairing flow

1. Trigger pairing mode on the machine (via its display or the official app).
2. Connect and send `@HP:,,` (empty credentials).
3. Machine responds `@hp4:TOKEN` (TOKEN is a hex string generated by the machine).
4. **The app stores TOKEN verbatim as `a_val`** at this point — no HMAC or derivation; it is stored as-is.
5. Send: `@HW:01,<4-digit-PIN>` (PIN shown on machine display).
6. Machine responds `@hw:01` on success. The TOKEN issued in step 3 is now permanently activated for this device.
7. All future connections use `@HP:b_val,hex_d_val,TOKEN` → machine responds `@hp4` (recognised).

## 5. Sessions, Remote Screen & Keyboard Locking

### Remote Screen (`@TS:01` / `@TS:00`) — optional, and it LOCKS the keypad

```
@TS:01   ← start Remote Screen (machine acks @ts:01)
... commands ...
@TS:00   ← stop  (machine acks @ts:00)
```

Remote Screen mirrors the machine's display to the client and makes the machine **push
unsolicited status frames** over TCP. **It is NOT required to brew or to read
statistics** — contrary to an earlier assumption in this document:

- `@TP` (brew) starts a product **without** `@TS:01`. The machine brews **autonomously**
  (it keeps going even if the TCP connection is closed) and reports progress over UDP
  (§1.2). The official app issues product/cancel commands as standalone queued commands.
- `@TR:32`, `@TG:C0`, `@TG:43` (statistics) are issued as **standalone commands**, each
  with its own response matcher (`@tg:C0[0-9a-fA-F]{6}` etc.) — no `@TS:01` wrapper.

**While Remote Screen is active the machine LOCKS its physical keypad.** This shows up as
alert **bit 39 "Locked keys"** in the status word — and the machine displays *nothing* on
its own screen to indicate it. If a `@TS:01` session ends uncleanly (connection RST, app
killed, no `@TS:00`), the lock can **stick until the machine is power-cycled**. This is
the "machine is frozen, no buttons work" symptom — it is **not a CPU hang**, it is a
stuck remote-control lock. (The `@TM:50` status subscription behaves the same way and can
hang the read waiting for a push that never comes.)

**Recommendation: never open Remote Screen.** Read live state over UDP (which never locks
the keypad) and send commands as short standalone TCP bursts. Our client opens **no**
`@TS:01`/`@TM:50` session anywhere.

### The single TCP session and the shared token

The machine accepts **one TCP connection at a time**. And every device paired with the
same setup PIN receives the **same auth token** — the token is **per-machine/PIN, not
per-device** (the device-name field in `@HP` is only a cosmetic label). So the machine
**cannot tell two phones apart**; they authenticate as the same logical client.
Consequences:

- **Concurrent/overlapping TCP use is unsupportable by design** — two clients cannot hold
  the one session at once. Keep each TCP burst short so collisions are rare; a single
  retry after ~1–2 s resolves the occasional connection-reset.
- The machine appears to **buffer outgoing status frames per token, not per socket**: a
  session that dies without being fully drained can have its queued frames
  **re-delivered to the next connection** that authenticates with that token — seen as a
  stale `@tf:00` read as instant "done", or a request/response desync. **Subscribing to
  status over TCP (`@TS:01` / `@TM:50`) is what creates this backlog**; not subscribing
  (UDP for status) avoids it entirely.

Because status/progress rides connectionless UDP, **multiple phones can watch — and brew
on — the machine** without contending for the session; only the brief command bursts
serialize on TCP, and they are short enough that conflicts are rare.

### Things that destabilise the firmware (do NOT do)

Tested and confirmed to wedge or hang this hardware — keep TCP minimal:

- A TCP **keepalive** of any kind (`@TG:C0` poll mid-brew stalled the brew; a 1-byte
  TCP urgent/OOB segment hung the machine hard until power-cycle).
- **Drain-on-close / flush-on-open** loops (extra reads around a connection) hung *every*
  operation, including status.
- Holding a TCP connection open and streaming status over it (`@TS:01`/`@TM:50`).

The safe pattern is strictly: open → auth → send command(s) → close, with all live state
on UDP.

---

## 6. Commands Reference

All commands are ASCII. Machine responses echo the command in lowercase (e.g. `@TG:C0` → `@tg:C0...`).

### Maintenance status — `@TG:C0`

Response: `@tg:C0XXYYZZ` — exactly 6 hex chars (3 bytes).

Each byte is a **percentage toward needing service** (0 = just serviced, 100 = service due). Value `0xFF` means the maintenance type is not applicable to this machine.

| Byte offset | Maintenance type |
|---|---|
| 0–1 | Cleaning |
| 2–3 | Filter change |
| 4–5 | Descaling |

Field order is positional (see §9 `maintenanceStatusFields`).

### Maintenance counters — `@TG:43`

Response: `@tg:43` + 24 hex chars (6 × 2-byte big-endian counters).

| Offset (hex chars) | Counter |
|---|---|
| 0–3 | Cleaning cycles |
| 4–7 | Filter change cycles |
| 8–11 | Descaling cycles |
| 12–15 | Cappu rinse cycles |
| 16–19 | Coffee rinse cycles |
| 20–23 | Cappu clean cycles |

Counts represent cycles since last servicing. Field order is positional (see §9
`maintenanceCounterFields`). Not all 6 counters are present on all machines.

### Product counters — `@TR:32,P`

Response: `@tr:32,P,<16 hex chars>` — one **page** of 4 product counters (2 bytes each).

The 16 hex chars encode counters for product codes `4P`, `4P+1`, `4P+2`, `4P+3`:

```
offset within hex data = (product_code % 4) * 4
page P                 = product_code // 4
```

To read the counter for all products, request pages 0–15 (16 requests), concatenate all 16 data fields (256 chars total), then extract:

```python
counter = int(combined[code * 4 : code * 4 + 4], 16)
# 0xFFFF means slot not used
```

Page 0 (`@TR:32,00`) offset 0–3 holds the **total** across all products (code 0x00).

### Machine state — `@TM:50` (prefer UDP)

Returns a bitmask of active alert/status flags as a hex string (the 7-byte / 56-bit
alert word). Each bit is named in the machine catalog's `alertBits` map (see §9).

> **Don't use `@TM:50` as a TCP subscription.** It makes the machine push frames until
> disconnect, locks the keypad, and hangs the reader when no push arrives (§5). The
> **same alert word is in every UDP idle reply** (§1.2) — read it there instead. The bit
> decoding is identical. Bit numbering is **per byte, MSB-first**: bit `N` lives in
> `byte[N/8]` at mask `0x80 >> (N % 8)`. Reference: `MachineStateDecoder.kt`.

Common bits (EF1030):

| Bit | Name |
|---|---|
| 0 | Insert tray |
| 1 | Fill water |
| 2 | Empty grounds |
| 3 | Empty tray |
| 10 | No beans |
| 12 | Heating up |
| 13 | Coffee ready |
| 31 | Enjoy product |
| 32 | Filter alert |
| 33 | Descaling alert |
| 34 | Cleaning alert |
| 36 | Energy save |
| 38 | Remote screen active |
| 39 | **Locked keys** (keypad locked by a remote session — see §5) |

### Brew — `@TP:<payload>`

Starts a product. `<payload>` is exactly **32 hex chars** (16 fields × 2 hex chars each).

| Field index | Argument | Meaning |
|---|---|---|
| 0 | — | Product code (see §7) |
| 2 | F3 | Coffee strength (01–0A, 1=mild … 10=strong) |
| 3 | F4 | Water amount: `ml ÷ 5` as hex (e.g. 60 ml → `0C`) |
| 5 | F6 | Milk foam amount (cappuccino/macchiato only) |
| 6 | F7 | Temperature: `00`=Low, `01`=Normal, `02`=High |
| 8 | F9 | Always `01` |
| 9 | F10 | Bypass water: `ml ÷ 5` (Barista drinks only) |
| 15 | F16 | Grinder override: `04`=use grinder, `00`=default |
| all others | — | `00` |

Example — Espresso, 60 ml, strength 8, temp High:
```
@TP:0200080C000002000100000000000000
     ^^  product code 0x02 = Espresso
       ^^strength 0x08
         ^^water 0x0C = 60÷5=12
               ^^temp 0x02 = High
                   ^^always 01
```

**Progress is observed over UDP, not TCP.** `@TP` needs no `@TS:01` and no reply-reading:
fire it as a one-shot command (you may close TCP immediately afterward — the machine
keeps brewing) and watch progress via the UDP poll (§1.2). While the product runs, the
UDP reply's byte-109 bit 0 is `0` and its payload is a `@TV` brew-state frame; when the
product finishes, the reply goes idle (`@TF`). Decode the `@TV` payload as in the table
below.

`@TV` / `@tv:` brew-state payload (16 bytes; see `BrewProgressDecoder.kt`):

| Byte | Meaning |
|---|---|
| 0 | brew state code (grinding, brewing, dispensing, …) |
| 4 | water dispensed so far ÷ 5 (ml) |
| 5 | target water ÷ 5 (ml) |
| 14 | progress percent (0–100) |

Our client: `BrewViewModel.start` (TCP `@TP` burst) + `awaitCompletion` (UDP progress poll).

### Other useful commands

| Command | Description |
|---|---|
| `@TG:FF` | Cancel current product step |
| `@TG:7E` | Cancel quality assistant step |
| `@TG:01` | Advance to next product step |
| `@TF:02` | Restart machine |
| `@HO:<n>` | Set output level (0–4) |

---

## 7. Product Codes (EF1030 / Jura E6)

| Code (hex) | Name | P_Kind | Milk |
|---|---|---|---|
| 02 | Espresso | C | — |
| 03 | Coffee | C | — |
| 04 | Cappuccino | CM | yes |
| 06 | Espresso Macchiato | CM | yes |
| 08 | Milk Foam | M | yes |
| 0D | Hot Water | W | — |
| 28 | Caffé Barista | C | bypass |
| 29 | Barista Lungo | C | bypass |
| 31 | 2 Espressi | C | — |
| 36 | 2 Coffee | C | — |

**Default parameters per product** (EF1030):

| Product | Strength default | Water default (ml) | Water min–max | Temp default |
|---|---|---|---|---|
| Espresso | 8 | 45 | 15–80 | High |
| Coffee | 5 | 100 | 25–240 | Normal |
| Cappuccino | 8 | 60 | 25–240 | Normal |
| Espresso Macchiato | 8 | 25 | 15–80 | High |
| Hot Water | — | 220 | 25–300 | Normal |

All water amounts must be multiples of 5 ml.

---

## 8. Machine Identification

### Article number → model ID

The **article number** (bytes 68–69, big-endian uint16) in the UDP broadcast beacon
identifies the machine model. Examples:

```
15441 → EF1030  (E6 Smart Connect)
15802 → EF1030  (E6 Espresso Collection)
```

Connection type `13` in the lookup table = WiFi Smart Connect v2.

### Model ID → catalog

Once the model ID is known, Juras loads the matching JSON catalog file:

```
protocol/src/commonMain/resources/catalogs/<modelId>.json
```

Example: article number `15441` → model `EF1030` →
`protocol/src/commonMain/resources/catalogs/EF1030.json`.

Loading: `MachineCatalog.forModel(modelId)` in `MachineCatalog.kt`.

---

## 9. Catalog JSON Format

Juras defines all machine-specific data in
`protocol/src/commonMain/resources/catalogs/<modelId>.json` (one file per model). Parsing:
`CatalogJson.kt` → `MachineCatalog`.

### Top-level fields

| Field | Type | Description |
|---|---|---|
| `modelId` | string | Model identifier (e.g. `"EF1030"`) |
| `products` | array | Product definitions (see below) |
| `alertBits` | object | Bit index → label, for idle UDP payloads (`@TF:`) |
| `brewStateNames` | object | State byte → label, for running UDP payloads (`@TV:`) |
| `dispensingStates` | array | State bytes that indicate active dispensing |
| `maintenanceStatusFields` | array | Field names for `@TG:C0` response (positional, 1 byte each) |
| `maintenanceCounterFields` | array | Field names for `@TG:43` response (positional, 2 bytes each) |

Map keys in `alertBits`, `brewStateNames`, and `dispensingStates` accept decimal (`"33"`)
or `"0x"`-prefixed hex (`"0x21"`).

### Product object

```json
{
  "code": 2,
  "name": "Espresso",
  "kind": "COFFEE",
  "strength": { "default": 8, "min": 1, "max": 10, "step": 1 },
  "water":    { "default": 45, "min": 15, "max": 80,  "step": 5 },
  "defaultTemperature": "HIGH"
}
```

| Field | Values | Maps to `@TP:` |
|---|---|---|
| `code` | int (decimal) | byte 0 (product code) |
| `kind` | `COFFEE` `MILK` `COFFEE_MILK` `WATER` | — |
| `strength` | range or absent | F3 (byte 2) |
| `water` | range or absent | F4 (byte 3, ml ÷ 5) |
| `milk` | range or absent | F6 (byte 5) |
| `bypass` | range or absent | F10 (byte 9, ml ÷ 5) — Barista drinks only |
| `defaultTemperature` | `LOW` `NORMAL` `HIGH` or absent | F7 (byte 6: 0/1/2) |

`maintenanceStatusFields` maps positionally to `@TG:C0` response bytes (`0xFF` = not
applicable for this machine). `maintenanceCounterFields` maps positionally to `@TG:43`
big-endian 2-byte counters.

---

## 10. Adding Support for Other Models

The WiFi protocol (cipher, framing, auth, command set) is **identical across all supported
machines**. What varies per model:

| What varies | Where defined |
|---|---|
| Product catalogue (codes, kinds, ranges) | `products` array in the model's JSON catalog |
| Maintenance counter fields | `maintenanceCounterFields` in JSON |
| Maintenance status fields | `maintenanceStatusFields` in JSON |
| Alert bitmask meanings | `alertBits` map in JSON |
| Brew state names | `brewStateNames` map in JSON |

### Adding a new model

1. Obtain the article number (from the UDP beacon, §1.3, or the machine's settings screen).
2. Map it to a model ID (e.g. `15441` → `EF1030`).
3. Create `protocol/src/commonMain/resources/catalogs/<modelId>.json` following the
   format in §9, using the EF1030 file as a template.
4. The catalog is picked up automatically by `MachineCatalog.forModel(modelId)`.

### Key Juras source files

| File | Purpose |
|---|---|
| `protocol/.../CatalogJson.kt` | Deserialises JSON catalog → `MachineCatalog` |
| `protocol/.../MachineCatalog.kt` | In-memory model; `forModel()` loads and caches catalogs |
| `protocol/.../product/Product.kt` | Per-product definition (ranges, kind, code) |
| `protocol/.../client/TpPayload.kt` | Builds the 32-char `@TP:` payload |
| `protocol/.../client/MachineStateDecoder.kt` | Decodes `alertBits` from idle UDP payload |
| `protocol/.../client/BrewProgressDecoder.kt` | Decodes `brewStateNames` from running UDP payload |
| `protocol/.../client/JuraUdpStatusClient.kt` | UDP poll loop (§1.2) |

---

## 11. Known Limitations / Open Questions

- **Keep TCP minimal — the firmware is fragile.** No keepalives, no Remote Screen
  (`@TS:01`), no `@TM:50` subscription, no extra reads around close. Use UDP for all live
  state. See §5 for the specific patterns that wedge the machine.
- **Single TCP session + shared token**: one connection at a time, and all devices on the
  same PIN share one token, so the machine can't distinguish clients (§4, §5). Concurrent
  TCP is unsupportable; UDP status polling, being connectionless, is multi-client safe.
- **Per-token frame backlog**: a TCP session that dies mid-stream can have its queued
  status frames re-delivered to the next connection (§5). Avoided by not subscribing to
  status over TCP.
- **`@TM:50` status word width**: the number of alert bits varies by model. The EF1030 catalog defines bits 0–40+; models without a milk system will have fewer bits.
- **Counter byte order**: `@TG:43` counter values are ASCII hex, big-endian. Confirmed behaviorally — the machine sends hex text, not raw little-endian bytes.
- **UDP poll IP field**: the machine's own IPv4 is embedded in the poll packet (§1.2). It
  appears to be ignored (the reply routes by UDP source address), but we send it matching
  observed behavior; whether any firmware validates it is unconfirmed.
- **`@TS:01`-free statistics**: confirmed that `@TR`/`@TG` reads work with no Remote Screen
  session. Whether any *other* command groups genuinely require `@TS:01` is untested — we
  never open one.
