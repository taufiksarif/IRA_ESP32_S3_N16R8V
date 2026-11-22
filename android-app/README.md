# IRA Controller for Android

Native Kotlin/Jetpack Compose dashboard for the IRA ESP32-S3 controller. It uses the same dark green panels, blue sequence buttons, yellow Run Mode button, red active/Stop buttons, status pills, queued notifications, press animation, and haptic feedback as the web dashboard.

The launcher icon uses the supplied microcontroller artwork and the app is named **IRA**. A local Preview mode displays and simulates the dashboard when the ESP32 is unavailable; Preview actions never reach hardware.

## Open in Android Studio

Open the **`android-app` folder** in Android Studio (not just the repository root). Let Gradle sync, and install Android SDK Platform 37 if Android Studio prompts you. The project includes a Gradle wrapper, so you do not need to install Gradle separately. It uses Android Gradle Plugin 9.4.1, Jetpack Compose, compile SDK 37, target SDK 36, and the wrapper downloads Gradle 9.9.1. The build requires JDK 17 or newer; Android Studio's bundled Java runtime is suitable.

## Build and install a debug app

1. Connect an Android phone with USB debugging enabled, or start an Android emulator.
2. In Android Studio, select the **app** run configuration and press **Run ▶** to install and launch it.
3. To create a debug APK instead, choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Android Studio shows the output location when the build completes. The default path is `app/build/outputs/apk/debug/app-debug.apk`.

You can also build the debug APK from a terminal opened in the `android-app` folder with `./gradlew assembleDebug`.

## Create a Google Play release bundle

Google Play needs a signed release **Android App Bundle (AAB)**; the debug APK above is for local installation and testing.

1. In Android Studio, choose **Build → Generate Signed Bundle / APK**.
2. Select **Android App Bundle**, then select the **app** module.
3. If this is the first release, choose **Create new** to make an upload keystore and key. Keep the keystore file and passwords backed up securely; you need the upload key for future updates. Do not commit it to the repository.
4. Select the **release** build variant and finish the wizard. Android Studio displays the generated AAB location. The usual Gradle output path is `app/build/outputs/bundle/release/app-release.aab`.
5. Upload the AAB in Google Play Console and follow its Play App Signing setup and release checklist.

For updates, increase `versionCode` in `app/build.gradle.kts` before creating the next release bundle.

## First use

1. Power on the ESP32 controller.
2. Connect the phone to its Wi-Fi access point (default SSID: `Repair Center`; default password: `12345678`, unless changed in firmware settings).
3. If Android asks whether to stay connected to a network without internet, choose to stay connected. If needed, use **Open Wi-Fi settings** in the app's connection card.
4. Run **IRA Controller**. The default controller address is `10.10.10.10`.

The app does not change or replace the ESP32 firmware. It calls the existing local HTTP APIs. The native dashboard covers live status, core controls with output confirmations, sequence progress, controller health, all 13 sequence timers, native AP/router Wi-Fi settings, and the protocol analyzer. Polling pauses in the background. The app requests the no-internet Wi-Fi network directly for controller traffic, and the connection screen explains Android's “stay connected” prompt. The analyzer includes CAN and UART/Modbus receive logs, bitrate and mode settings, filters, local recording, TXT/CSV sharing, and gated CAN/UART transmit controls.
