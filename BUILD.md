# Building the VirtualCam Manager APK

## GitHub Actions (recommended)

1. Push to `main` (or run the workflow manually via Actions → Build APK → Run workflow).
2. Wait for the job to finish.
3. Download the artifacts:
   - `VirtualCam-Manager-debug`
   - `VirtualCam-Manager-release`

The workflow uses:
- JDK 17 (Temurin)
- Android SDK via android-actions/setup-android
- Gradle 8.9 + AGP 8.7.2
- Compose BOM 2024.10.01
- libsu 5.2.2

## Local build (Android Studio)

1. Clone the repository:
   ```bash
   git clone https://github.com/smithluke874/Android-VirtualCam-Manager.git
   cd Android-VirtualCam-Manager
   ```

2. Open the **manager-app** folder in Android Studio (File → Open → select the `manager-app` directory).

3. Let Android Studio sync Gradle.

4. Build → Build Bundle(s) / APK(s) → Build APK(s).

5. The debug APK will appear at:
   `manager-app/app/build/outputs/apk/debug/app-debug.apk`

## Command line

```bash
cd manager-app
gradle wrapper --gradle-version 8.9   # first time only if gradlew is missing
./gradlew assembleDebug
./gradlew assembleRelease
```

## After building

1. Install the Magisk module (`VirtualCam-Manager-Magisk-v1.2.0.zip`).
2. Install the APK you just built.
3. Grant root to the APK.
4. Use the Media tab to place `virtual.mp4` and the Settings tab to control the original flag files.

No LSPosed is required.
