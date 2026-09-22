# Cathode privacy statement

Cathode is a local-first music player. It does not operate a Cathode account service, advertising system, analytics service, or remote music catalog.

## Data stored on the device

Cathode stores local preferences, metadata and artwork overrides, favorites, playlists, playback state, listening history, Transmission Log statistics, diagnostics, and playlist-migration sessions on the device. Spotify access/refresh tokens and the YouTube Data API key are encrypted with a key held by Android Keystore.

## Device media

Cathode reads audio exposed through Android MediaStore after the user grants audio permission. Metadata edits are non-destructive Cathode overrides unless the user explicitly confirms device-file deletion through Android's system prompt.

## Network services

- Spotify authorization and playlist metadata use Spotify's official OAuth and Web API endpoints.
- Public YouTube playlist metadata uses YouTube Data API v3 with the user's API key.
- Discover opens explicitly listed third-party websites in an isolated HTTPS-only WebView. Downloads are handed to Android DownloadManager.

Cathode does not receive Spotify or Google passwords, scrape authenticated webpages, stream Spotify/YouTube audio, bypass DRM, or automatically download an imported playlist.

Third-party services receive the network information normally required to answer requests, including the device IP address and the credentials or cookies used for that service. Their own terms and privacy policies apply.

## Diagnostics

The local diagnostics log records runtime crashes and selected playback, download, and library-scan failures. It is not uploaded automatically and can be cleared in Settings.

## Backups and removal

Android backup behavior may copy eligible app data according to the device's backup settings. Disconnecting Spotify or removing the YouTube key deletes the corresponding stored credential. Uninstalling Cathode removes its private app data but does not delete downloaded music unless the user explicitly deletes those files.
