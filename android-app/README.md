# IRA Controller for Android

Native Kotlin/Jetpack Compose dashboard for the IRA ESP32-S3 controller. It uses the same dark green panels, blue sequence buttons, yellow Run Mode button, red active/Stop buttons, status pills, queued notifications, press animation, and haptic feedback as the web dashboard.

The launcher icon uses the supplied microcontroller artwork and the app is named **IRA**. A local Preview mode displays and simulates the dashboard when the ESP32 is unavailable; Preview actions never reach hardware.

## Open in Android Studio

Open the `android-app` folder as a project. Android Studio will prompt to sync Gradle and install any missing Android SDK components. The app uses Android Gradle Plugin 9.4.1, built-in Kotlin, Jetpack Compose, compile SDK 37, and target SDK 36. This plugin line requires Gradle 9.6 or newer and JDK 17 or newer.

## First use

1. Power on the ESP32 controller.
2. Connect the phone to its Wi-Fi access point (default SSID: `Repair Center`; default password: `12345678`, unless changed in firmware settings).
3. If Android asks whether to stay connected to a network without internet, choose to stay connected.
4. Run **IRA Controller**. The default controller address is `10.10.10.10`.

The app does not change or replace the ESP32 firmware. It calls the existing local HTTP APIs. The native dashboard covers live status, core controls with output confirmations, sequence progress, controller health, all 13 sequence timers, native AP/router Wi-Fi settings, and the protocol analyzer. Polling pauses in the background. The app requests the no-internet Wi-Fi network directly for controller traffic, and the connection screen explains Android's “stay connected” prompt. The analyzer includes CAN and UART/Modbus receive logs, bitrate and mode settings, filters, local recording, TXT/CSV sharing, and gated CAN/UART transmit controls.
