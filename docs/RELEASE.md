# Cathode Android release signing

Never commit the production keystore or its passwords. Back up the keystore in at least two encrypted locations; Android updates must be signed by the same key.

Create the key locally:

```sh
keytool -genkeypair -v -keystore cathode-release.jks -alias cathode -keyalg RSA -keysize 4096 -validity 10000
```

Provide these environment variables before building:

```text
CATHODE_KEYSTORE_FILE=/absolute/path/to/cathode-release.jks
CATHODE_KEYSTORE_PASSWORD=...
CATHODE_KEY_ALIAS=cathode
CATHODE_KEY_PASSWORD=...
```

Then run:

```sh
bash gradlew testDebugUnitTest assembleRelease bundleRelease --stacktrace
```

Outputs:

- APK: `app/build/outputs/apk/release/app-release.apk`
- Play-compatible bundle: `app/build/outputs/bundle/release/app-release.aab`

GitHub CI intentionally produces only a debug APK until encrypted repository secrets and a base64-encoded production keystore are configured.
