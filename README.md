# Wear for Toyota

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/A0A71AC06Z)

Unofficial, standalone Wear OS app for Toyota and Lexus cars sold in Europe (MyToyota / Toyota Connected Services Europe accounts). It shows the lock state, range, fuel or battery and mileage, and sends the remote commands MyToyota offers for your car: lock, unlock and climate. English, Spanish, German, French, Italian and Portuguese.

[Versión en español](README.es.md)

> **Not affiliated with, endorsed by or supported by Toyota.** The app talks to the same backend as the official MyToyota app, as documented by the community (see Credits). Toyota can change that backend at any time and break the app. Use it at your own risk and only with your own car and account.

## What it does

- **My Garage**: one card per car on the account, with Toyota's own picture of the car.
- **Vehicle**: locked / unlocked, range, fuel or battery, mileage, when the data was reported, and the last parked position (opens the maps app on the watch).
- **Lock / Unlock**: one screen with both actions. Unlocking asks for an on-screen confirmation. After every command the app wakes the car and verifies the real state before telling you "Vehicle locked".
- **Climate**: a dial you turn with the crown or the +/- buttons (18–29 °C); start or stop for 10 minutes.
- **Standalone**: the watch talks to Toyota on its own over Wi-Fi or LTE, or through the Bluetooth proxy of the paired phone. The phone app is optional.
- **Battery-friendly by design**: no background polling, no services, one small encrypted state file. The car is only woken when you ask.

## Requirements

- A Toyota or Lexus account in Europe with Connected Services, and a car with remote services. Whatever MyToyota offers for your car is what this app can do.
- A Wear OS 3 or newer watch (Android 11, API 30+). Tested on a OnePlus Watch 3 (Wear OS 6) and the Wear OS 5 emulator.
- To build: JDK 17 or newer and an Android SDK with platform 37 and build-tools 36. Android Studio is optional; everything works from the command line.

## Install your own copy

1. Clone the repository and build both apps:

   ```bash
   cd android && ./gradlew :watch:assembleRelease :phone:assembleRelease
   ```

   On Windows use `gradlew.bat`. The APKs land in `android/watch/build/outputs/apk/release/` and `android/phone/build/outputs/apk/release/`. Without your own keystore they are signed with the debug key, which is fine for sideloading.

2. Enable developer options and wireless debugging on the watch: Settings → System → About → tap "Build number" seven times → Developer options → Wireless debugging. Then, from your computer:

   ```bash
   adb pair <watch-ip>:<pairing-port>
   adb connect <watch-ip>:<port>
   ```

3. Install the watch app:

   ```bash
   adb -s <watch-serial> install -r android/watch/build/outputs/apk/release/watch-release.apk
   ```

4. Sign in, either way:
   - **On the watch**: open the app → "Sign in here" → type your MyToyota email and password with the watch keyboard.
   - **From the phone** (optional): install `phone-release.apk` on the paired phone with `adb install -r`, open "Wear for Toyota", sign in. The tokens travel to the watch over the Bluetooth link; the phone keeps nothing.

5. Optional, for a signed release of your own: create `android/keystore.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. It is git-ignored and picked up automatically. Both APKs must share the same signature for the phone → watch link to work.

## Security and privacy

- Your password is used once, for the login handshake, and never stored. The watch keeps the OAuth tokens encrypted with a key that lives in the Android Keystore; backups are disabled.
- No analytics and no third-party servers. The watch talks only to Toyota's endpoints (`*.toyotaconnectedeurope.io`, `b2c-login.toyota-europe.com`) and, once per car, to Toyota's image CDN.
- The static identifiers of the official app (client id, API key) are the public ones documented by pytoyoda. Toyota may rotate them.
- Unlocking asks for confirmation but, by design, not for a watch PIN. Anyone wearing an unlocked watch could unlock your car. If you want that barrier, set a screen lock on the watch: the app then also requires the watch to be unlocked.
- Logs never contain tokens or credentials. The debug-only helpers (token injection, demo mode) do not exist in release builds.
- Full privacy policy: [PRIVACY.md](PRIVACY.md).
- Found a vulnerability or a bug? Open an issue. Please never paste tokens, VINs or coordinates.

## Known limits

- Europe only; other regions use different backends.
- The API is unofficial. When Toyota changes it, the app needs an update; pytoyoda's issue tracker is the early-warning system.
- Remote commands depend on your car and subscription. Climate runs for 10 minutes and Toyota caps the number of starts per ignition cycle.
- Not on Google Play: Play forbids password input on the watch, and an unofficial API would not pass review. Sideload only.

## Development

- `android/core`: Toyota client (login, refresh, reads, commands) and payload parsing, with unit tests on real fixtures: `./gradlew :core:testDebugUnitTest`.
- `android/watch` and `android/phone`: the two apps. Compose for Wear OS Material 3; the phone app is a single screen.
- Demo mode (debug builds only): `adb shell am start -n com.poiki.toyotawear/.MainActivity --ez demo true` seeds a fake car so you can work on the UI without an account.
- `probe/probe.py`: read-only dump of what Toyota returns for an account (`uv run probe/probe.py`).
- `docs/`: research on the Toyota API and authentication, the Wear OS stack, other manufacturers' watch apps, the technical decisions and the measurements.
- Bug reports and improvement ideas are welcome: open an issue or a pull request.

## Credits

- [pytoyoda](https://github.com/pytoyoda/pytoyoda) and [ha_toyota](https://github.com/pytoyoda/ha_toyota) (MIT): the community documentation of the MyToyota Europe API this app follows (endpoints, headers, login flow, wake and refresh strategy). The test fixtures in `android/core/src/test/resources` come from pytoyoda.
- [DurgNomis-drol/mytoyota](https://github.com/DurgNomis-drol/mytoyota), the project pytoyoda descends from. Also studied: [openhab-mytoyota-binding](https://github.com/Prinsessen/openhab-mytoyota-binding), [evcc](https://github.com/evcc-io/evcc) and [ioBroker.toyota](https://github.com/TA2k/ioBroker.toyota).
- [Material Symbols](https://github.com/google/material-design-icons) (Apache 2.0) for the icons.
- [Wear OS samples](https://github.com/android/wear-os-samples) (Apache 2.0) for the Compose for Wear OS project baseline.

## License

[PolyForm Noncommercial 1.0.0](LICENSE): use, modify and share it freely for noncommercial purposes, keep the copyright notice, and credit this project in anything you build on it.
