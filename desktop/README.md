# Cathode Desktop foundation

The desktop application will live in `csanborndd17/cathode-desktop`, not as an Android module or APK artifact.

Recommended baseline:

- Kotlin + Compose Multiplatform
- JDK 17
- SQLite for the library index, playlists, overrides, history, and Transmission Log
- User-selected folders plus filesystem watching
- Desktop media-key and OS media-session integration
- Windows installer first; Linux and macOS after the player is stable

The new repository must start with `docs/parity.json`, `docs/PARITY.md`, and `docs/CHANGE_TEMPLATE.md`. The ledger defines behavior, not implementation. Android APIs must be replaced with native desktop equivalents.

