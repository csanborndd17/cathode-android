# Cathode

Cathode is a local-first Android music player by 12Pts with an artwork-reactive cyan-on-black visual system. It plays music stored on the device; it does not stream a hosted Cathode catalog.

## Current functionality

- Android MediaStore indexing and Monochrome-folder detection
- FLAC, MP3, M4A, OGG, Opus, WAV, and other device-supported formats
- Embedded artwork plus non-destructive metadata, tag, and custom-artwork overrides
- Songs, albums, artists, folders, favorites, and local playlists
- Search across metadata and custom tags
- Media3 background playback, queue editing, waveform scrubbing, shuffle, and repeat
- Notification, lock-screen, Bluetooth, headset, and audio-focus integration
- Audio equalizer where the active Android route exposes an effects session
- Persistent mini-player, artwork-derived backgrounds, profile customization, and Transmission Log
- HTTPS-only Discover hub with Android DownloadManager handoff and offline detection
- Authorized Spotify and public YouTube playlist import with lazy large-playlist rendering and guided source-search queues
- Android 8.0+ (`minSdk 26`), targeting API 36

> Download only media you own or are legally authorized to use. Cathode contains no scraper, service API, DRM circumvention, or copyright-bypass system.

## Build

1. Open the repository in a current Android Studio release.
2. Install Android SDK 36 and sync Gradle.
3. Run the `app` configuration on a device.
4. Grant audio permission when prompted.

The repository intentionally excludes `local.properties`. CI uses `bash gradlew testDebugUnitTest assembleDebug --stacktrace` and uploads `app-debug.apk` as `cathode-debug-apk`.

## Discover boundary

Discover provides isolated HTTPS WebViews for explicitly listed sources. Normal HTTPS downloads are handed to Android DownloadManager. Browser-generated `blob:` payloads cannot be passed safely to DownloadManager without a privileged JavaScript bridge, so Cathode blocks that path instead of extracting site data.

Source availability and file formats vary. Internet Archive and Wikimedia contain FLAC only for some items. Bandcamp supports FLAC for eligible purchases or artist-enabled downloads. Monochrome availability depends on its external instances.

## Android and desktop parity

The planned desktop edition will use a sibling repository, `csanborndd17/cathode-desktop`. This keeps desktop packaging and native filesystem/media-session behavior separate from Android while preserving product parity through:

- [Platform parity contract](docs/PARITY.md)
- [Machine-readable feature ledger](docs/parity.json)
- [Change-record template](docs/CHANGE_TEMPLATE.md)
- [Desktop foundation](desktop/README.md)

Every user-facing update should record what changed, why, how Android implements it, and the exact desktop equivalent.

## Library migration

Cathode accepts authorized Spotify and public YouTube playlist links, collects track and artist names through official provider APIs, and prepares a guided search queue on the selected Discover source. Private YouTube playlists require a future Google OAuth connection. Cathode does not scrape private playlists, bypass service authentication, or automatically download copyrighted catalogs.
