# Compatibilidad de MyToyota con el emulador (AVD `mytoyota`)

Fecha: 2026-10-01. Pregunta: ¿por qué MyToyota 2.25.2 muestra "The Toyota app does not support rooted devices." en el AVD, y lo evitaría otra configuración oficial del Android Emulator?

## Resultado en tres líneas

- El AVD **no** es un dispositivo de desarrollo: imagen oficial Google Play, build `user` firmada con `release-keys`, sin root, SELinux enforcing, particiones de solo lectura.
- MyToyota bloquea igualmente: diálogo modal en el splash; pulsar OK mata el proceso. No se llega al login.
- Causa más probable: el emulador **no está certificado por Play Protect** (Play Store lo indica literalmente) y un emulador nunca obtiene `MEETS_DEVICE_INTEGRITY`; MyToyota lo presenta bajo el texto genérico "rooted". Ninguna imagen oficial lo evita. Siguiente paso: dispositivo Android físico estándar por ADB.

## Qué se ha comprobado

| Comprobación | AVD `mytoyota` | Android comercial típico | Lectura |
|---|---|---|---|
| System image | `system-images;android-36;google_apis_playstore;x86_64` (Android 16, Google Play) | imagen OEM certificada | oficial Google Play, firmada con release key, sin root posible ([doc](https://developer.android.com/studio/run/managing-avds)) |
| `ro.build.type` / `ro.build.tags` | `user` / `release-keys` | `user` / `release-keys` | igual que un dispositivo comercial |
| `ro.build.fingerprint` | `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.D1/13818094:user/release-keys` | fingerprint OEM | identifica un emulador |
| `ro.debuggable` / `ro.secure` / `ro.adb.secure` | `0` / `1` / `1` | `0` / `1` / `1` | producción |
| `adb root` | "adbd cannot run as root in production builds"; shell = uid 2000 | igual | sin root por ADB |
| `su`, Magisk, SuperSU, Xposed/LSPosed, Frida, busybox | ninguno (ni binarios ni paquetes) | ninguno | sin herramientas de root |
| `/`, `/vendor`, `/product`, `/system_ext` | erofs **ro** | ro | sistema no modificable |
| SELinux | Enforcing | Enforcing | igual |
| Parche de seguridad | 2025-07-05 | mensual | antiguo; afectaría a STRONG, no a DEVICE integrity |
| `ro.kernel.qemu` / `ro.boot.qemu` / `ro.hardware` | `1` / `1` / `ranchu` | ausentes / OEM | **marcador de emulador** |
| modelo / device / serial | `sdk_gphone64_x86_64` / `emu64xa` / `EMULATOR37X1X11X0` | OEM | **marcador de emulador** |
| `ro.boot.verifiedbootstate` / `flash.locked` / `vbmeta.device_state` | vacíos | `green` / `1` / `locked` | **sin verified boot ni bootloader bloqueado atestables** |
| Google Play | Play Store 53.3.21, GMS 26.36.33, cuenta iniciada; MyToyota instalada con installer `com.android.vending` | igual | Play operativo y fuente legítima |
| Play Protect | activo, "No harmful apps found" | igual | correcto |
| Certificación Play Protect (Play Store › Ajustes › Acerca de) | **"Device is not certified"** (Play Store muestra "Fix device issue") | "Device is certified" | **dispositivo no certificado**: señal visible al usuario de que Google no lo trata como dispositivo real |
| Play Integrity | no medido (requeriría instalar una app checker); por documentación, un emulador obtiene como mucho `MEETS_VIRTUAL_INTEGRITY`, nunca `MEETS_DEVICE_INTEGRITY` ([doc](https://developer.android.com/google/play/integrity/verdicts)) | `MEETS_DEVICE_INTEGRITY` | **no superable en emulador** |
| Depuración ADB | `adb_enabled=1` (por defecto en el emulador); `development_settings_enabled` sin fijar | `0` | diferencia menor, no es "root" |
| Bloqueo de pantalla | ninguno | PIN/biometría | diferencia menor, no es "root" |

## Comportamiento observado de MyToyota

- Versión 2.25.2 (versionCode 382), minSdk 29, targetSdk 36. Instalada desde Google Play (installer `com.android.vending`).
- Tamaño instalado: 286,6 MB (base 186 MB + split x86_64 100 MB + xxhdpi 14 MB). 30 permisos declarados.
- En `SplashActivity`, antes de cualquier interacción, aparece el diálogo "The Toyota app does not support rooted devices." con un único botón OK. Al pulsarlo el proceso termina y vuelve a Play Store.
- La comprobación ocurre antes del login, luego no depende de la cuenta ni del vehículo: es una verificación local de integridad del dispositivo o una llamada a Play Integrity al arrancar.

## Por qué dice "rooted" si no hay root

- Las librerías de root/RASP agrupan root, emulador, bootloader desbloqueado y fallo de atestación bajo un único mensaje. Google documenta que un emulador no cuenta como dispositivo físico: el veredicto queda vacío cuando "the app is not running on a physical device (such as an emulator that does not pass Google Play integrity checks)" ([verdicts](https://developer.android.com/google/play/integrity/verdicts)).
- Una imagen Google Play del emulador puede obtener a lo sumo `MEETS_VIRTUAL_INTEGRITY`; una app que exija `MEETS_DEVICE_INTEGRITY` rechaza cualquier emulador por diseño.
- Proveedores de emuladores en la nube documentan la misma incidencia genérica ("rooted" en emuladores) ([Appetize](https://support.appetize.io/android-app-does-not-run-on-rooted-device)). Hay hilos de usuarios con el mismo texto en teléfonos no rooteados ([foro](https://www.grcorollaforum.com/threads/toyota-app-wont-work-on-my-phone.2642/), de pago, sin confirmar).

## ¿Un AVD nuevo lo arreglaría?

No. Todas las imágenes oficiales con Play Store (API 34, 35, 36, 36.1, 37.0, `google_apis_playstore;x86_64`) comparten `ro.kernel.qemu=1`, `ro.hardware=ranchu`, modelo `sdk_gphone64_*` y ausencia de verified boot atestable. Las imágenes Google APIs y AOSP son `userdebug` con root: peor. Por eso **no se ha creado otro AVD**; `mytoyota` se conserva tal cual.

No se ha intentado ocultar root, instalar Magisk, hacer hooking, falsear Play Integrity, modificar el APK ni manipular identificadores. Tampoco se han alterado ajustes del dispositivo (depuración, bloqueo de pantalla) para forzar el paso: con la detección de emulador activa el resultado sería el mismo y no aislaría nada.

## Alternativas descartadas

- LDPlayer 9.5 (ya instalado en el PC): emulador de juegos, no certificado, misma detección, más pesado.
- WSA: discontinuado por Microsoft en 2025.
- Genymotion, BlueStacks y similares: no certificados; misma detección.
- Android Device Streaming (dispositivos físicos remotos de Google vía Android Studio): pasaría integridad, pero exige iniciar sesión en Google y Toyota en un dispositivo compartido en la nube. Descartado por privacidad.

## Recomendación: dispositivo Android físico estándar por ADB

1. Teléfono Android normal (no rooteado, bootloader bloqueado, Play certificado) con MyToyota instalada desde Play; el usuario inicia sesión a mano.
2. Conectarlo por USB o depuración inalámbrica con la depuración activada. Riesgo: algunas RASP también marcan "depuración activa"; si MyToyota lo hiciera, se analiza con la depuración desactivada y capturas hechas desde el propio teléfono.
3. Desde el PC, solo observación: `adb exec-out screencap`, `adb shell uiautomator dump` (lectura de pantalla, sin tocar la app) y scrcpy para mirroring.
4. El AVD `mytoyota` queda para probar el posible componente de teléfono de nuestra app y como Play Store de referencia.

## Impacto en el plan

- Fase 1 (análisis funcional de MyToyota) pasa a dispositivo físico.
- Fase 2 (READ ONLY con pytoyoda) no depende del emulador y puede ejecutarse desde el PC cuando el usuario lo autorice.

## Coste del AVD medido (para referencia)

Arranque en frío 40 s; qemu en reposo 0,6 % de CPU total y ~2 GB privados en el host; disco AVD 2,6 GB + imagen 2,3 GB.
