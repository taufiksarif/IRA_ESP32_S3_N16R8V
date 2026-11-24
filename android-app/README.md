# IRA Controller for Android

When connected, the Android app opens the dashboard served by the ESP32. When the controller is unavailable, it opens the same dashboard page packaged with the app and supplies simulated status and analyzer data. Demo controls and settings stay on the phone and do not reach hardware.

## Open in Android Studio

Open the **`android-app` folder** in Android Studio. Let Gradle sync, and install Android SDK Platform 37 if prompted. The project includes a Gradle wrapper and uses Android Gradle Plugin 9.4.1, compile SDK 37, target SDK 36, and JDK 17 or newer.

## Build and install

1. Connect an Android phone with USB debugging enabled, or start an Android emulator.
2. In Android Studio, select the **app** run configuration and press **Run ▶** to install and launch it.
3. To create a debug APK, choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The default output is `app/build/outputs/apk/debug/app-debug.apk`.

You can also build from a terminal in this folder with `./gradlew assembleDebug`.

## First use

1. Power on the ESP32 controller.
2. Connect the phone to its Wi-Fi access point (default SSID: `Repair Center`; default password: `12345678`, unless changed in firmware settings).
3. If Android asks whether to stay connected to a network without internet, choose to stay connected.
4. Open IRA. It uses `http://10.10.10.10/` by default. Offline mode displays the packaged dashboard with simulated data; when the controller responds, the app switches to its live dashboard.

The app requests the local Wi-Fi network and binds dashboard traffic to it. Analyzer TXT and CSV exports are saved to Downloads on Android 10 and later.

## Google Play release bundle

Choose **Build → Generate Signed Bundle / APK** in Android Studio, select **Android App Bundle**, then follow the signing wizard. Keep the upload keystore and passwords backed up securely and do not commit them to the repository. Increase `versionCode` in `app/build.gradle.kts` before publishing an update.
