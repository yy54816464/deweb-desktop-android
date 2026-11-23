# DeWEB-Desktop Android Shell

Android WebView shell for the DeWEB on-chain Web desktop.

## What it does

- Loads the official Web desktop over HTTPS.
- Injects the wallet provider bridge into allowed origins.
- Provides native wallet approval dialogs and per-window screenshot protection.
- Supports multiple site windows, floating windows, desktop layout memory, and
  Tape-verified on-chain Web loading.
- Update flow is browser-only: the app checks a signed metadata file and opens
  `https://download.dewebdesk.link/`. It does not download or install APKs.

## Requirements

- Android Studio or Gradle 8.9
- JDK 17
- Android SDK 35
- minSdk 24

## Build debug

```bash
./gradlew :app:assembleDebug
```

Debug builds use the debug signing key and do not require a release keystore.

## Build signed release

Release signing is optional. Without a keystore the project still builds an
unsigned release APK.

1. Copy the example:

```bash
cp keystore.properties.example keystore.properties
```

2. Fill in the real values and point `storeFile` at your own `.jks`. Keep both
   the properties file and the keystore outside the repository.

3. Build:

```bash
./gradlew :app:assembleProdRelease
# or
./gradlew :app:assembleBetaRelease
```

Outputs:

```text
app/build/outputs/apk/prod/release/app-prod-release.apk
app/build/outputs/apk/beta/release/app-beta-release.apk
```

Never commit `keystore.properties`, `*.jks`, `*.keystore`, or private keys.

## Updates

- Production metadata: `https://download.dewebdesk.link/android-latest.json`
- Beta metadata: `https://download.dewebdesk.link/android-latest-beta.json`
- Metadata is ECDSA P-256/SHA-256 signed. The app embeds the public key.
- The update dialog displays the APK SHA-256 and certificate fingerprint.
- The app has no `REQUEST_INSTALL_PACKAGES` permission and never invokes an
  installer.

## Permissions

```text
INTERNET
SYSTEM_ALERT_WINDOW   (floating windows)
REORDER_TASKS
```

## Security boundaries

- No private key, seed phrase, or wallet password is stored by the shell.
- `sourceOrigin` from WebView is authoritative for bridge calls.
- Mixed content is disabled; file/content access is disabled.
- Update metadata is signed and the app only opens the official download page.
- Site windows and the background WebView are isolated per activity/webview.

## License

Project code: MIT. See `LICENSE`.

Third-party components, including the vendored `@okxconnect/ui` bundle, retain
their upstream licenses. See `THIRD_PARTY_NOTICES.md` and `third_party/`.
