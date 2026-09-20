# Cathode

Cathode is a local-first Android music player by 12Pts. It uses a cyan-on-black CRT/terminal interface while keeping precision controls touch-friendly. Music playback is local; the **Acquire** tab embeds [Monochrome](https://monochrome.samidy.com/) so files downloaded through its normal flow can be indexed from `Downloads/Monochrome`.

> Use Monochrome only for media you are legally authorized to download. Cathode contains no DRM circumvention, service extraction, or streaming implementation.

## MVP functionality

- Android MediaStore audio indexing
- FLAC, MP3, M4A, OGG, Opus, WAV, and other formats supported by the device/Media3
- Embedded cover art through MediaStore album-art URIs
- Search across track, artist, and album
- Background Media3/ExoPlayer playback
- Queue, scrubbing, previous/next, shuffle, and repeat
- Android media notification, lock-screen controls, and Bluetooth/headset controls
- Embedded HTTPS-only Acquire browser with Android DownloadManager handoff
- Monochrome download-folder detection and manual rescan
- Android 8.0+ (`minSdk 26`), targeting API 36

## Open in Android Studio

1. Open this repository as a project in a current Android Studio release.
2. Let Android Studio install Android SDK 36 and sync Gradle.
3. Run the `app` configuration on a device.
4. Grant audio permission when prompted.

The repository intentionally does not commit `local.properties`. Android Studio generates it for the local SDK path.

## Current boundary

Some Monochrome downloads may be generated as browser `blob:` URLs. Android WebView does not expose blob payloads to `DownloadManager`; Cathode reports that case instead of injecting a privileged JavaScript bridge. Regular HTTPS downloads work in-app. If Monochrome changes its delivery mechanism, the reliable fallback is its native Android app, which writes to the same `Downloads/Monochrome` folder Cathode indexes.

## Roadmap

- Room-backed playlists, favorites, and history
- Audio-session EQ and saved presets
- ReplayGain reading and normalization
- `.lrc` synchronized lyrics
- Gapless/crossfade controls
- Album and artist detail screens
- Folder browser and tag inspection
- Sleep timer and Android Auto
