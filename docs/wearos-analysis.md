# Análisis técnico: app standalone mínima para Wear OS (estado a 2026-10-01)

Objetivo: app de control/estado de un coche (lock state, range, fuel/battery, climate, last update; acciones lock, unlock, climate on/off + temperature, refresh, locate), muy pequeña, eficiente en batería y sin depender del teléfono.
Fuentes: docs oficiales de Android, Android Developers Blog, AndroidX release notes, Maven (tamaños medidos el 2026-10-01 con GET sobre `dl.google.com/android/maven2` y `repo1.maven.org`), `sdkmanager --list` del SDK local. Lo no verificado se marca "(sin confirmar)".

## 0. Cifras clave

| Tema | Dato |
|---|---|
| Versión actual | Wear OS 7 = Android 17 / API 37 (16-jun-2026); Wear OS 6.1 = API 36.1; Wear OS 6 = API 36 |
| Play (Wear) | targetSdk ≥ 35 obligatorio desde 31-ago-2026 (extensión hasta 1-nov-2026); 64-bit obligatorio para apps con código nativo desde 15-sep-2026 |
| minSdk recomendado | 30 (Wear OS 3, 2021+) — cubre todo lo que recibe actualizaciones; 33 si se quiere descartar relojes huérfanos en Wear OS 3 |
| UI | Compose Material 3 `1.7.0` (estable desde `1.5.0`, 27-ago-2025); M2.5 "superseded"; Views `androidx.wear:wear 1.4.0` sigue vivo pero sin M3 Expressive |
| Tiles | `tiles 1.6.2` + `protolayout 1.4.2`; `onTileRequest` debe resolver en ≤10 s; refresco automático recomendado ≥2 h |
| Complications | `UPDATE_PERIOD_SECONDS` ≥ 300 o 0 (push); `requestUpdate()` ≤ 1 cada 5 min de media |
| Auth | Sin WebView; Credential Manager (Wear OS 3+, passkeys desde Pixel Watch Wear OS 5.1), OAuth PKCE vía teléfono (`RemoteAuthClient`), token por Data Layer |
| Secure storage | `security-crypto` deprecated (todo) → Android Keystore directo |
| Toolchain | Kotlin 2.4.20, AGP 9.4.x (Gradle ≥ 9.6, JDK 17), Gradle 9.8.0, compileSdk/targetSdk 37; Android Studio no es obligatorio |

## 1. Plataforma Wear OS

| Wear OS | Android / API | Imagen emulador (`system-images;…;android-wear*;x86_64`) | Fecha |
|---|---|---|---|
| 3 / 3.5 | 11 / 30 | `android-30;android-wear` (x86, arm64) | ago-2021 / oct-2022 |
| 4 | 13 / 33 | `android-33;android-wear` | jul-2023 |
| 5 | 14 / 34 | `android-34;android-wear` | jul-2024 |
| 5.1 | 15 / 35 | `android-35-ext15;android-wear` | mar-2025 (Pixel: nov-2024) |
| 6 | 16 / 36 | `android-36;android-wear-signed` | jul-2025 |
| 6.1 | 16 QPR2 / 36.1 | `android-36.1;android-wear-signed` | dic-2025 |
| 7 | 17 / 37 | `android-37.0;android-wear-signed` | 16-jun-2026 |

Fuentes: Wear OS 5 "based on Android 14 (API level 34)" (https://developer.android.com/training/wearables/versions/5/changes), 5.1 → API 35 (https://developer.android.com/training/wearables/versions/5-1), 6 → API 36 (https://developer.android.com/training/wearables/versions/6/changes), 6.1 → API 36.1 (https://developer.android.com/training/wearables/versions/6-1), 7 → API 37 (https://developer.android.com/training/wearables/versions/7/changes), tabla histórica y fechas (https://en.wikipedia.org/wiki/Wear_OS), nombres de imágenes verificados con `sdkmanager --list` (SDK local, 2026-10-01).

- Cuota por versión: Google no publica dashboard de distribución para Wear OS (sin confirmar cifras). Proxy: Wear OS 7 llegó en jun-jul 2026 a Pixel Watch 2/3/4 y Galaxy Watch 9/Ultra 2; Oppo/Xiaomi aún completaban Wear OS 6 (https://en.wikipedia.org/wiki/Wear_OS; https://www.androidauthority.com/wear-os-7-widgets-vs-tiles-3669585/). Google cita "up to 10% improvement in battery life" al pasar de 5→6 y de 6→7 (https://android-developers.googleblog.com/2025/05/whats-new-in-wear-os-6.html, https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html).
- Suelos de librería: Wear Compose minSdk 25 (https://developer.android.com/jetpack/androidx/releases/wear-compose); Tiles 1.6 minSdk 23 y compileSdk 35 (https://developer.android.com/jetpack/androidx/releases/wear-tiles). El sample oficial `ComposeStarter` usa minSdk 26 / compileSdk 36 / targetSdk 37 (https://github.com/android/wear-os-samples/blob/main/ComposeStarter/app/build.gradle.kts).
- Recomendación: minSdk 30. Wear OS 2 (API 28) no recibe actualizaciones y usa el modelo antiguo de app companion; desde Wear OS 3 existen Play standalone, Tiles modernos y Compose. No hay "legacy baggage" relevante entre 30 y 33 para esta app.

Google Play / calidad:
- Wear: "New apps and app updates must target Android 15 (API level 35) or higher" antes del 31-ago-2026; extensión hasta 1-nov-2026 (https://support.google.com/googleplay/android-developer/answer/11926878).
- 64-bit: desde 15-sep-2026 toda app/actualización con código nativo debe traer build 64-bit (https://android-developers.googleblog.com/2026/04/get-your-wear-os-apps-ready-for-64-bit-requirement.html). Una app Kotlin pura no tiene `.so` → no aplica.
- Manifest obligatorio: `<uses-feature android:name="android.hardware.type.watch"/>` (nunca `required=false`) y `<meta-data android:name="com.google.android.wearable.standalone" android:value="true"/>`; Play valida el flag y oculta las apps no-standalone (o mal declaradas) en relojes sin teléfono (https://developer.android.com/training/wearables/apps/standalone-apps, https://developer.android.com/training/wearables/packaging).
- Listado: track dedicado "Wear OS" (Test and release → Advanced settings → Form factors → Wear OS), al menos 1 screenshot 1:1 del reloj, mencionar "Wear OS" en la ficha, mismo package name/firma si existe app de móvil, versionCode independiente (https://developer.android.com/training/wearables/packaging; https://support.google.com/googleplay/android-developer/answer/13295490).
- Wear app quality: WO-V2 touch targets ≥48dp, WO-V13 fondo negro, WO-V14 texto ≥12sp, WO-V15 splash icono 48dp, WO-V10 preview de tile, WO-P6 "Must NOT ask for username/password input directly on Wear OS device", WO-V4 OngoingActivity/Live Update para tareas largas (https://developer.android.com/docs/quality-guidelines/wear-app-quality).

## 2. Operación standalone, red y trabajo en segundo plano

- Red: "When a watch has a Bluetooth connection to a phone, the watch's network traffic is generally proxied through the phone. When a phone is unavailable, Wi-Fi and cellular networks are used, depending on the watch hardware. The Wear OS platform handles transitions between networks." → un HTTPS normal con `HttpURLConnection` funciona sin código extra (https://developer.android.com/training/wearables/data/network-access).
- Soportado HTTP/TCP/UDP; `android.webkit` y `CookieManager` no están disponibles (misma fuente). Sobre BLE el ancho de banda puede ser ~4 KB/s (https://developer.android.com/training/wearables/apps/standalone-apps); `ConnectivityManager.requestNetwork(TRANSPORT_WIFI)` solo para transferencias grandes y liberar el callback después (network-access).
- Data Layer (`DataClient`/`MessageClient`) solo entre reloj y teléfono Android con la misma app; no sirve como vía a Internet (https://developer.android.com/training/wearables/data/data-layer).
- Coste energético relativo (doc oficial): network LTE/Wi-Fi "Very High", pantalla "High", Bluetooth "Medium", wakelocks "Medium"; "Defer non-essential network access until the device is charging" (https://developer.android.com/training/wearables/apps/power).

Trabajo en background (reglas de plataforma; Wear OS no documenta intervalos propios, sin confirmar diferencias):

| Mecanismo | Límite | Fuente |
|---|---|---|
| `WorkManager` periódico | mínimo 15 min; respeta Doze/App Standby; puede retrasarse o saltarse | https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work |
| Alarmas exactas | `SCHEDULE_EXACT_ALARM` denegada por defecto en Android 14+ (Wear OS 5+) | https://developer.android.com/training/wearables/versions/5/changes |
| Alarmas inexactas | ventana mínima 10 min (`setWindow`), `set()` dentro de 1 h en Android 12+ | https://developer.android.com/develop/background-work/services/alarms/schedule |
| Doze / Standby Buckets | plataforma Android (red y jobs diferidos); Android 14 añade causas extra para el bucket "restricted" | https://developer.android.com/training/monitoring-device-state/doze-standby ; https://developer.android.com/topic/performance/appstandby |
| Tiles/complications | "Disable automatic refresh, or increase the refresh rate to 2 hours or longer"; no programar trabajo si el usuario no interactúa | https://developer.android.com/training/wearables/apps/power |

- Bedtime mode (Pixel Watch): apaga pantalla, tilt-to-wake, táctil y silencia notificaciones salvo alarmas/prioritarias; es ajuste de usuario, sin API para apps (https://support.google.com/googlepixelwatch/answer/13532591). Wear OS 5+ manda las apps always-on a background tras un timeout configurable (https://developer.android.com/training/wearables/versions/5/changes).
- Implicación: no hacer polling. Refrescar al abrir la app / al tocar el tile, y usar FCM (funciona directo en el reloj y convive con Doze) para cambios de estado push (network-access).

## 3. Frameworks de UI

| Opción | Versión estable | Tamaño artefacto (AAR, pre-R8) | Qué aporta | Coste / riesgo |
|---|---|---|---|---|
| (a) Compose Material 3 `androidx.wear.compose:compose-material3` | 1.7.0 (23-sep-2026); primera estable 1.5.0 (27-ago-2025) | 2.135 KB + `compose-foundation` 944 KB + runtime Compose (`androidx.compose.ui` 1.12.x) | `AppScaffold`/`ScreenScaffold`, `TransformingLazyColumn`, `EdgeButton`, dynamic color (Wear OS 6+), `SwipeToReveal`, `ConfirmationDialog`/`AlertDialog`/`OpenOnPhoneDialog`, `Picker`/`TimePicker`, `oneHandedGesture` (1.7); ships baseline profiles | Mayor APK/RAM que Views; requiere Compose ≥1.8; M3 Expressive solo luce en Wear OS 6+ |
| (b) Compose Material 2.5 `compose-material` | 1.7.0 | 870 KB (+foundation) | `ScalingLazyColumn`, `Scaffold`, `Chip` | "superseded by compose-material3"; sin nuevas features |
| (c) Views `androidx.wear:wear` | 1.4.0 (feb-2026) | 319 KB (sin Compose runtime) | `WearableRecyclerView`, `DismissibleFrameLayout` (sustituye `SwipeDismissFrameLayout`), `ConfirmationOverlay`, `ArcLayout`/`CurvedText`, `AmbientLifecycleObserver` | Sin M3 Expressive ni dynamic color; XML/Views; menor mantenimiento futuro |
| (d) Horologist | 0.8.4-alpha (10-ago-2026); 0.7.x estable para Wear Compose 1.5 | según módulo | `auth` (OAuth/PKCE screens), `datalayer`, `tiles` (coroutines `TileService`), media | Google: "You should no longer use the Horologist Composables, Compose Layout, or Compose Material libraries. Instead, use the components in M3." |

Fuentes: versiones y notas (https://developer.android.com/jetpack/androidx/releases/wear-compose; https://developer.android.com/jetpack/androidx/releases/wear-compose-m3; https://developer.android.com/jetpack/androidx/releases/wear); migración/Horologist (https://developer.android.com/training/wearables/compose/migrate-to-material3); Horologist releases/README (https://github.com/google/horologist/releases; https://github.com/google/horologist); tamaños medidos en Maven (dl.google.com/android/maven2, 2026-10-01).

Mediciones publicadas (no hay cifras oficiales específicas de Wear; sin confirmar para reloj):
- Sunflower (móvil, release con R8 full mode): Views-only 2.252 KB → mixto 3.034 KB → Compose-only 2.966 KB (https://developer.android.com/develop/ui/compose/migrate/compare-metrics). Orden de magnitud esperable en Wear: ~+0,7–1 MB de descarga por el runtime Compose.
- Baseline Profiles: "~30% faster code execution from first launch"; cada librería Compose for Wear OS incluye sus reglas y se fusionan en el APK; un Startup Profile "will increase the APK size" (https://android-developers.googleblog.com/2022/01/improving-app-performance-with-baseline.html; https://developer.android.com/training/wearables/compose/performance).
- R8: recomendado siempre; verificar `adb shell dumpsys package dexopt | grep -A1 <pkg>` → `status=speed-profile` (perf page). RAM: ninguna cifra publicada Compose vs Views en reloj (sin confirmar); medir con `dumpsys meminfo`.
- Nota de alcance: para 2 pantallas (estado + climate) la diferencia Views/Compose es ~1 MB de APK y algo de RAM; a cambio Compose M3 da gratis rotary, scaffold, dialogs y M3 Expressive. Views es la opción si el objetivo es el APK mínimo absoluto.

## 4. Tiles

- Librerías: `androidx.wear.tiles:tiles 1.6.2` + `androidx.wear.protolayout:protolayout / protolayout-material3 / protolayout-expression 1.4.2` (29-jul-2026). 1.6.0: `Material3TileService` (un `suspend fun tileResponse()` devuelve layout + recursos), recursos inline vía `ProtoLayoutScope` (elimina un binder call), `tileId` en `requestUpdate`, `METADATA_GROUP_KEY`, minSdk 23, compileSdk 35 (https://developer.android.com/jetpack/androidx/releases/wear-tiles; https://developer.android.com/jetpack/androidx/releases/wear-protolayout).
- Render: el tile es un árbol ProtoLayout declarativo que renderiza el sistema; profundidad máxima 30 nodos; desde Wear OS 6 todos los tiles usan la fuente del sistema. `protolayout-material3` trae `primaryLayout` (3 slots), `textEdgeButton`/`iconEdgeButton`, `iconDataCard`, `circularProgressIndicator`, dynamic color del watch face, Lottie, gradientes (mismas fuentes).
- Actualización: `setFreshnessIntervalMillis()` → "the system calls onTileRequest() shortly after the interval finishes. If you don't set a freshness interval, the system doesn't call onTileRequest()". Push: `TileService.getUpdater(ctx).requestUpdate(MyTileService::class.java)` (https://developer.android.com/training/wearables/tiles/update). Datos dinámicos (`DynamicBuilders`, platform data a 1 Hz) solo para lo que ya está en el reloj; hay un límite no numerado de expresiones por tile (https://developer.android.com/training/wearables/tiles/dynamic). Límite numérico de `requestUpdate` no documentado (sin confirmar); la guía de energía pide refresco automático ≥2 h y ningún trabajo si el tile no se usa (power page).
- Clicks: `launchAction(ComponentName, extras)` abre una Activity; `loadAction()` re-invoca `onTileRequest` y permite leer `requestParams.currentState.lastClickableId` y un `stateMap` propio; ProtoLayout 1.4 añade `Clickable` con `PendingIntent` y fallback a Load/LaunchAction (https://developer.android.com/training/wearables/tiles/interactions; release notes protolayout 1.4.0).
- ¿Llamada de red dentro de `onTileRequest`? Posible (`Material3TileService` es `suspend`), pero "the returned future must complete after at most 10 seconds from the moment this method is called (exact timeout length subject to change)" (https://developer.android.com/reference/androidx/wear/tiles/TileService) y la guía dice no hacer fetch frecuente ni tareas largas en el servicio; patrón oficial: mostrar valor cacheado, lanzar sync (WorkManager/FCM) y `requestUpdate` al llegar, o esperar 1-2 s y caer al cache (https://developer.android.com/training/wearables/tiles; tiles/update). Para "lock" desde el tile: `loadAction(id="lock")` → `onTileRequest` dispara la orden en ≤10 s y devuelve layout "sending…"; el resultado llega con `requestUpdate`.
- Threads/lifecycle: `Service` y `TileService` callbacks van en hilos asíncronos distintos; `onTileAddEvent`/`onTileRemoveEvent` para activar/desactivar trabajo; en Wear OS 6 los enter/leave llegan en lote via `onRecentInteractionEventsAsync()` (https://developer.android.com/training/wearables/tiles/lifecycle; versions/6/changes).
- Preview: `tiles-tooling-preview` + `@Preview` con `TilePreviewData` (platform data overridable desde 1.4); Play exige preview en el tile manager (WO-V10) (wear-tiles releases; quality guidelines).
- Multi-instancia: `tileId` en eventos/requests desde 1.3.0, `getActiveTilesAsync()`, y en 1.6 `requestUpdate` por `tileId` (wear-tiles releases).
- Futuro: Wear OS 7 introduce "Wear Widgets" (Jetpack Glance + RemoteCompose); Google "will continue to support our Protolayout and Tiles libraries for some time" (https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html). Tiles sigue siendo la vía con cobertura Wear OS 3–7.

## 5. Complications

- Librería: `androidx.wear.watchface:watchface-complications-data-source-ktx 1.3.0` (25-feb-2026). Las APIs de render de watch face están deprecated a favor de Watch Face Format, pero "Complication APIs are NOT deprecated" (https://developer.android.com/jetpack/androidx/releases/wear-watchface).
- Tipos: `SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`, `GOAL_PROGRESS`, `SMALL_IMAGE`, `MONOCHROMATIC_IMAGE`, `PHOTO_IMAGE`, `WEIGHTED_ELEMENTS`; declarar varios en `SUPPORTED_TYPES` para encajar en más watch faces (https://developer.android.com/training/wearables/complications/exposing-data; https://android-developers.googleblog.com/2025/08/building-complication-data-sources-wear-os.html).
- Frecuencia: `UPDATE_PERIOD_SECONDS` "at least 300 (5 minutes), which is the minimum update period that the system enforces", o `0` para push. Push: `ComplicationDataSourceUpdateRequester.requestUpdate()`, "don't call requestUpdate() … more often than every 5 minutes on average" (exposing-data).
- Servicio: `SuspendingComplicationDataSourceService.onComplicationRequest()` se llama al activarse y cada periodo; `onComplicationActivated/Deactivated` para encender/apagar el trabajo; `getPreviewData()` obligatorio; 1.3.0 permite preview estática en manifest (exposing-data; wear-watchface 1.3.0).
- Tap: `setTapAction(PendingIntent)` en cada tipo (p. ej. abrir la app en "lock"). Valores relativos a tiempo (`TimeDifferenceComplicationText`) y timelines evitan actualizaciones; dynamic values (Wear OS 4+) con fallback (exposing-data).
- Watch Face Format: los watch faces WFF consumen las mismas complications (timeline y dynamic values incluidos); desde ene-2026 WFF es obligatorio para instalar watch faces (exposing-data; wear-watchface releases).
- Ejemplo para el coche: `RANGED_VALUE`/`GOAL_PROGRESS` = range o battery, `SHORT_TEXT` = "Locked"/"Unlocked", `MONOCHROMATIC_IMAGE` = icono candado; `UPDATE_PERIOD_SECONDS=0` + push cuando la app o FCM reciben estado nuevo.

## 6. Autenticación y seguridad en el reloj

- WebView: no existe; "android.webkit APIs … are not available" (https://developer.android.com/training/wearables/data/network-access). Además Play prohíbe pedir usuario/contraseña en el reloj (WO-P6, quality guidelines).

| Opción | Librería | Requisitos / límites | Fuente |
|---|---|---|---|
| Credential Manager (passkeys, passwords, Sign in with Google) | `androidx.credentials:credentials 1.6.0` | "available on Wear OS 3 and higher"; passkeys/Credential Manager de sistema desde Pixel Watch con Wear OS 5.1; "Credentials cannot be created on Wear OS"; sin restore ni hybrid; targetSdk ≥35 para passkeys; capturar `NoCredentialException`/`GetCredentialCancellationException` y caer a otro método | https://developer.android.com/training/wearables/apps/auth-wear ; https://android-developers.googleblog.com/2025/05/whats-new-in-wear-os-6.html |
| OAuth 2.0 PKCE vía teléfono ("sign in with your phone") | `androidx.wear:wear-remote-interactions 1.2.0` `RemoteAuthClient.sendAuthorizationRequest()` | Abre el navegador del teléfono; redirect `https://wear.googleapis.com/3p_auth/<package>`; necesita teléfono emparejado (Android o iOS) | auth-wear |
| Device Authorization Grant | `RemoteActivityHelper.startRemoteActivity(ACTION_VIEW uri)`; 1.3.0-beta01 añade `startRemoteActivityAttemptUnlock` | Usuario introduce código en el teléfono | auth-wear ; https://developer.android.com/jetpack/androidx/releases/wear |
| Token por Data Layer | `play-services-wearable` (`MessageClient`/`DataClient`) | Solo teléfono Android con la app companion instalada; canal BT "single encrypted channel… standard Bluetooth encryption" y cloud "end-to-end encrypted"; package name y firma deben coincidir | https://developer.android.com/training/wearables/data/overview ; auth-wear |
| Teclado del reloj | IME del sistema (Gboard: QWERTY, gestos, voz, emoji) vía `RemoteInput`/EditText | Entrada de una sola línea; Compose for Wear OS no incluye `TextField` (usar `RemoteInput` o `BasicTextField` de foundation, sin confirmar soporte oficial); email/OTP es viable técnicamente, contraseña está vetada por WO-P6 | https://developer.android.com/training/wearables/user-input/wear-ime ; quality guidelines |

- Recomendación para esta app: OAuth PKCE con `RemoteAuthClient` (sin companion app) + refresh token en el reloj; opcional Credential Manager si el backend soporta passkeys. Evitar formularios en el reloj.

Secure storage:
- `androidx.security:security-crypto 1.1.0`: "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore" (`EncryptedSharedPreferences`, `EncryptedFile`, `MasterKeys`) (https://developer.android.com/jetpack/androidx/releases/security).
- Reemplazo: clave AES-GCM en `AndroidKeyStore` (`KeyGenParameterSpec`, opcional `setUserAuthenticationRequired`) y ciphertext en `SharedPreferences`/DataStore; el sandbox + file-based encryption ya cifran en reposo (https://developer.android.com/privacy-and-security/keystore). StrongBox en relojes: sin confirmar.

Gating por desbloqueo:
- `KeyguardManager.isDeviceSecure()` (hay PIN) e `isDeviceLocked()` (está bloqueado ahora) son APIs de plataforma disponibles en Wear (https://developer.android.com/reference/android/app/KeyguardManager). Doc oficial de Wear: en Wear OS 5+ si el usuario desactiva wrist detection el reloj permanece desbloqueado más tiempo; comprobar `isDeviceSecure` + ajuste OEM `Settings.Global` (`PIXEL_WRIST_AUTOLOCK_SETTING_STATE` en Pixel) antes de mostrar datos sensibles (https://developer.android.com/training/wearables/apps/auth-wear; https://developer.android.com/training/wearables/versions/5/changes).
- Biometría: los relojes no tienen sensor biométrico (sin confirmar como regla general); `androidx.biometric` 1.4.0-alpha04 (may-2025) "Always use KeyguardManager API internally for Wear apps" → `BiometricPrompt` con `DEVICE_CREDENTIAL` muestra el PIN del reloj; estable sigue en 1.1.0 (2021) (https://developer.android.com/jetpack/androidx/releases/biometric). Alternativa sin librería: `KeyguardManager.requestDismissKeyguard()` (comportamiento en Wear sin confirmar) o clave Keystore con `setUserAuthenticationRequired(true)` + `BiometricPrompt(DEVICE_CREDENTIAL)` → el sistema fuerza PIN si está bloqueado.
- UX de confirmación: M3 `AlertDialog` (confirmar/cancelar), `ConfirmationDialog`/`SuccessConfirmationDialog`/`FailureConfirmationDialog` (feedback transitorio), `OpenOnPhoneDialog`; `SwipeToReveal` para acciones secundarias; Views `ConfirmationOverlay` (https://developer.android.com/reference/kotlin/androidx/wear/compose/material3/package-summary; https://developer.android.com/jetpack/androidx/releases/wear). "Swipe-to-confirm" y long-press como patrón oficial de confirmación: no existen en AndroidX (sin confirmar en guías de diseño). Patrón sugerido: unlock → `AlertDialog` → `SuccessConfirmationDialog` + haptic `CONFIRM`.

## 7. Red y JSON: coste de librerías

Tamaños de artefacto (jar/aar, comprimidos, antes de R8; medidos 2026-10-01 en Maven Central). No son tamaño en APK: R8 elimina gran parte, pero el orden de magnitud se mantiene. Cifras publicadas de APK/method count por librería en Wear: no encontradas (sin confirmar).

| Librería | Versión estable | KB | Nota |
|---|---|---|---|
| `HttpURLConnection` + `org.json` | plataforma | 0 | TLS, HTTP/1.1, gzip; suficiente para 5 endpoints REST |
| `okhttp-jvm` + `okio-jvm` | 5.5.0 / 3.18.2 | 939 + 383 | HTTP/2, pooling, interceptors |
| Retrofit | 3.0.0 | 128 (+OkHttp) | Reflexión + converters |
| Ktor client (`core`+`http`+`io`+`utils`+`cio`) | 3.6.0 | 941+448+296+388+135 | Más pesado; engine `android` usa `HttpURLConnection` |
| `kotlinx-serialization-json` + `core` | 1.11.0 | 287 + 395 | Sin reflexión; plugin de compilador |
| Moshi | 1.15.2 | 158 (+okio 383) | Reflexión o codegen |
| `kotlinx-coroutines-core` + `-android` | 1.11.0 | 1.540 + 17 | Ya es dependencia transitiva de Compose: coste 0 adicional si se usa Compose |

Fuentes: índices Maven (https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-jvm/, https://repo1.maven.org/maven2/io/ktor/, https://repo1.maven.org/maven2/org/jetbrains/kotlinx/). Recomendación: `HttpURLConnection` + `org.json` + coroutines (`Dispatchers.IO`); añadir OkHttp solo si el backend exige HTTP/2, certificate pinning cómodo o websockets.

## 8. Haptics, rotary, OngoingActivity, ambient

- Haptics: `View.performHapticFeedback(HapticFeedbackConstants.CONFIRM/REJECT)` (API 30+) o `Vibrator.vibrate(VibrationEffect.createPredefined(EFFECT_CLICK))` (API 29+); evitar `createOneShot`; comprobar `arePrimitivesSupported()` para composiciones (https://developer.android.com/develop/ui/views/haptics/haptics-apis; https://developer.android.com/develop/ui/views/haptics/haptics-principles). En Compose: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.Confirm)` (constantes ampliadas en Compose UI 1.8, sin confirmar lista exacta). El motor del reloj se siente distinto: probar en dispositivo (haptics-principles).
- Rotary: `ScalingLazyColumn`, `TransformingLazyColumn` y `Picker` "support the scroll gesture by default, if you place these components inside AppScaffold and ScreenScaffold"; para custom, `Modifier.onRotaryScrollEvent {}` + `focusable()`; `Modifier.rotaryScrollable` con `RotaryScrollableDefaults.behavior/snapBehavior(snapSensitivity)` en foundation 1.6 (https://developer.android.com/training/wearables/user-input/rotary-input; wear-compose 1.6.0-alpha08 notes). Temperatura: `Picker` M3 con pasos de 0,5 °C y snap.
- OngoingActivity: `androidx.wear:wear-ongoing 1.1.0`; exige notificación `setOngoing(true)` + foreground service; icono estático obligatorio; aparece en watch face y Recents; pensado para tareas ">1 minute". En Wear OS 7 Google recomienda Live Updates ("use Live Updates instead of the Ongoing Activities API"), misma API que móvil, `MetricStyle` no soportado (https://developer.android.com/training/wearables/notifications/ongoing-activity; https://developer.android.com/training/wearables/notifications/live-updates; blog Wear OS 7). Para un lock/unlock de pocos segundos no compensa: indicador in-app + `ConfirmationDialog`. Solo "climate on" prolongado justificaría un OngoingActivity.
- Ambient: en Wear OS 6+ (targetSdk 36) la app queda "resumed" y atenuada, actualizaciones ~1/min; antes se mostraba una captura borrosa. Compose: `LocalAmbientModeManager` + `AmbientTickEffect` (wear-compose 1.6); Views: `AmbientLifecycleObserver`. Reglas: ≥85 % de píxeles negros, sin animaciones, placeholders `--`, mostrar la hora (`TimeText` lo hace). No usar `keepScreenOn` (https://developer.android.com/training/wearables/views/always-on; versions/6/changes). Esta app no necesita always-on.

## 9. Herramientas de medición

| Métrica | Herramienta / comando | Nota | Fuente |
|---|---|---|---|
| Tamaño APK/AAB | `apkanalyzer apk file-size\|download-size app.apk`, `apkanalyzer dex packages` | CLI en `cmdline-tools` | https://developer.android.com/tools/apkanalyzer |
| Tamaño instalado | `adb shell dumpsys package <pkg>` (codePath/dataDir) + `adb shell du -sh`; Play Console → Android vitals → App size (download/install) | Play solo tras subir AAB | https://developer.android.com/tools/dumpsys ; https://support.google.com/googleplay/android-developer/answer/9859372 |
| RAM | `adb shell dumpsys meminfo <pkg>` (PSS/RSS); Android Studio Memory Profiler | medir en release | https://developer.android.com/tools/dumpsys |
| Batería | `adb shell dumpsys batterystats --reset` → uso → `dumpsys batterystats --charged <pkg>`; Perfetto (`ui.perfetto.dev`, data source battery/power rails); Android Studio Power Profiler | Battery Historian archivado el 29-dic-2022 (read-only) | https://developer.android.com/training/wearables/apps/power ; https://github.com/google/battery-historian |
| Startup | `adb shell am start -S -W <pkg>/.MainActivity` → `ThisTime/TotalTime/WaitTime`; logcat `Displayed`; `ReportDrawn()` en Compose; Macrobenchmark | Validar `dumpsys package dexopt` → `speed-profile` | https://developer.android.com/topic/performance/vitals/launch-time ; compose/performance |
| Red | `adb shell dumpsys netstats detail` (por UID, `set=DEFAULT/BACKGROUND`); Network Inspector de Studio (HttpURLConnection/OkHttp) | sigue documentado en Android 14+ | https://developer.android.com/tools/dumpsys ; https://developer.android.com/studio/debug/network-profiler |
| Sensores/wakelocks | `dumpsys sensorservice`, `dumpsys activity service WearableService` | para verificar que no queda trabajo tras swipe-dismiss | power page |

Emulador (verificado con `sdkmanager --list`, SDK local, 2026-10-01): `android-30;android-wear` (Wear OS 3, x86 y arm64), `android-33;android-wear` (Wear OS 4, x86_64/arm64), `android-34;android-wear` (5), `android-35-ext15;android-wear` (5.1), `android-36;android-wear-signed` (6.0), `android-36.1;android-wear-signed` (6.1), `android-37.0;android-wear-signed` (7.0); legados `android-25/26/28`. Las imágenes "signed" (6+) no tienen root (https://developer.android.com/training/wearables/versions/6/setup; /versions/7/setup). Perfiles AVD (sdklib `wear.xml`): Large Round 454×454, Small Round 384×384, XL Round 480×480 (API 33+), Rectangular 402×476, Square 360×360, todos con 512 MiB de RAM del guest. Requisito de RAM del host: no documentado específicamente para Wear (sin confirmar; en la práctica el emulador consume ~1–2 GB).

## 10. Baseline de proyecto (oct-2026)

| Componente | Versión | Fuente |
|---|---|---|
| Kotlin | 2.4.20 (7-sep-2026) | https://kotlinlang.org/docs/releases.html |
| AGP | 9.4.x (sep-2026): Gradle ≥ 9.6, JDK 17, Build Tools 36.0.0, API máx 37; AGP 10 hará obligatoria la nueva Variant API | https://developer.android.com/build/releases/gradle-plugin |
| Gradle | 9.8.0 (24-sep-2026) | https://gradle.org/releases/ |
| Android Studio | Quail 4 (2026.1.4) | https://developer.android.com/studio/releases |
| compileSdk / targetSdk | 37 (guía Wear OS 7); Play exige ≥35 en Wear | https://developer.android.com/training/wearables/versions/7/setup |
| minSdk | 30 (ver §1) | — |
| Compose | `androidx.compose.ui` 1.12.x (BOM 2026.09); Wear Compose M3 1.7.0 + foundation 1.7.0 | wear-compose releases; índice Maven |
| Tiles / ProtoLayout | 1.6.2 / 1.4.2 | wear-tiles releases |
| Complications | watchface-complications-data-source-ktx 1.3.0 | wear-watchface releases |
| Opcionales | credentials 1.6.0, wear-remote-interactions 1.2.0, work-runtime 2.12.0, datastore-preferences 1.2.1 | índices Maven |

- Referencia oficial: `ComposeStarter` usa AGP 9.1.0, Kotlin 2.3.20, Compose BOM 2026.03.01, Wear Compose 1.6.0, `isMinifyEnabled = true` + `isShrinkResources = true` (https://github.com/android/wear-os-samples/blob/main/ComposeStarter/gradle/libs.versions.toml).
- ¿Android Studio obligatorio? No: con JDK 17+, `cmdline-tools` (`sdkmanager` para `platforms;android-37`, `build-tools;36.0.0`, `emulator`, imagen `system-images;android-36;android-wear-signed;x86_64`) y el Gradle wrapper se compila, firma e instala desde CLI (https://developer.android.com/build/building-cmdline). Studio solo aporta `@Preview` de Compose/Tiles, Device Manager GUI y profilers; Perfetto UI y `apkanalyzer` funcionan sin él. En esta máquina ya hay SDK en `%LOCALAPPDATA%\Android\Sdk` (platform `android-37.0`, build-tools 36.0.0, emulator 37.1.11, JDK 21 en `C:\Users\Poiki\tools\jdk-21.0.11+10`); faltan por instalar las imágenes Wear.

## 11. Recomendación resumida

1. Standalone (`standalone=true`), minSdk 30, target/compileSdk 37, Kotlin 2.4.20 + AGP 9.4.x, R8 + shrinkResources, sin código nativo.
2. UI: Compose Material 3 1.7.0 (dos pantallas: estado con `TransformingLazyColumn` + `EdgeButton`; climate con `Picker`). Views solo si el APK mínimo absoluto pesa más que el coste de mantenimiento.
3. Red: `HttpURLConnection` + `org.json` + coroutines; sin polling; FCM para push de estado; `WorkManager` solo para sync al cargar.
4. Tile M3 (`Material3TileService`) con estado cacheado, `loadAction` para lock/unlock/refresh y `requestUpdate` al llegar la respuesta; `freshnessInterval` ≥ 2 h o ninguno. Complication push (`UPDATE_PERIOD_SECONDS=0`, tipos RANGED_VALUE/SHORT_TEXT/MONOCHROMATIC_IMAGE).
5. Auth: OAuth PKCE vía `RemoteAuthClient` (+ Credential Manager si hay passkeys); tokens cifrados con clave `AndroidKeyStore`; acciones sensibles tras `isDeviceSecure()` + `BiometricPrompt(DEVICE_CREDENTIAL)` (androidx.biometric 1.4 alpha) o `isDeviceLocked()`; confirmación con `AlertDialog` + `SuccessConfirmationDialog` + haptic CONFIRM.
6. Medir desde el día 1: `apkanalyzer download-size`, `dumpsys meminfo`, `am start -W`, `dumpsys batterystats` + Perfetto, en emulador `android-36` y en reloj físico.
