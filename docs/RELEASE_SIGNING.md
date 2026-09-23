# Permanent release signing

Cathode's public repository never stores the private signing key or its passwords. The Android CI workflow builds the debug APK normally and only builds `cathode-release-apk` when all four repository secrets below exist.

## 1. Create and protect the keystore

Run this once on your own computer with Java installed:

```bash
keytool -genkeypair -v -keystore cathode-release.jks -alias cathode -keyalg RSA -keysize 4096 -validity 10000
```

Choose strong passwords and record the keystore password, key alias, and key password in a password manager. Back up `cathode-release.jks` in at least two secure locations. Losing it prevents future APKs from updating installations signed by it.

## 2. Convert the keystore to Base64

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("cathode-release.jks")) | Set-Clipboard
```

macOS:

```bash
base64 < cathode-release.jks | tr -d '\n' | pbcopy
```

Linux:

```bash
base64 -w 0 cathode-release.jks
```

## 3. Add GitHub Actions secrets

Open the repository, then **Settings → Secrets and variables → Actions → New repository secret**. Add:

- `CATHODE_RELEASE_KEYSTORE_BASE64`: the complete Base64 text
- `CATHODE_KEYSTORE_PASSWORD`: the keystore password
- `CATHODE_KEY_ALIAS`: the alias, normally `cathode`
- `CATHODE_KEY_PASSWORD`: the key password

Trigger Android CI again. A successful configured run uploads both `cathode-debug-apk` and `cathode-release-apk`.

## Installation rule

Android only updates an installed app when the package name and signing certificate match. The first release-signed Cathode APK cannot update a debug-signed installation; uninstall the debug build once, install the release build, and use release-signed APKs for every update afterward.
