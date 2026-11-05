# I.R.A. ESP32-S3 N16R8V Relay Controller

I.R.A. (Interactive Relay Automation) is an ESP32-S3 web-controlled relay sequencer with automatic UP/DOWN cycles, configurable timers, CAN/TWAI monitoring and transmission, UART2 support for Modbus RTU/passive monitoring/raw text, an onboard RGB status LED, and a 16x2 LCD status display.

The Arduino sketch is [`ESP32-S3-N16R8V.ino`](ESP32-S3-N16R8V.ino). The included board image is [`ESP32-S3-N16R8V.jpg`](ESP32-S3-N16R8V.jpg).

## Board setup

The pin assignments target the ESP32-S3 N16R8 board shown in the image. In Arduino IDE, select **ESP32-S3 Dev Module** or the exact board variant if installed. Configure **16 MB flash** and **8 MB OPI PSRAM** using the available board menu options.

The sketch uses Arduino-ESP32 libraries, **LiquidCrystal**, **Adafruit NeoPixel**, and the legacy ESP-IDF TWAI API from `driver/twai.h`. Install the **LiquidCrystal** and **Adafruit NeoPixel** libraries through Arduino Library Manager if they are not already installed. Select an Arduino-ESP32 core version that provides the legacy TWAI API; if your installed core has changed it, the TWAI calls may need an API update.

## GPIO assignment

All seven control outputs are active-HIGH in the sketch. Use suitable driver stages or relay modules; do not drive relay coils directly from ESP32 GPIOs.

| Function | GPIO | Electrical behavior |
|---|---:|---|
| Brake | 10 | Active-HIGH |
| Enable | 9 | Active-HIGH |
| Up | 8 | Active-HIGH |
| Down | 7 | Active-HIGH |
| Speed 0 | 6 | Active-HIGH |
| Speed 1 | 5 | Active-HIGH |
| Speed 2 | 4 | Active-HIGH |
| Ready button | 14 | Button to GND, active-LOW; internal pull-up |
| Ready relay | 11 | Active-HIGH |
| RS-485 DE and /RE | 13 | LOW receive, HIGH transmit; tie DE and /RE together |
| Buzzer relay | 12 | Active-HIGH |
| UART2 RX / TX | 16 / 17 | Connect to transceiver RO / DI respectively |
| CAN TX / RX | 18 / 21 | Connect to a CAN transceiver, not directly to CANH/CANL |
| Onboard addressable RGB LED | 48 | Default for the pictured revision; change `IRA_RGB_LED_PIN` to 38 if required by your board revision |
| LCD RS | 1 | 16x2 character LCD, 4-bit mode |
| LCD E | 2 | 16x2 character LCD, 4-bit mode |
| LCD D4 / D5 / D6 / D7 | 15 / 39 / 40 / 41 | LCD data lines; leave D0–D3 unconnected |
| Physical sequence reset button | 42 | Button to GND, active-LOW; internal pull-up; stops sequences and silences the post-Run-Mode buzzer; does not reboot the ESP32 |

Check the RGB LED data pin against your exact board revision. The sketch assumes an addressable one-pixel LED on GPIO48, as on many N16R8 boards; some revisions use GPIO38. The `IRA_RGB_LED_PIN` constant is near the other pin assignments. The sketch sets a low brightness to limit LED current.

### LCD wiring and display

The sketch drives the pictured 16-pin parallel character LCD as a 16x2 HD44780-compatible display in 4-bit mode. Connect LCD RS, E, D4, D5, D6, and D7 to the GPIOs in the table. Connect RW to GND, and leave D0–D3 unconnected. Connect LCD VSS to GND, VDD to the supply specified for your exact LCD module, VO to the contrast potentiometer/wiper, and the backlight pins (A/K or BLA/BLK) according to the module markings. The display refreshes about twice per second with the current sequence step and remaining time, or idle/CAN status and AP client count. CAN faults take priority.

Check the LCD's logic-level requirements before connecting it. ESP32 GPIOs are 3.3 V and are not 5 V tolerant; a 5 V LCD module may require level shifting on the six ESP32-to-LCD signal lines. Do not connect a 5 V LCD signal output to an ESP32 pin. The selected LCD pins leave GPIO38 unused for board revisions that use it for the onboard RGB LED.

## Features

- Wi-Fi access point and optional router connection. The dashboard is served at `http://10.10.10.10/` while connected to the device AP.
- Manual controls for Brake, Enable, direction, and speed selections. Direction and speed are interlocked during automatic sequences.
- Auto UP and Auto DOWN timed sequences, plus Run Mode that alternates direction until its configured duration expires.
- Physical sequence reset button stops an active sequence, returns the sequence controller to idle (READY when CAN is healthy), and silences the post-Run-Mode buzzer. It does not reboot the ESP32.
- Configurable timers stored in ESP32 NVS, including the ready-signal delay and Run Mode duration.
- Ready button behavior: a debounced press starts the configured delay; the ready relay activates only while the button remains held. A raw release turns the relay off immediately.
- The buzzer beeps twice for 2 seconds each when an access-point client connects. After Run Mode completes, it gives 2-second pulses at the configured interval until stopped.
- CAN/TWAI frame monitoring and manually initiated frame transmission through the protocol analyzer.
- UART2 modes: Modbus RTU slave (unit ID 1), passive Modbus monitor, and raw UART text.
- Live status, diagnostics, and a bounded analyzer event log.
- Local 16x2 LCD status display for sequence progress, CAN faults, and AP clients.

The RGB status LED blinks blue while no access-point client is connected, glows green when a client is connected and the controller is idle, glows amber during an automatic sequence, and glows red if the CAN driver is unavailable or the bus is off. Fault and sequence indications take priority over the Wi-Fi connection indication.

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
