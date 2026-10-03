# Decisiones técnicas (2026-10-01)

Destilado de `toyota-api.md`, `toyota-auth.md`, `wearos-analysis.md`, `oem-wear-apps.md`, `emulator.md` y `mytoyota-emulator-compatibility.md`. Cada decisión es provisional hasta que se mida (§9). Formato: decisión → alternativas → por qué.

## 1. Comunicación con Toyota: acceso directo desde el reloj a la API no oficial de MyToyota EU

- Alternativas: (a) API oficial: **no existe** para particulares (TCEU sin portal; EDA/Data Act solo exporta históricos; High Mobility/Smartcar son B2B y sin Toyota EU). (b) Intermediario propio (backend que habla con Toyota y expone una API simple al reloj). (c) Acceso directo desde el reloj, como hacen pytoyoda, ha_toyota, openHAB, evcc e ioBroker.
- Elegida: (c). Sin servidor: cero infraestructura, cero coste, los tokens solo viven en el reloj, misma superficie que cinco clientes comunitarios activos. Un backend propio añadiría un punto de fallo y custodia de credenciales ajenas sin ahorrar peticiones (Toyota no ofrece push a terceros).
- Coste asumido: la API cambia sin aviso (jun–sep 2026 rompió a todos los clientes). Mitigación: rutas en un único fichero de constantes, seguir pytoyoda como referencia viva y publicar actualizaciones (desde 0.2.0 el reloj se actualiza solo desde GitHub, §8). Las credenciales estáticas del cliente `oneapp` (API key, Basic auth) son públicas en GitHub pero no se copian a la documentación.

## 2. Autenticación: login en un componente de teléfono mínimo, tokens al reloj una sola vez

- Hechos: el login es ForgeRock por JSON (3 llamadas HTTPS, sin WebView, sin CAPTCHA, hoy sin OTP). El refresh token rota y **no requiere contraseña**; sin device binding; vida desconocida. `RemoteAuthClient` (OAuth vía navegador del móvil) **no sirve**: Toyota solo acepta el redirect `com.toyota.oneapp:/oauth2Callback`. Credential Manager: Toyota no tiene passkeys. Play prohíbe pedir usuario/contraseña en el reloj (WO-P6).
- Elegida: dos vías equivalentes. (a) App Android de teléfono de **una pantalla** (email, contraseña, marca) que ejecuta las 3 llamadas y envía `refresh_token + access_token + expires_at + uuid` al reloj por `MessageClient` (canal Bluetooth cifrado, misma firma de app); el teléfono **no guarda nada**. (b) A petición del usuario (2026-10-01), **login directo en el reloj** con el teclado del sistema, para que la app sea autónoma también al vincular; esta vía impide publicar en Google Play (WO-P6), aceptado porque la distribución es por instalación manual. En ambos casos el reloj refresca solo; si el refresh falla, pide iniciar sesión de nuevo.
- Contraseña guardada (0.2.0, 2026-10-03, decisión del usuario): interruptor "Guardar contraseña", **desactivado por defecto**, en los dos logins. Si se activa, el reloj la guarda cifrada con una clave propia (ver §8) y solo la lee cuando Toyota rechaza el refresh token, para iniciar sesión de nuevo sin teclado; si Toyota la rechaza, se borra. Los errores transitorios del login (429/5xx) son `ToyotaError`, no `ToyotaLoginError`: nunca borran la contraseña ni la sesión.
- Descartadas: guardar la contraseña por defecto (secreto de alto valor en un wearable); backend intermediario (§1); `RemoteAuthClient` (redirect fijo de Toyota).

## 3. Cliente Wear OS: Kotlin + Compose for Wear OS Material 3, standalone

- Alternativas: Compose M3 1.7 (estable; ~+0,7–1 MB de APK por el runtime), Compose M2.5 (superseded), Views `androidx.wear` 1.4 (319 KB, sin M3 Expressive ni rotary/scaffold gratis), Horologist (Google pide no usarlo para UI).
- Elegida: Compose M3. Da `ScreenScaffold`/`TimeText`, `TransformingLazyColumn`, `EdgeButton`, `Picker` con corona, `AlertDialog`/`ConfirmationDialog` y baseline profiles incluidos; es la única opción con futuro. El coste (~1 MB APK, RAM extra) se mide en §9; si rompe el presupuesto se recorta a Views.
- Parámetros: minSdk 30 (Wear OS 3+), compileSdk/targetSdk 37, Kotlin 2.4, AGP 9.4, R8 + shrinkResources, sin código nativo, `standalone=true`, `uses-feature watch`. Sin Android Studio: SDK + Gradle wrapper desde CLI.

## 4. Red y JSON: `HttpURLConnection` + `org.json` + coroutines

- Alternativas: OkHttp (+1,3 MB AAR), Ktor (+2,2 MB), Retrofit, kotlinx.serialization/Moshi.
- Elegida: plataforma pura. Son ~8 endpoints REST JSON; TLS/gzip/HTTP 1.1 bastan. Coroutines ya vienen con Compose. Reglas heredadas de pytoyoda: peticiones **en serie**, backoff 2/4/8 s en 429/5xx, `followRedirects=false` en el `authorize`, cabeceras idénticas a pytoyoda (`x-appversion`, `x-channel`, `x-client-ref`, `x-correlationid`, `x-region`, `x-user-region`, `x-brand`, `x-guid`).

## 5. Almacenamiento: un fichero, sin base de datos

- Contenido: tokens cifrados con clave AES-GCM de `AndroidKeyStore` (Jetpack `security-crypto` está deprecado), `uuid`, VIN seleccionado, lista corta de vehículos (vin, alias, modelo, capabilities) y el **último snapshot** de estado (JSON de pocos KB con timestamps). Nada más: ni trips, ni historial, ni logs en disco.
- Por qué: el snapshot permite pintar app, tile y complication al instante y distinguir "última información conocida" de "actual". Todo lo demás se pide bajo demanda.
- 0.2.1 (petición del usuario): la app **no muestra un snapshot de más de 2 minutos** al abrir un coche; muestra un anillo de carga hasta que Toyota responde, para no enseñar un estado falso. El snapshot guardado solo sirve para comparar el timestamp tras un wake y para abrir al instante un coche leído hace un momento.

## 6. Sincronización: bajo demanda, nunca polling de fondo

- Al abrir la app: GET `vehicle/status`, `telemetry`, `electric/status` (si EV/PHEV), `location`, `climate-status`, en serie (≈5 peticiones, <10 s). Desde 0.2.1 se piden para el último coche mientras se ve el garaje (al tocarlo suele estar listo) y la esfera de estado aparece tras las dos primeras (cierre, combustible, kilometraje); posición y clima llegan después.
- Arranque (0.2.1, medido en OnePlus Watch 3): los tokens se descifran en la primera petición y no en `onCreate` (el Keystore costaba ~57 ms de los ~90 ms de `Store.init` en el hilo principal) y se desactiva `EmojiCompatInitializer` (la app no muestra emoji). Ver docs/measurements.md.
- "Actualizar": POST `/v1/remote/status` (wake) → GET status a los 5, 10, 20 y 30 s hasta que `lastUpdateTimestamp` avance; presupuesto 30 s; si no avanza, "El coche no responde" conservando el dato anterior. Nunca wakes periódicos: cada wake gasta batería 12 V del coche (hay reportes de baterías agotadas con polling).
- Tile: pinta el snapshot; botón "Actualizar" → `loadAction` con una sola GET dentro de los 10 s permitidos y `requestUpdate` al terminar; sin `freshnessInterval`. Complication: `UPDATE_PERIOD_SECONDS=0`, push desde la app tras cada refresco.
- Sin WorkManager periódico, sin FCM (no hay servidor), sin servicios residentes.

## 7. Comandos: enviado ≠ confirmado

- Toyota acusa recibo al instante (`returnCode 000000`) y **no hay endpoint para consultar la ejecución**. Confirmación: wake + GET status hasta que el `lastUpdateTimestamp` del componente sea posterior al envío (sondeo 5/15/30/60 s, máx. 60 s). Rechazos del coche (llave dentro, puerta abierta) se leen de `/v2/notification/history` tras un fallo.
- Estados internos: pendiente → enviado → confirmado | no confirmado | rechazado. UI: "Enviando…", "Verificando…", "Vehículo cerrado" / "Sin confirmar: comprueba el coche". Sin reintentos automáticos; botón bloqueado 10 s tras cada envío; háptico CONFIRM/REJECT.
- Clima: una sola llamada `POST /v2/remote/climate-control` con temperatura (18–29 °C), duración y opciones; nunca llamadas separadas por opción.

## 8. Seguridad y privacidad

- Apertura: diálogo de confirmación explícito y, si el reloj tiene bloqueo de pantalla, que esté desbloqueado en ese momento (`!isDeviceLocked`). El requisito de PIN (`isDeviceSecure`) se retiró el 2026-10-02 por decisión del usuario; el README avisa del riesgo (quien lleve un reloj desbloqueado puede abrir el coche). Nunca desde el Tile ni la complication. Cierre y clima: un toque sin confirmación, pero con verificación posterior. Replica el consenso OEM: barrera única, sin PIN por comando.
- Marca y nombre: el repositorio público no incluye el logotipo ni el nombre "MyToyota" (riesgo de marca); nombre "Wear for Toyota", icono neutro de Material Symbols y aviso de "no oficial" en el README.
- Gating por capabilities: `remoteDisplay == 7` + flags de `extendedCapabilities` + tolerancia a `CTP-REMOTE-40006` (ocultar el botón tras un rechazo definitivo). Los flags no son fiables; la prueba READ ONLY (Fase 2) fija el modelo real.
- Contraseña guardada: clave AES-256-GCM aparte en el Android Keystore (`toyota-credentials`), en StrongBox si el reloj lo tiene, con `setUnlockedDeviceRequired(true)` (si el reloj tiene bloqueo de pantalla, la clave no funciona mientras está bloqueado) y sin exigir autenticación por uso (el usuario no quiere PIN). Se descifra solo en el momento del re-login. Si el Keystore rechaza la clave (hay OEM con fallos en `UnlockedDeviceRequired`), no se guarda nada y se avisa: nunca hay una vía menos protegida. Sin copia útil fuera del reloj: la clave no sale del Keystore y las copias de seguridad están desactivadas.
- Actualizaciones (0.2.0): el reloj consulta `api.github.com/repos/Poiki/wear-for-toyota/releases/latest` como mucho una vez al día al abrirse; si hay versión mayor, ofrece instalarla. Primero pide el permiso "Instalar apps desconocidas" de la propia app (pantalla del sistema; si el reloj la oculta, `adb shell appops set com.poiki.toyotawear REQUEST_INSTALL_PACKAGES allow`), luego descarga el APK `wear-for-toyota-watch-*.apk` directamente a una sesión de `PackageInstaller` (`setAppPackageName`) y el instalador del sistema pide confirmación. Android solo acepta el mismo paquete, firmado con la misma clave y con `versionCode` mayor o igual: sin verificación de hash propia porque la firma ya la cubre. El estado vuelve por un `BroadcastReceiver` no exportado (nadie más puede inyectarle intents). Probado en el emulador Wear OS 5 hasta el instalador del sistema; la app del móvil no se autoactualiza (se instala con un toque desde la release).
- Privacidad: VIN, posición y km solo en el snapshot local; sin analítica, sin logs persistentes; "Desvincular" borra tokens, contraseña y caché (no hay endpoint de revocación confirmado; se recomienda cambiar la contraseña si se pierde el reloj).
- Errores al usuario: traducción de códigos (`APIGW-403`, 429, `ONE-GLOBAL-RS-10068`, `CTP-REMOTE-*`) a frases; el código técnico solo en una pantalla de detalle.

## 9. Presupuestos a medir desde el primer build

| Métrica | Herramienta | Presupuesto |
|---|---|---|
| APK descarga (reloj) | `apkanalyzer apk download-size` | ≤ 4 MB |
| RAM en uso (PSS) | `dumpsys meminfo` | ≤ 60 MB en pantalla principal |
| Arranque en frío | `am start -W` | ≤ 1,0 s en emulador Wear OS 5 |
| Peticiones por apertura / por comando | logging de depuración | ≤ 5 / ≤ 6 |
| Tráfico por apertura | `dumpsys netstats` | ≤ 60 KB |
| Consumo en reposo | `dumpsys batterystats` | 0 wakeups propios |

Si Compose supera APK o RAM en más de un 50 %, se repite la medición con Views antes de seguir.

## 10. Entorno y orden de trabajo

- MyToyota **no se puede analizar en emulador** (dispositivo no certificado): análisis en un teléfono físico estándar por ADB. El AVD `mytoyota` se conserva para la app de teléfono.
- Fase 2 (READ ONLY) con `probe/` sobre pytoyoda desde el PC, antes de escribir código Kotlin: fija datos reales, timestamps y capabilities.
- Emulador Wear para el MVP: `system-images;android-34;android-wear;x86_64` (Wear OS 5, 512 MB de RAM en el guest) como objetivo de medición; validación final en reloj físico.

## 11. Lo que se deja fuera deliberadamente

Trips e historial, mantenimiento, notificaciones push, geocerca, Digital Key, voz, always-on, múltiples cuentas, backend propio, base de datos local, analítica. Se revisará solo si una medición o el uso real lo pide.
