# Changelog

## 0.3.2 — 2026-10-07

- Red consumption chart for up to 12 recent dated trips, with accessible values, chronological order and gaps for missing fuel readings. Each point is a whole-trip average; zero fuel remains valid.
- Electric-distance bar in trip details when Toyota provides hybrid data. Both charts use existing trip reads and preserve the cockpit style.
- Fixed gesture competition between vehicle paging and swipe-to-dismiss. Swipe from the left edge to return to the garage; horizontal paging and upward trip navigation remain available.
- Documented Huawei Watch Fit 4 Pro port constraints and Toyota API features not yet shown on the watch.
- About app screen, available from the garage and before sign-in: version/build, manual update check with progress and connection feedback, and confirmed clearing of the watch session, saved password and vehicle data.
- Manual update checks bypass the daily automatic-check interval and reuse the existing signed-APK installation flow.
- Verified the distance-weighted fuel average with unequal trips: 10 km at 10 L/100 km and 90 km at 5 L/100 km produce 5.5 L/100 km. Zero fuel and missing readings retain their existing treatment.
- Included token model/listener source files previously hidden by an overly broad credential ignore rule, so fresh checkouts contain the required code.

## 0.3.1 — 2026-10-05

- Swipe up or tap the car photo to open recent trips; existing horizontal controls navigation remains available.
- On-demand Toyota trip read (last 30 days, first page up to 50 trips), without waking the car or downloading GPS routes.
- Weighted fuel average, sample coverage, trip list and detail: distance, duration, fuel, average speed, optional electric-distance share and Toyota score. Missing values stay unknown; zero fuel remains valid.
- Separate loading/error state for history. Monthly comparisons and per-kilometre consumption charts await sufficient data.
- Trip fields depend on the vehicle and Toyota account. Parsing and navigation have been tested; a live trip response for the development account has not yet been confirmed.
- The phone sign-in confirmation reads configuration-aware string resources.

## 0.3.0 — 2026-10-05

- Black and red cockpit interface, compact fuel/battery dial beside the full car photo, metallic buttons and a matching climate dial.
- Centered button icons and labels; complete garage photos and long vehicle names; controls and confirmation dialogs fit round screens in all six languages.
- Active remote subscriptions remain usable when Toyota omits `remoteDisplay`. Vehicle metadata refreshes at startup instead of remaining cached indefinitely. Explicit Toyota account blocks are still respected.
- Console installers for Windows and macOS in the repository, linked from both READMEs. They download ADB and the signed watch APK, verify SHA-256 and preserve existing app data. Every release includes an installation README.
- Added `design.md` and trip-consumption research for the next version. Trip history is not part of this release.

## 0.2.1 — 2026-10-03

- The car screen is now two dials. The status dial shows the lock state, fuel or battery with a gauge around the edge, range, mileage and the time of the last report; tap that time to wake the car. Swipe left for the controls dial: lock, unlock (confirmed), climate and the map.
- Opening a car shows a loading ring until Toyota answers. Old data is never shown. The last car is read in the background while the garage is showing, so it usually opens at once.
- The garage appears only when the car pictures are ready.
- Every text fits on one line in all six languages: icons instead of labels, and long words shrink to fit. Errors on the dials appear full screen.
- Small and large round screens keep the same layout, and animations respect the system's reduce-motion setting.
- Faster start: the tokens are decrypted at the first request instead of at launch, and the unused emoji support no longer starts.

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
