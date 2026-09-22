# Cathode platform parity

This is the handoff contract between Cathode for Android and the planned desktop application. The machine-readable source of truth is [parity.json](parity.json).

## Repository layout

- `cathode-android`: Android application, Android CI, and the canonical ledger until desktop exists.
- `cathode-desktop`: planned sibling repository for Windows, Linux, and macOS.
- Desktop installers belong to the desktop repository, not Android CI.
- Every user-facing change must update the ledger and add a record under `docs/changes/`.

This split prevents Android Gradle, signing, permissions, and MediaStore behavior from becoming coupled to desktop packaging. Shared behavior uses stable feature IDs instead of blind code copying.

## Current matrix

| Feature | Contract ID | Android | Desktop |
|---|---|---:|---:|
| Local library scan | `library.local-scan` | Implemented | Planned |
| Live library refresh | `library.live-refresh` | Implemented | Planned |
| Download state and indexing | `discover.download-status` | Implemented | Planned |
| FLAC/lossless playback | `playback.lossless` | Implemented | Planned |
| System media controls | `playback.system-session` | Implemented | Planned |
| Queue, shuffle, repeat, scrubbing | `playback.transport` | Implemented | Planned |
| Playback-session restoration | `playback.session-restore` | Implemented | Planned |
| Sleep timer | `playback.sleep-timer` | Implemented | Planned |
| Playback error recovery | `playback.error-recovery` | Implemented | Planned |
| Albums, artists, folders, playlists | `library.browse` | Implemented | Planned |
| Search and custom tags | `library.search` | Implemented | Planned |
| Favorites and playlists | `library.collections` | Implemented | Planned |
| Metadata and artwork overrides | `library.metadata-overrides` | Implemented | Planned |
| Artwork-derived visuals | `visual.artwork-theme` | Implemented | Planned |
| Profile and customization | `profile.customization` | Implemented | Planned |
| Transmission Log | `history.transmission-log` | Implemented | Planned |
| Discover hub | `discover.source-hub` | Implemented | Planned |
| Playlist-link library conversion | `import.playlist-link` | Partial | Planned |
| Equalizer | `audio.equalizer` | Partial | Planned |
| ReplayGain | `audio.replaygain` | Implemented | Planned |
| Gapless and crossfade | `playback.transitions` | Partial | Planned |
| Synchronized lyrics | `library.lyrics` | Implemented | Planned |

## Change rule

1. Add or update the stable ID in `parity.json`.
2. Add `docs/changes/<version>.md` using `CHANGE_TEMPLATE.md`.
3. State Android behavior, desktop behavior, platform constraints, storage changes, and migration work.
4. Implement the other platform or mark it `queued`.
5. Never mark platforms equal when only their visuals or labels match.

Statuses are `implemented`, `partial`, `queued`, `planned`, and `not_applicable`.
