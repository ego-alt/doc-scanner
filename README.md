# Scanner

An Android receipt/document scanner. Point the camera at a receipt and it
captures, detects the edges, and flattens it into a clean image — ready to
share, or (later) to send to a server for OCR / field extraction.

## How it works

The scan flow is powered by **Google ML Kit's Document Scanner** (delivered
through Google Play services): live edge detection, auto-capture, crop/rotate,
and cleanup filters, returning a flattened JPEG. The app wraps that with its
own home screen, result screen, sharing, and an extension point for
server-side OCR.

Flow: **Home → ML Kit scan → Result (share / scan again)**

## Tech stack

- Kotlin 2.1 · Jetpack Compose (Material 3)
- ML Kit Document Scanner (`com.google.android.gms:play-services-mlkit-document-scanner`)
- minSdk 26 · compileSdk 35 · AGP 8.7 / Gradle 8.11

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
│   ├── ScanViewModel.kt      # Holds the current scan
│   ├── HomeScreen.kt         # Launches the ML Kit scanner
│   ├── ResultScreen.kt       # Shows the scan; share / scan again
│   ├── ImageSaver.kt         # Share via FileProvider
│   └── theme/                # Compose theme
└── ocr/
    ├── OcrService.kt         # Interface for field extraction (the future server step)
    └── RemoteOcrService.kt   # Stub — wire your endpoint here
```

## Future: server-side OCR

The on-device flow produces a flattened bitmap. To extract fields (merchant,
date, total, …), implement `OcrService.extract(bitmap)` in `RemoteOcrService`:
upload the image to your endpoint and map the response into `ReceiptFields`.
Until then it returns `OcrResult.NotConfigured`.
