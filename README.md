# F7 Download Manager

A clean-room Android download manager: multi-connection segmented HTTP downloads, resumable
byte ranges, a foreground service with live progress, a built-in browser that catches download
links, a verification-aware library, and a light-first premium interface.

- **Package:** `com.f7.downloadmanager`
- **Version:** 1.0.0 (versionCode 1)
- **Min SDK:** 24 · **Target SDK:** 36 · **Compile SDK:** 36
- **Licence:** GPL-3.0-or-later (full text in `LICENSE`)

## Relationship to Mister Download Manager

[anefzaoui/misterdownloadmanager](https://github.com/anefzaoui/misterdownloadmanager) is a
GPL-3.0 desktop download manager written in Rust, without an Android target. This project is an
**original Kotlin implementation** of that feature idea for Android. No Rust source, binaries,
icons, artwork or text from that repository are included. It is released under GPL-3.0 so the
lineage stays honest, and the About screen credits the inspiration.

## Features

| Area | Implementation |
| --- | --- |
| Segmented download | HTTP range requests across up to 16 connections, each writing at its own file offset. A long range is re-split when other connections are idle. |
| Resume | Each segment offset is persisted to SQLite (~900 ms and on pause). Before resuming, the engine revalidates `ETag`, `Last-Modified` and total size; a changed file restarts instead of corrupting. |
| Background | `dataSync` foreground service, determinate notification with Pause all / Resume all, partial wake lock only while work is active, self-stop when the queue empties. |
| Queue | 1–4 simultaneous downloads (FIFO), retry with backoff, automatic recovery after process death, priority bump. |
| Speed limit | Global token bucket shared by all connections; unlimited by default. |
| HLS | Non-DRM HLS: variant selection, AES-128 decryption, `EXT-X-MAP` fMP4 init, merged to `.mp4` (fMP4) or `.ts` (transport stream). DRM playlists are refused with a clear message. |
| Browser | WebView with strict TLS (certificate errors are never bypassed), JS + DOM storage only, no local file access, popups disabled, Safe Browsing, download interception with cookies and user agent, share and clipboard capture. |
| Library | SHA-256 verification, duplicate detection, open, share, delete, storage summary. |
| UI | Jetpack Compose, six accent presets, light/dark/system modes, Plus Jakarta Sans, large type scale, compact screens that avoid unnecessary scrolling. English only. |

## Visual system

- **Motion** — a small token set (120/200/320 ms, springs at 380 and 220 stiffness) drives card
  press feedback, filter pills, the navigation indicator, progress fills and per-segment ticks.
- **Speed console** — live speed is interpolated between engine ticks (900 ms linear) and set in
  tabular figures, so numbers never jitter once per second. Real segment ticks from the engine sit
  under the readout.
- **Rows** — swipe right to pause/resume, swipe left to remove (confirmation dialog). Progress and
  segment bars animate from real values only; nothing is simulated.
- **Ambient shader** — an AGSL `RuntimeShader` glow, API 33+, enabled only on the Downloads tab,
  paused when the system animation scale is zero, disabled in battery saver, and safe to fail
  (any compile error falls back to the static gradient).
- **Blender** — `design/render_icon.py` renders the launcher icon concept in EEVEE Next using the
  app's own Plus Jakarta ExtraBold font; `tools/blender_bridge.py` is the socket client. Blender
  output is offline artwork only — never runtime 3D, and no decorative hero art inside the app.
- **Typography** — Plus Jakarta Sans, tabular numerals for all live data, 13sp floor, 16–17sp body.
- **Dark mode** — accents are lifted toward white by 32% instead of 62%, so jade stays jade instead
  of turning pale mint.

Visual changes are compiled and packaged, but **not yet visually verified on a device** (the test
phone was in use). Screenshots should be taken before calling the redesign signed off.

## Transfers & settings

- Real multi-connection downloads with work stealing, ETag/If-Range validation and per-segment resume.
- Constraints: Wi-Fi only, charging only, automatic pause when the network drops and resume when it
  returns, plus a free-space check before a transfer starts.
- Auto retry with exponential backoff (attempts: off / 3 / 5 / 10).
- Search, sort (newest, oldest, name, size), five status filters (All, Active, Paused, Done, Failed)
  and a selection mode with batch pause / resume / remove.
- Row metadata: source host, size, live ETA and retry count; the detail sheet adds segments, checksum
  and saved location.
- HLS: AES-128 decryption, real byte progress plus segment counters, safe resume.
- Settings: connections per download (1–16), simultaneous downloads (1–4), global speed limit,
  Wi-Fi only, charging only, auto retry + attempts, notifications, live progress notifications,
  resume on launch, ambient glow, confirm removal, download folder, schedule, partial-file cleanup,
  free space, theme presets and light/dark/system mode.

## Storage

No broad storage permission is requested.

1. User-selected folder through the Storage Access Framework (persisted grant) when configured.
2. Otherwise MediaStore `Download/F7` on Android 10+.
3. Older releases fall back to app-specific external storage.

Downloads are staged in app-private external storage because random-offset writes are not
reliably seekable through every SAF provider; the finished file is streamed to the destination
and the target name is de-duplicated (`name (1).ext`).

## Project layout

```
app/src/main/java/com/f7/downloadmanager/
  core/        models, categories, formatting
  data/        SQLite store, settings, SAF/MediaStore storage
  engine/      HTTP probe, segmented downloader, HLS downloader, queue engine, limiter
  service/     foreground service, notifications, alarms/receivers
  ui/          theme, icon set, components, downloads/browser/library/settings screens
  util/        link extraction, SHA-256
```

## Build

JDK 17 and Android SDK 36 are required. The Gradle wrapper is included.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

Artifacts:

| File | Size | Notes |
| --- | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | ~10 MB | Debug build with tooling |
| `app/build/outputs/apk/release/app-release.apk` | ~1.5 MB | R8-minified, signed |
| `app/build/outputs/bundle/release/app-release.aab` | ~3.4 MB | Play bundle, signed |

`keystore.properties` points at `keystore/f7-upload.jks` (a sample upload key created for this
build). **Replace it with your own Play upload key before publishing.**

## Verification performed

On a real Android 13 arm64 device, using the debug build:

- A 10 MB HTTPS file was downloaded using **eight parallel ranges**; SQLite recorded
  `0,1310719,1310720; 1310720,2621439,1310720; …` — eight 1,310,720-byte segments, all complete.
- The saved file was **10,485,760 bytes** and its SHA-256
  `e5b844cc57f57094ea4585e235f36c78c1cd222262bb89d53c94dcb4d6b3e55d` matched a reference
  download of the same URL byte for byte.
- Foreground notification `7101` was active with progress and two actions during the transfer.
- Force-stopping the process mid-transfer and reopening produced a completed file with the same
  matching hash. All segment offsets survived in the database, which is exactly the state the
  resume path reads.
- A 100 MB transfer showed progress advancing across several connections in SQLite.

Still to verify on hardware (was not completed when the phone was handed back):

- A full pause → wait → resume → force-stop → resume walkthrough. The pause tap in the test run
  landed on another screen, so pause/resume is implemented and progress was observed persisting,
  but the complete walkthrough is not yet ticked off.
- The R8-minified release APK/AAB was built and signature-verified, but not yet run on a device.
- HLS against a real playlist (parsing, AES-128 and merge logic are implemented and compile, but
  no device run yet).

## Honest limitations

- **Torrent / magnet is not implemented.** Magnet links are handed to an installed torrent app.
  A torrent engine needs a native library and a much larger review surface; no fake button was
  added.
- **HLS is non-DRM only**; SAMPLE-AES / Widevine content is refused.
- **HLS output is merged, not remuxed**: fMP4 → `.mp4`, transport stream → `.ts`.
- **Connections are a global setting**, not per download.
- **English only.**
- No "faster than IDM" claim is made. Throughput depends on the server and the network.

## Google Play checklist

- `targetSdk 36`, 64-bit (no custom native ABIs; only the Compose path library), no ads SDK, no
  analytics, no account, no tracking.
- Foreground service declares `dataSync` and is only started for active transfers.
- No `MANAGE_EXTERNAL_STORAGE`, no `READ/WRITE_EXTERNAL_STORAGE`.
- Data safety: history stays in local SQLite; nothing is transmitted except the user's own
  download requests.
- Replace the sample upload key, add store listing assets, complete the data safety form, and
  review the browser against Play's user-generated-content and deceptive-behaviour policies.
