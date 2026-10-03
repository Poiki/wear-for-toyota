# Changelog

## 0.2.0 — 2026-10-03

- "Save password" switch on the watch and phone sign-in, off by default. The password is encrypted with its own AES-256 Android Keystore key (StrongBox when available, usable only while the watch is unlocked when it has a screen lock). The watch reads it only to sign in again by itself when Toyota ends the session, and deletes it if Toyota rejects it.
- Self-update: at most once a day the watch checks GitHub Releases and offers the new version. The first time it opens the "Install unknown apps" switch for the app. It then downloads the APK and Android's installer asks for confirmation.
- Temporary Toyota sign-in errors (429/5xx) now read "service unavailable" instead of "check email and password", and never delete a saved password or the session.
- My Garage and the link screen show progress while something is running.

## 0.1.0 — 2026-10-02

First release.

- My Garage with one card per car and Toyota's picture of it.
- Vehicle screen: lock state, range, fuel or battery, mileage, data timestamp, last parked position.
- Lock / Unlock on one screen, unlock with confirmation, every command verified against the car's real state.
- Climate dial controlled with the crown or +/- (18–29 °C), start/stop for 10 minutes.
- Standalone: sign in on the watch, or send the tokens from the optional phone app.
- Full-screen success/failure confirmations with haptics.
- English, Spanish, German, French, Italian and Portuguese.
