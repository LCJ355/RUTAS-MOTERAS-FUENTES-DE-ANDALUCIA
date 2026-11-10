# AGENTS.md

## Products (do not conflate)

- Web/PWA: `WEB-V3.40/`; version in `const RM_VERSION` in its `index.html` (currently 3.40). Root `index.html` is only a compatibility redirect to this folder.
- Native Android: `NATIVA-V1.8/`; Kotlin + osmdroid, one programmatic `MainActivity.kt` (no XML layouts) plus `CompassRoseView.kt`. Current version is in `NATIVA-V1.8/app/build.gradle` (1.9.7 / code 31).
- The old Capacitor APK source line is intentionally removed. A reconstructed installable APK is under `WEB-V3.40/APK-Android/`; it has no maintained source tree here. Do not reintroduce a second web `index.html`.
- Web and native are independent; native data is a generated snapshot from web data.

## Commands

- Web: `cd WEB-V3.40`; `npm install`; `npm run dev`; `npm test`.
- Web tests parse named helpers from `WEB-V3.40/index.html` by regex. Keep `KEYS`, `simplifyPolyline`, `serializeEndpoint`, `restoreEndpoint`, and `migrateLegacyStorage` extractable; there is no single-test selector.
- Native data: `cd NATIVA-V1.8`; `node tools\build_native_data.mjs`; verify with `node tools\build_native_data.mjs --check`.
- Native build from `NATIVA-V1.8`: `.\gradlew.bat --no-daemon assembleRelease`. Output is `app/build/outputs/apk/release/app-release.apk`; published copy is `NATIVA-V1.8/APKs/RutasFuentes-v<version>.apk`.
- Android toolchain: use JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`) and `ANDROID_HOME=C:\Android\sdk`. Use `C:\Android\sdk\platform-tools\adb.exe`; the PATH `adb` is stale on this workstation.
- `NATIVA-V1.8/local.properties` is local and ignored; create it from the SDK path if needed.

## Data and build traps

- `NATIVA-V1.8/app/src/main/assets/data.json` is generated; do not hand-edit. Native-only fixes belong in `NATIVA-V1.8/data_overrides.json`.
- `tools\build_native_data.mjs` reads `..\WEB-V3.40`; if either folder moves, update `webRoot` in that script.
- `fuentes_data.js` comes from the external Access pipeline at `G:\-IA-\FUENTES-Access\`, not from this repository; do not invent or regenerate it here.
- Web is online-only. `sw.js` only removes the old cache; do not add offline caching.
- Native release uses debug signing (`signingConfigs.debug`) with R8/shrinking; it is not Play-ready.

## Publishing and secrets

- Web publishes through Git/GitHub Pages at `/WEB-V3.40/`; the root redirect preserves the old URL. Never store tokens in the repo; temporary tokens must be fine-grained and limited to this repository.
