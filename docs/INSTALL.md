# Install and update Cathode

## Development APK

1. Open the successful Android CI run.
2. Download the `cathode-debug-apk` artifact and extract it.
3. Transfer `app-debug.apk` to the Android device.
4. Open the APK and allow installation from that file/browser app if Android asks.

Debug APKs produced by the existing CI signing cache can update earlier APKs signed by that same cached debug key.

## Permanent release APK

The first production-signed APK uses a different signature from development builds. Android will require the development build to be uninstalled once before installing the first production release. This removes Cathode's private app data, so export or record anything important first; downloaded music remains outside the app.

After the first production release is installed, every later APK signed by the same permanent keystore installs as an in-place update:

1. Transfer the newer APK to the phone.
2. Open it.
3. Choose **Update**.

Never delete or replace the production keystore. Android will reject updates signed with another key.
