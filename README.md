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

Each scan is one ML Kit session, and the app keeps only the latest scan — to
change a page, scan again.

## Tech stack

- Kotlin 2.1 · Jetpack Compose (Material 3)
- ML Kit Document Scanner (`com.google.android.gms:play-services-mlkit-document-scanner`)
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
│   ├── ScanViewModel.kt      # Holds the latest scan result (+ ScanResult model)
│   ├── ScanScreen.kt         # Home (launch scanner) + Result (preview, share)
│   ├── PageImporter.kt       # Copies ML Kit's JPEG/PDF output into shareable cache; preview decode
│   ├── ImageSaver.kt         # Shares a file (image or PDF) via FileProvider
│   └── theme/                # Compose theme
└── ocr/
    ├── OcrService.kt         # Interface for field extraction (the future server step)
    └── RemoteOcrService.kt   # Stub — wire your endpoint here
```

## Future: server-side OCR

Each scanned page is a flattened JPEG on disk. To extract structured fields
(merchant, date, total, …), implement `OcrService.extract(bitmap)` in
`RemoteOcrService`: decode a page (e.g. via `PageImporter.decodeScaled`), upload
it to your endpoint, and map the response into `ExtractedFields`. Until then it
returns `OcrResult.NotConfigured`.
