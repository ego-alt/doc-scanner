# Document Scanner

An Android document scanner. Point the camera at a document and ML Kit
captures it, detects the edges, and flattens it into a clean page — then share
it as an image or a PDF.

## How it works

Capture and editing are powered by **Google ML Kit's Document Scanner**
(delivered through Google Play services): live edge detection, auto-capture,
multi-page capture, per-page crop/rotate, cleanup filters, and gallery import.
ML Kit hands back the flattened pages as JPEGs **and** as a ready-made PDF; the
app wraps that with a home screen, a result/preview screen, and sharing.

Flow: **Home → ML Kit scan → Result (share as image / PDF, or scan again)**

- A single-page scan can be shared as an **image** or a **PDF**.
- A multi-page scan is shared as a **PDF** (one file, all pages).
- Turn on **Extract Dates (Beta)** in Settings to read the document's date on-device automatically after each scan. Tap the date or time on the result screen to correct it; the export filename follows.
- Turn on **Connect to calendar (Beta)** in Settings to upload a scan to your self-hosted home calendar, attached to its date. The option only appears when the server is reachable.

Each scan is one ML Kit session, and the app keeps only the latest scan — to
change a page, scan again.

## Tech stack

- Kotlin 2.1 · Jetpack Compose (Material 3)
- ML Kit Document Scanner (`com.google.android.gms:play-services-mlkit-document-scanner`)
- ML Kit on-device text recognition + entity extraction (date/time)
- OkHttp (optional calendar upload)
- minSdk 26 · compileSdk 35 · AGP 8.7.3 / Gradle 8.11.1

## Build & run

1. Open the project in **Android Studio** (it creates `local.properties` and
   syncs Gradle automatically).
2. Use a device or emulator **with Google Play services** — the scanner module
   is delivered through Play services, so the "Google Play" emulator images
   (or any normal phone) work.
3. Run the **app** configuration.

> The ML Kit scanner downloads a small module from Play services on first use
> (needs network). It can be flaky on emulators — especially hanging on the
> Play services screen on relaunch — so prefer a **physical device** for
> reliable testing.

## Project structure

```
com.scanner.app
├── MainActivity.kt           # Compose entry point
├── ui/
│   ├── ScannerApp.kt         # Top-level: Home <-> Result
│   ├── ScanViewModel.kt      # Latest scan result, date-extraction state, and settings
│   ├── ScanScreen.kt         # Home + Result (preview, share, upload) + Settings entry
│   ├── SettingsScreen.kt     # Settings: Extract Dates + Connect to calendar toggles
│   ├── PageImporter.kt       # Copies ML Kit's JPEG/PDF output into shareable cache; preview decode
│   ├── ImageSaver.kt         # Shares a file (image or PDF) via FileProvider
│   └── theme/                # Compose theme
├── ocr/
│   ├── OcrService.kt         # Extraction interface + ExtractedFields model
│   ├── OnDeviceOcrService.kt # On-device ML Kit text recognition + date extraction
│   └── RemoteOcrService.kt   # Stub for a future server backend (richer fields)
├── calendar/
│   └── CalendarClient.kt     # Health probe + multipart upload to the home calendar
├── update/
│   └── UpdateClient.kt       # Asks the home stack for a newer build, downloads it, opens the installer
└── HomeServer.kt             # URLs on the home stack, from the server address in Settings
```

## Field extraction

Off by default — turn on **Extract Dates (Beta)** in Settings. When enabled, each
scan automatically runs `OcrService.extract(bitmap)`, which pulls structured
fields (`ExtractedFields`) from the first page. The default `OnDeviceOcrService`
runs fully on-device with ML Kit — text recognition (bundled, offline) plus
entity extraction — and currently fills in the **date** and **time**. Both are
editable on the result screen, and the shared file is named
`YYYY-MM-DD-HHMM.<ext>` from them (falling back to the scan time when nothing is
detected). The entity-extraction model downloads once on first use (needs network
that one time); text recognition is bundled and works offline.

For richer fields (vendor, totals, line items), implement `RemoteOcrService`:
upload the page to your endpoint and map the response into `ExtractedFields`. It
returns `OcrResult.NotConfigured` until wired up.

## Connect to calendar (Beta)

Optionally upload a scan to a self-hosted [home calendar](../calendar) (served
behind the [dashboard](../dashboard) nginx), attached to the scan's date. Off by
default; enable it in Settings and provide:

- **Server URL** — your home stack's Tailscale name, e.g.
  `https://pi.your-tailnet.ts.net`. The phone must be on the same tailnet
  (Tailscale app installed and logged in) to resolve and reach it.
- **API token** — generate one in the dashboard's Settings → API tokens, then
  paste it in. Sent as `Authorization: Bearer <token>`; the dashboard authorizes
  it and the calendar attaches the file to that date's entry.

The result screen shows **Upload to calendar** only when `GET /calendar/healthz`
succeeds. The combined PDF is uploaded (single-page falls back to the JPEG) under
the `YYYY-MM-DD-HHMM` name, on the (possibly corrected) extracted date.

Tailscale provisions a real, publicly-trusted TLS cert for the `*.ts.net` name,
so the app needs no bundled CA or network-security-config — just the `INTERNET`
permission and tailnet connectivity to the host.

## Releases and updates

The app installs and updates from the home stack, not a store: the [dashboard](../dashboard)
lists it under **Services → Phone apps** (from `homestack.toml`).

- **Every push to `main` is a release.** CI (`.github/workflows/ci.yml`) builds a signed APK and
  publishes it on GitHub as `v<versionCode>`, named `1.0.<versionCode>`, with the commit messages
  since the last release as its notes. The versionCode is the commit count, so it only goes up.
- **Install** from Services on the phone. Android asks once to allow installs from the browser.
- **Updates:** with the server URL and API token set in Settings, the app asks the dashboard on
  launch (`GET /phone-apps/doc-scanner/latest`), shows **Update available** on the home screen,
  downloads the APK from the Pi and opens Android's installer. The first time, Android asks to allow
  installs from the scanner. Asking also tells the dashboard which version is installed, so Services
  shows Update or Up to date.

### Signing

Android only accepts an update signed with the same key as the installed app, so every release is
signed with one key:

- The key is `~/.android-signing/doc-scanner-release.p12` on the Mac, with its password in
  `doc-scanner-release.password` next to it, and a copy in `~/keys/` on the Pi (in the encrypted
  off-site backups). **Don't lose it:** without it, the app can only be reinstalled from scratch.
- CI reads it from two repository secrets: `SIGNING_KEYSTORE` (the `.p12`, base64) and
  `SIGNING_PASSWORD`. Without them CI still builds, but publishes nothing.
- A local build without the key is signed with the debug key; it installs, but won't update a
  released copy (or be updated by one).

