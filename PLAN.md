# PLAN.md — Jarvis: OpenCode Remote for Android

## 1. Goal

Turn the phone into a remote control + live monitor for the user's opencode sessions running on the PC/laptop.

1. Peer-to-peer connection to the PC — same Wi-Fi **and** over the internet.
2. See all currently running opencode sessions.
3. Notify when a model finishes work (plus errors / permission asks).
4. Show what the model is doing right now (live session UI).
5. Send prompts back to the model from the phone (chat).

## 2. Foundational research (done, verified against opencode v1.18.32)

opencode ships a headless HTTP server (`opencode serve`) — **no custom backend bridge is needed**:

- `opencode serve --port <port> --hostname 0.0.0.0 [--mdns]`; app hangs off it directly (true P2P).
- OpenAPI spec at `/doc`; **SSE realtime event bus** at `/event`.
- Auth: HTTP basic auth via `OPENCODE_SERVER_PASSWORD` / `OPENCODE_SERVER_USERNAME`.
- mDNS discovery of the PC as `opencode.local`.
- Key REST: `GET /global/health`, `GET /session`, `GET /session/status`, `GET /session/:id/message?limit=`, `POST /session/:id/message`, `POST /session/:id/prompt_async` (fire-and-forget), `POST /session/:id/abort`, `POST /session/:id/permissions/:permissionID`.
- Key SSE events: `server.connected`, `session.created`, `session.updated`, `session.idle`, `session.error`, `session.status` (busy→idle), `message.updated`, `message.part.updated`, `message.part.delta` (streaming), `permission.updated`.
- Notifications are delivered locally by an Android **foreground service that keeps the SSE stream alive** → no Firebase/FCM.

Reference app **NBA Rumble** sets the Android conventions: Kotlin 1.9.24, AGP 8.9.1, Gradle 8.11.1, Java 17, minSdk 26 / compileSdk+targetSdk 35, Jetpack Compose + Material3 + Navigation-Compose, single activity, manual DI (`Application` + `AppContainer` + `vmFactory`), version-catalog Gradle, `data/model` + `data/repo` + `ui/<feature>/` package layout, unit tests in `src/test`.

## 3. Architecture

```
┌─────────────────── PC (Windows) ───────────────────┐
│  opencode serve --hostname 0.0.0.0 --port 4096     │
│   (password-protected, autostarted, firewall-open) │
│   └─ REST + SSE at http://<host>:4096              │
│   └─ terminals attach: opencode attach            │
└──────────────▲─────────────────────────────────────┘
               │  HTTP(basic auth) + SSE     ┌─────────── LAN: mDNS "opencode.local"
               │                             │             / https via VPN/tunnel
┌──────────────┴──── PUMP / INTERNET ────────▼────────────┐
└─────────────── Android app (foreground service) ────────┘
   • Connect screen (mDNS / manual IP / QR)   → credential store
   • Sessions screen (list + live status)     → GET /session, /session/status + SSE
   • Session detail (live transcript/deltas)  → /session/:id/message + SSE
   • Chat composer (send prompt)              → POST /session/:id/prompt_async
   • Notifications (idle/error/permission)    → foreground service holds SSE, fires local notif
```

## 4. Execution team (sub-agents)

Each phase below is run by a dedicated sub-agent with fresh context; a lead agent coordinates, reviews diffs, and runs verifications.

| Agent | Responsibility |
|---|---|
| `pc-setup` | serve.ps1, firewall rule, autostart, PC-side docs |
| `android-scaffolder` | Gradle/Compose project scaffold |
| `android-networking` | `OpenCodeClient` + SSE event stream + serializable models + unit tests |
| `android-connectivity` | mDNS/manual/QR discovery, credential storage, health handshake |
| `android-ui-sessions` | Sessions list screen |
| `android-ui-session` | Session detail live transcript |
| `android-ui-chat` | Prompt composer + streaming reply |
| `android-notifications` | Foreground service + local notifications |
| `android-security` | Auth/TLS hardening for internet exposure |
| `android-testing` | End-to-end + edge-case verification |
| `docs` | README.md + SETUP.md packaging |

## 5. Phases

### Phase 0 — PC-side server foundation — *sub-agent: `pc-setup`*
- `pc/serve.ps1`: reads password from `pc/.env` (or prompts, → Windows Credential Manager), starts `opencode serve --hostname 0.0.0.0 --port 4096 --mdns`.
- `pc/firewall.ps1`: `netsh advfirewall firewall add rule name="opencode" dir=in action=allow protocol=TCP localport=4096`.
- Optional Task Scheduler autostart; optional `pc/pair.ps1` printing a QR (`opencode://user:pass@ip:port`) for phone scanning.
- **Acceptance**: from the phone's Wi-Fi, `Invoke-WebRequest http://<pc-ip>:4096/global/health` returns healthy with auth; fails without password.
- **Verify**: `curl http://localhost:4096/global/health`, `curl -u opencode:<pass> ...`.

### Phase 1 — Android scaffold — *sub-agent: `android-scaffolder`*
- Gradle project under `android/` cloning NBA Rumble conventions (version catalog, Kotlin 1.9.24/AGP 8.9.1, minSdk 26, Compose BOM 2024.06.00, manual DI `AppContainer`, `vmFactory`).
- Dependencies to add: `okhttp`, `okhttp-sse`, `kotlinx-serialization-json` (+ plugin), `coroutines-android`, `lifecycle-*`, `navigation-compose`, `material-icons-extended`, `androidx.security:security-crypto` (or SharedPreferences).
- Package skeleton `com.jarvis.remote` with `data/model`, `data/repo`, `ui/connect`, `ui/home`, `ui/session`, `notify/`; empty Compose shells.
- **Acceptance**: `gradlew assembleDebug` builds; APK installs.

### Phase 2 — Networking layer — *sub-agent: `android-networking`*
- `data/model/`: `Session`, `Message`, `Part` (text/tool/step), `SessionStatus`, `OpenCodeEvent`, `Health`.
- `data/repo/OpenCodeClient.kt`: OkHttp with basic-auth `Interceptor`; `health()`, `sessions()`, `sessionStatus()` (map id→status), `messages(id, limit)`, `sendPrompt(id, text)` via `prompt_async`, `abort(id)`, `respondPermission(id, permissionID, allowed)`.
- `data/sse/EventStream.kt`: okhttp-sse `EventSource` wrapped as `Flow<OpenCodeEvent>` with auto-reconnect + exponential backoff; exposes `onEvent` → JSON parse; special-cases `session.idle`, `session.error`, `message.part.delta/updated`, `permission.updated`.
- Kotlinx-serialization parsers as pure functions.
- **Acceptance**: unit tests for JSON parsing + client against a local mock server engine.
- **Verify**: `gradlew test`.

### Phase 3 — Connect & discovery — *sub-agent: `android-connectivity`*
- `ui/connect/ConnectViewModel`: three inputs — mDNS scan (`android.net.nsd` for `_opencode._tcp`), manual `host:port`, QR scan (zxing) → auto-fill creds.
- Health handshake before saving; credential persistence; connection profile (remember multiple PCs).
- Internet path: URL may be LAN IP, dynamic-DNS, or a VPN-assigned IP; plain-HTTP + non-LAN host triggers an inline security warning (see Phase 8).
- **Acceptance**: app connects to the live Phase-0 server and shows server version.

### Phase 4 — Sessions list UI — *sub-agent: `android-ui-sessions`*
- Home screen: `GET /session` merged with `/session/status`; rows show title, project, status chip (Running / Idle / Error), last-activity time; busy row highlighted; pull-to-refresh; SSE updates list live.
- **Acceptance**: sessions the user starts via `opencode attach` appear within seconds.

### Phase 5 — Session detail (live session UI) — *sub-agent: `android-ui-session`*
- Transcript from `/session/:id/message?limit=` rendered Compose-style: text parts, tool-call parts (name + args summary), tool-result parts (collapsed/expanded), assistant/user bubbles.
- Live mode: subscribes to SSE; streams `message.part.delta` text as it types; header shows "Running: <current tool/step>" and busy/idle/error.
- "Show what the model is doing" = render of parts + current tool activity.
- **Acceptance**: watching a real long task on the PC updates the phone in real time.

### Phase 6 — Chat composer — *sub-agent: `android-ui-chat`*
- Bottom-sheet composer in session detail: send → `POST /session/:id/prompt_async`; reply streams back via SSE into the same transcript; abort button → `POST /session/:id/abort`.
- **Acceptance**: a prompt sent from the phone executes on the PC and its response streams back; PC terminal shows the same session activity.

### Phase 7 — Notifications — *sub-agent: `android-notifications`*
- `notify/RemoteForegroundService`: keeps one SSE connection alive while app active/backgrounded; persistent "Connected to Jarvis" notification.
- Channels: "Work finished" (session.idle), "Error" (session.error), "Needs approval" (permission.updated) with distinct behavior; POST_NOTIFICATIONS runtime permission (Android 13+, manifest permission pattern already known from NBA Rumble).
- Reconnect on network change (ConnectivityManager callback); battery-optimization exemption guide for reliable delivery.
- **Acceptance**: battery out of hand, phone locked → a tap notification fires the moment a model finishes on the PC.

### Phase 8 — Internet exposure hardening — *sub-agent: `android-security`*
- No secrets hardcoded; creds only in encrypted storage.
- Docs + UI guidance: over the internet use a TLS layer (Tailscale/WireGuard VPN, or HTTPS reverse proxy; optional `--cors` only for browser) — plain HTTP implied only within a trusted LAN.
- Handle 401 (re-auth prompt) and TLS errors cleanly.
- **Acceptance**: security review checklist signed off.

### Phase 9 — Integration testing & polish — *sub-agent: `android-testing`*
- End-to-end against the real server: connect, list, live view, chat round-trip, notification firing, network drop → reconnect, wrong password, offline empty states.
- Unit tests: parsers, event mapping, backoff/reconnect logic.
- **Acceptance**: test checklist (below) fully green; app name/icon/branding set.

### Phase 10 — Packaging & docs — *sub-agent: `docs`*
- `README.md` (feature overview + screenshots) and `SETUP.md` mirroring NBA Rumble's style: PC setup (serve.ps1, firewall, internet options), Android build/install, troubleshooting.
- **Acceptance**: a fresh person can go from zero to notified in ~10 min.

## 6. Testing checklist (acceptance for the app)

1. Phone discovers PC via mDNS on LAN; manual IP also works.
2. Sessions started on the PC appear live.
3. Session transcript updates in real time; current tool shown.
4. Prompt sent from phone runs on PC; streaming reply returns.
5. Locked-phone notification fires on session idle, error, and permission ask.
6. Wrong password → clean re-auth screen; network loss → auto-reconnect.
7. Internet connection via VPN/tunnel works; plain-HTTP outside LAN warns.

## 7. Risks & mitigations

- **SSE reconnect semantics / event fidelity**: known opencode issues around `prompt_async` deltas and `attach --dir` events → fall back to polling `/session/status` every ~2s as a safety net; use `POST /session/:id/message` (waits) if streaming proves flaky.
- **Windows firewall / network profile blocks inbound** → firewall script + "Private network" guidance.
- **Basic auth in transit over internet** → mandate TLS layer (VPN/tunnel) for non-LAN, Phase 8.
- **Battery-kill of foreground service** → exemption setup + guide; minimal SSE heartbeat.
- **Server not running** (PC asleep / not launched) → clear "PC offline" state with a Start hint.

## 8. Stretch (post-MVP)

- Approve/deny permission prompts from the phone (`permission.updated` → respond).
- Multi-PC profiles + project-scoped filters.
- Widget showing the single most recent session status.
- Optional Firebase push fallback for deep-background delivery (deviates from P2P, only if reliability demands).