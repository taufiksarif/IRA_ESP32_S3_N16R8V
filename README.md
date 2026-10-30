# I.R.A. ESP32-S3 N16R8V Relay Controller

I.R.A. (Interactive Relay Automation) is an ESP32-S3 web-controlled relay sequencer with automatic UP/DOWN cycles, configurable timers, CAN/TWAI monitoring and transmission, and UART2 support for Modbus RTU, passive monitoring, or raw text.

The Arduino sketch is [`ESP32-S3-N16R8V.ino`](ESP32-S3-N16R8V.ino). The included board image is [`ESP32-S3-N16R8V.jpg`](ESP32-S3-N16R8V.jpg).

## Board setup

The pin assignments target the ESP32-S3 N16R8 board shown in the image. In Arduino IDE, select **ESP32-S3 Dev Module** or the exact board variant if installed. Configure **16 MB flash** and **8 MB OPI PSRAM** using the available board menu options.

The sketch uses Arduino-ESP32 libraries and the legacy ESP-IDF TWAI API from `driver/twai.h`. Select a core version that provides this API; if your installed core has changed it, the TWAI calls may need an API update.

## GPIO assignment

All seven control outputs are active-HIGH in the sketch. Use suitable driver stages or relay modules; do not drive relay coils directly from ESP32 GPIOs.

| Function | GPIO | Electrical behavior |
|---|---:|---|
| Brake | 4 | Active-HIGH |
| Enable | 5 | Active-HIGH |
| Up | 6 | Active-HIGH |
| Down | 7 | Active-HIGH |
| Speed 0 | 8 | Active-HIGH |
| Speed 1 | 9 | Active-HIGH |
| Speed 2 | 10 | Active-HIGH |
| Ready button | 11 | Button to GND, active-LOW; internal pull-up |
| Ready relay | 12 | Active-HIGH |
| RS-485 DE and /RE | 13 | LOW receive, HIGH transmit; tie DE and /RE together |
| Buzzer relay | 14 | Active-HIGH |
| UART2 RX / TX | 16 / 17 | Connect to transceiver RO / DI respectively |
| CAN TX / RX | 18 / 21 | Connect to a CAN transceiver, not directly to CANH/CANL |
| Optional status LED | 2 | Active-HIGH output; use a series resistor |

Check the GPIO labels on your exact board before wiring. The pictured board has an RGB LED module whose pin/protocol is not assumed by this sketch; GPIO2 is used only as an optional external status LED output.

## Features

- Wi-Fi access point and optional router connection. The dashboard is served at `http://10.10.10.10/` while connected to the device AP.
- Manual controls for Brake, Enable, direction, and speed selections. Direction and speed are interlocked during automatic sequences.
- Auto UP and Auto DOWN timed sequences, plus Run Mode that alternates direction until its configured duration expires.
- Configurable timers stored in ESP32 NVS, including the ready-signal delay and Run Mode duration.
- Ready button behavior: a debounced press starts the configured delay; the ready relay activates only while the button remains held. A raw release turns the relay off immediately.
- CAN/TWAI frame monitoring and manually initiated frame transmission through the protocol analyzer.
- UART2 modes: Modbus RTU slave (unit ID 1), passive Modbus monitor, and raw UART text.
- Live status, diagnostics, and a bounded analyzer event log.

CAN receive traffic is logged and counted; CAN messages do not directly control relay outputs. Modbus commands can request sequences and update timer settings.

## Modbus register summary

| Holding register | Meaning |
|---:|---|
| 0 | Last command: 0 stop, 1 up, 2 down, 3 Run Mode; writable |
| 1 | Controller state: 0 idle, 1 sequence, 2 Run Mode; read-only |
| 2 | Output bitfield; bits 0–6 map to Brake, Enable, Up, Down, Speed 0, Speed 1, Speed 2 |
| 3 | Current sequence-step time remaining in seconds |
| `0x0010`–`0x001C` | Timer settings; seconds except Run Mode duration, which is in hours |

Supported Modbus functions are 03 (read holding registers), 06 (write single register), and 16/`0x10` (write multiple timer registers). Use function 06 to issue commands; function 16 is reserved for timer settings.

## Safety and security

This is a bench controller, not a safety controller. The sketch initializes its outputs to LOW and forces direction/speed outputs off before beginning a sequence, but external pull-downs, interlocks, appropriate isolation, and fail-safe relay hardware are still required for hazardous equipment. Verify output polarity and behavior with the controlled equipment disconnected before use.

The existing Wi-Fi behavior is unchanged: the default access-point password is `12345678`, and the web dashboard has no user login. Decide on any security changes before exposing the controller to other networks or users.

For ESP32-S3 N16R8 modules, GPIO22–25 are unavailable; GPIO26–32 are typically reserved for flash/PSRAM, and GPIO33–37 are used by octal flash/PSRAM on R8 modules. GPIO19/20 are avoided because they are used by native USB/JTAG by default. Avoid strapping pins GPIO0, GPIO3, GPIO45, and GPIO46 for relay-control outputs.
