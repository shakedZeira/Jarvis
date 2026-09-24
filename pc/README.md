# Jarvis — PC-side setup

Starts a persistent, LAN/internet-accessible **opencode server** that the Android app (phone) connects to directly. No cloud, true peer-to-peer.

## 1. Start the server

```powershell
.\pc\serve.ps1
```

- On first run it generates a random password, saves it to `pc\.env`, and prints it.
- Auth username is always `opencode`; the password is what's in `pc\.env`.
- The server listens on `0.0.0.0:4096` with mDNS enabled. Printout shows your local IP.
- Never commit `pc\.env` (already gitignored).

### Work from your computer as usual

Attach your terminals to the same server so every session lives in one place:

```powershell
opencode attach http://localhost:4096
```

or point a new instance at it:

```powershell
opencode --port 4096 --hostname 127.0.0.1
```

## 2. Open the firewall (one-time, admin)

```powershell
.\pc\firewall.ps1
```

Elevates itself and adds an inbound allow rule `opencode-jarvis` for TCP `4096` on Private/Domain profiles (idempotent). Over the internet it will likely hop through a router too.

## 3. Connect from the phone

- **LAN:** `http://opencode.local:4096` (mDNS) or `http://<pc-ip>:4096`
- **Internet:** `https://<your-host>:port` — see below.

Health check from any device:

```powershell
curl -u opencode:<password> http://<pc-ip>:4096/global/health
```

## 4. Rules

- Firewall rule is only needed for inbound connections; within a home LAN just run `firewall.ps1` once.
- **Never expose plain HTTP to the public internet.** If the phone must reach the PC remotely, put TLS in front: use a VPN/tunnel (Tailscale, WireGuard), a reverse proxy with HTTPS, or a tunnel service. Basic auth protects against casual scans but is not encryption.

## 5. Troubleshooting

- `Invoke-WebRequest http://localhost:4096/global/health` from the PC itself → must return `healthy:true` (with auth).
- Phone can't connect but PC can → firewall rule, or phone/PC on different subnets.
- Password reset → delete `pc\.env` and re-run `serve.ps1`; copy the new password to the phone.