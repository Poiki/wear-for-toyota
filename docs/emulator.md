# Ejecutar MyToyota (Toyota Europe) en Windows 11: la opción más ligera

Investigación: 2026-10-01. Equipo: Windows 11 Pro 26200, AMD Ryzen 7 5700X (16 hilos), 32 GB RAM, Hyper-V + WSL2 + Docker Desktop activos, Android SDK en `%LOCALAPPDATA%\Android\Sdk` (Android Emulator 37.1.11, platform-tools 37.0.1).
Objetivo: análisis funcional de la app instalada desde Google Play. Sin bypass de integridad, sin APK modificados.
`(sin confirmar)` = dato sin fuente oficial o no verificado en esta máquina.

## 1. Datos de la app MyToyota

| Campo | Valor | Fuente |
|---|---|---|
| Nombre / desarrollador | MyToyota / Toyota Motor Europe (TME) | [S1] |
| Package | `com.toyota.oneapp.eu`. El candidato `app.mytoyota.toyota.com.mytoyota` es la app antigua "MyT by Toyota", sustituida por la nueva API ctpa-oneapi | [S1][S3][S7] |
| Versión / fecha | 2.25.2 (build 382). Play: "Última actualización 2 sept 2026"; APKMirror/Uptodown: 10-11 sept 2026 | [S1][S3][S4] |
| Tamaño | Play web no muestra tamaño. Bundle universal 177,4 MB (APKMirror) / 174,3 MB (Uptodown); XAPK con todos los splits 586 MB (APKCombo) | [S3][S4][S5] |
| Android mínimo | "Requiere Android 10 y versiones posteriores" | [S1][S3][S4] |
| Google Play services | Play no lo declara. Usa GMS (mapas, push) `(sin confirmar)`; sin listado en Huawei AppGallery para la app EU `(sin confirmar)` | - |
| Permisos (Play, todas las versiones) | Calendario (leer/modificar), Contactos, Ubicación aproximada y precisa, Fotos/multimedia/archivos, Almacenamiento, Cámara, Wi-Fi, Bluetooth (emparejar/ajustes), NFC, red completa, ejecutarse al inicio, mostrar sobre otras apps, vibración, impedir suspensión | [S1] |
| Seguridad de datos | Recoge ubicación aproximada, datos personales, IDs de dispositivo, fotos, actividad; comparte datos personales y fotos con terceros; cifrado en tránsito; borrado bajo petición | [S2] |
| Descargas / valoración | 1.000.000+; 4,5 estrellas (171 mil reseñas); PEGI 3; publicada 13 oct 2023 | [S1] |
| Disponible en España | Sí: ficha con `gl=ES`, botón "Descargar" y reseñas españolas de sept 2026 | [S1] |
| Android Auto / Wear OS | Sin evidencia: ni la ficha ni APKMirror listan variante Wear OS ni soporte Android Auto `(sin confirmar)` | [S1][S3] |
| Novedades 2.25 (Play) | My Account y pantallas de testigos renovadas; Driving Analytics y avisos de privacidad; compra de suscripciones; mejoras HomeCharge y fixes de Digital Key; contenido educativo | [S1] |
| Incidencia reciente | Reseñas de sept 2026: fallo de inicio de sesión; TME responde "ya solucionada, actualiza a 2.25.2" | [S1] |

## 2. ¿Funciona en emuladores? Evidencia

- No hay informes públicos (Reddit, XDA, Home Assistant, GitHub) de MyToyota EU fallando ni funcionando en AVD, BlueStacks, Genymotion, root o GrapheneOS. Búsquedas: "MyToyota" + emulator / BlueStacks / rooted / Play Integrity / GrapheneOS / Magisk / LineageOS, en inglés, español, alemán, francés e italiano: sin resultados específicos. Las páginas "MyT by Toyota en PC" de LDPlayer son marketing generado, no pruebas [S8].
- Backend: los clientes open source `pytoyoda` / `ha_toyota` inician sesión en Toyota Connected Services Europe solo con usuario y contraseña desde Python, sin atestación de dispositivo [S6][S7]. El servidor no exige Play Integrity para autenticar; un bloqueo sería una comprobación dentro de la app `(sin confirmar que exista)`.
- App de Toyota Norteamérica (otra app): hilo de ToyotaNation sobre error en unidades Android rooteadas [S9]; contenido tras muro de pago `(sin confirmar)`; no aplica a la app EU.
- Play Integrity en entornos virtuales (doc oficial): el único veredicto para emuladores es `MEETS_VIRTUAL_INTEGRITY`, "currently limited to Google Play Games on PC"; un emulador que no pasa las comprobaciones recibe veredicto vacío [S10][S11]. BlueStacks: "Play Integrity does not pass on BlueStacks, at any level" [S12]. Si MyToyota exigiera `MEETS_DEVICE_INTEGRITY`, ningún emulador de Windows lo cumple: Plan B = móvil físico.
- A favor del AVD con Google Play: las imágenes Google Play van firmadas con release key, sin root (`adb root` no disponible) y son CTS-compliant [S13]; superan las comprobaciones de root clásicas.
- **Resultado empírico (2026-10-01, AVD `mytoyota`, MyToyota 2.25.2 instalada desde Play):** la app muestra "The Toyota app does not support rooted devices." y se cierra; Play Store › Ajustes › Acerca de indica "Device is not certified". Diagnóstico completo en [mytoyota-emulator-compatibility.md](./mytoyota-emulator-compatibility.md). Conclusión: Plan B (móvil físico).
- Digital Key (NFC/Bluetooth) no se puede ejercitar en ningún emulador; el GPS se simula con `adb emu geo fix`.

## 3. Comparativa de emuladores (0-5 por criterio; mayor = mejor)

| Opción | RAM | CPU idle | Disco | Arranque | Play Store | Play Integrity | Estabilidad | Esfuerzo | Legitimidad | Total /45 | Notas |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Android Emulator (AVD) + imagen Google Play, WHPX | 3 | 3 | 4 | 4 | 5 | 1 | 4 | 5 | 5 | **34** | Medido aquí: 3,7 GB working set con `hw.ramSize=2048`; 5,9 GB disco; Quick Boot "under 6 seconds" [S14]; ya instalado |
| BlueStacks 5 (Android 9/11; 13 beta jul 2026) | 3 | 3 | 3 | 3 | 5 | 0 | 3 | 4 | 4 | 28 | 4 GB RAM / 5 GB disco mín. [S15]; build específico para Hyper-V [S16]; Play Integrity falla [S12]; anuncios en la versión gratuita `(sin confirmar)` |
| LDPlayer 9 / MEmu / Nox / GameLoop | 3 | 3 | 3 | 3 | 4 | 0 | 3 | 4 | 2 | 25 | LDPlayer base Android 9 [S17] `(sin confirmar)`; anuncios en gratuito `(sin confirmar)`; Nox con quejas de adware en reseñas [S18]; orientados a juegos |
| Genymotion Desktop (free, uso personal) | 3 | 3 | 3 | 3 | 3 | 0 | 2 | 2 | 3 | 22 | Con VirtualBox exige desactivar Hyper-V por completo [S19] (rompe WSL2/Docker); QEMU experimental con problemas de rendimiento [S19]; Play vía widget Open GApps, no certificado [S20]; free sin Quick Boot ni Android recientes [S21] |
| WSA (Windows Subsystem for Android) | 4 | 4 | 4 | 4 | 0 | 0 | 1 | 0 | 2 | 19 | Fuera de Microsoft Store desde el 5 mar 2025 [S22][S23]; solo Amazon Appstore (sin MyToyota); informes de usuarios de desinstalación automática `(sin confirmar oficialmente)` [S24]; builds no oficiales: no recomendadas |
| Waydroid en WSL2 (WSLg) | 4 | 4 | 4 | 4 | 1 | 0 | 1 | 0 | 1 | 19 | Kernel WSL2 sin binder: hay que recompilarlo [S25][S26]; GApps de terceros |
| redroid en Docker Desktop | 4 | 4 | 4 | 4 | 1 | 0 | 1 | 0 | 1 | 19 | Igual: kernel WSL2 custom (binder, ashmem) [S27]; GApps solo vía MindTheGapps/scripts [S28] |
| Android-x86 / Bliss OS en Hyper-V | 3 | 2 | 3 | 2 | 2 | 0 | 1 | 1 | 2 | 16 | Docs de Bliss cubren VirtualBox/QEMU, no Hyper-V [S29]; sin GPU en Hyper-V: render por software `(sin confirmar)`; builds con GApps `(sin confirmar)` |

Criterios: RAM / CPU / disco = huella en reposo; Play Integrity = probabilidad de obtener veredicto de dispositivo (0 = nula); Legitimidad = app desde Google Play oficial en un entorno sin modificar y sin bloatware.

## 4. Recomendación y configuración concreta

**Opción más ligera con fuente legítima: Android Emulator (AVD) con imagen `google_apis_playstore` x86_64 acelerada por WHPX.** Razones: ya está instalado (emulator 37.1.11, imagen android-36 rev 7, AVD `mytoyota` creado); WHPX es la recomendación de Google en Windows y convive con Hyper-V, WSL2 y Docker, mientras que AEHD no coexiste con Hyper-V ni Core Isolation y se retira el 31 dic 2026 [S30][S31]; Play Store oficial con la cuenta Google del usuario; sin root. Requisitos oficiales del emulador: 16 GB RAM y 16 GB de disco [S37]; sobran aquí.

Estado comprobado en esta máquina (2026-10-01):
- `emulator -accel-check` -> `WHPX(10.0.26200) is installed and usable`.
- Imágenes Google Play x86_64 en `sdkmanager --list`: android-28 a android-36, `android-36.1` (rev 4) y `android-37.0` (rev 6). Instalada: `system-images;android-36;google_apis_playstore;x86_64` rev 7.
- Evitar API 37 para un AVD ligero: desde emulator 36.6.11 fuerza un mínimo de 4 GB de RAM [S31] y las revisiones < 5 fallan al iniciar sesión en Google [S32]. API 36 (Android 16) cubre el requisito "Android 10+".
- AVD `mytoyota` existente: pixel_7, 2 cores, 2048 MB, data 4 GB, GPU host, sin audio/cámara/SD, Play Store habilitado. Medido con el AVD en marcha (uptime 8 min, MyToyota aún no instalada): `qemu-system-x86_64` con 3,7 GB de working set (2,3 GB privados) y ~2 hilos ocupados durante el asentamiento tras el arranque (actualizaciones de Play). Disco: imagen 2,28 GB + emulador 1,01 GB + AVD 2,59 GB.

```powershell
$SDK = "$env:LOCALAPPDATA\Android\Sdk"
$SM  = "$SDK\cmdline-tools\latest\bin\sdkmanager.bat"
$AM  = "$SDK\cmdline-tools\latest\bin\avdmanager.bat"
$EMU = "$SDK\emulator\emulator.exe"
$ADB = "$SDK\platform-tools\adb.exe"

& $EMU -accel-check                                      # debe responder "WHPX ... is installed and usable"
& $SM --list | Select-String "google_apis_playstore;x86_64"
& $SM "emulator" "platform-tools" "system-images;android-36;google_apis_playstore;x86_64"

# AVD nuevo (omitir si ya existe `mytoyota`)
& $AM create avd -n mytoyota -k "system-images;android-36;google_apis_playstore;x86_64" -d pixel_7

# Perfil ligero: reescribe claves de config.ini (propiedades documentadas en [S13])
$ini = "$env:USERPROFILE\.android\avd\mytoyota.avd\config.ini"
$set = [ordered]@{ 'hw.ramSize'='2048'; 'hw.cpu.ncore'='2'; 'disk.dataPartition.size'='6442450944';
  'hw.gpu.enabled'='yes'; 'hw.gpu.mode'='auto'; 'hw.audioInput'='no'; 'hw.audioOutput'='no';
  'hw.camera.back'='none'; 'hw.camera.front'='none'; 'hw.sdCard'='no'; 'hw.keyboard'='yes' }
$keep = Get-Content $ini | Where-Object { $_.Split('=')[0].Trim() -notin $set.Keys }
$keep + ($set.GetEnumerator() | ForEach-Object { "$($_.Key) = $($_.Value)" }) | Set-Content $ini

# Primer arranque (frío); al cerrar guarda el snapshot Quick Boot. -netdelay none / -netspeed full son los valores por defecto.
& $EMU @mytoyota -no-audio -no-boot-anim -netdelay none -netspeed full
# Arranques siguientes: Quick Boot por defecto. Sesión desechable que no toca el snapshot:
& $EMU @mytoyota -no-audio -no-boot-anim -no-snapshot-save
# Headless (interacción solo por adb/scrcpy), render por software:
& $EMU @mytoyota -no-window -no-audio -no-boot-anim -gpu swiftshader
# Ajustes puntuales sin editar config.ini (-memory admite 1536-8192 MB):
& $EMU @mytoyota -memory 2048 -cores 2
# Comprobar arranque completo (repetir hasta que devuelva 1)
& $ADB wait-for-device shell getprop sys.boot_completed
```

Instalar MyToyota desde Google Play (única fuente legítima; los espejos APK no se usan):
1. En el AVD, abrir Play Store e iniciar sesión con la cuenta Google (la escribe el usuario; recomendable una cuenta dedicada al análisis).
2. Buscar "MyToyota" (Toyota Motor Europe) e instalar. Verificar: `adb shell dumpsys package com.toyota.oneapp.eu | findstr versionName` -> 2.25.2.
3. Iniciar sesión en la app con la cuenta Toyota. Ubicación simulada para "Encuentra mi vehículo": `adb emu geo fix -3.7038 40.4168`.
4. Análisis funcional: `adb logcat -v time | findstr /i "toyota oneapp"`, `adb shell dumpsys activity top`, capturas con `adb exec-out screencap -p > shot.png`.

Flags: `-gpu` admite `auto | host | software | lavapipe | swiftshader | swangle` (`swiftshader_indirect` obsoleto desde 36.4.9) [S30]; `-memory`, `-no-window`, `-no-audio`, `-no-boot-anim`, `-netdelay`, `-no-snapshot*` en [S33]; `-cores <number>` aparece en `emulator -help` del build 37.1.11 local.

Problemas conocidos en Windows 11 con Hyper-V: el antivirus ralentiza guardar/cargar snapshots (añadir el emulador como app de confianza); fallo de arranque por el límite de commit de Windows (dejar el pagefile gestionado por el sistema); errores Vulkan: `-feature -Vulkan` [S32]; crash del kernel invitado con WHPX en API 35/36 corregido en 36.2.11 [S31]. Issue antiguo "WHPX en Ryzen con Windows 11" (microsoft/WSL #7438, cerrado sin resolución documentada) [S34]: no se reproduce aquí (`-accel-check` OK).

## 5. Plan B si Play Integrity (o la detección de emulador) bloquea

1. Diagnosticar antes de culpar al emulador: (a) confirmar versión 2.25.2, la incidencia de login de sept 2026 era del servidor [S1]; (b) instalar desde Play "Play Integrity API Checker" (`gr.nikolasspyr.integritycheck`) [S35] en el AVD y leer el veredicto (esperado: vacío); (c) al fallar, `adb logcat | findstr /i "integrity droidguard attest root emulator"`.
2. Móvil Android físico certificado (sin root) por USB: depuración USB + `scrcpy` [S36] para ver y controlar la pantalla desde el PC, `adb logcat`, Layout Inspector. Es además la única vía para Digital Key (NFC/BLE) y notificaciones reales. Coste en el PC: ~0.
3. Análisis a nivel de API con `pytoyoda` (cliente open source, credenciales propias del usuario) [S6] para cubrir los datos de Connected Services sin la app.
4. Fuera de alcance, ni se documenta ni se recomienda: módulos de ocultación de root o de Play Integrity, APK modificados, GApps no oficiales, registro como "dispositivo no certificado".

## 6. Fuentes

- [S1] https://play.google.com/store/apps/details?id=com.toyota.oneapp.eu&hl=es&gl=ES (ficha, "Información de la aplicación", "Permisos", "Novedades", reseñas; consultada 2026-10-01)
- [S2] https://play.google.com/store/apps/datasafety?id=com.toyota.oneapp.eu&hl=es
- [S3] https://www.apkmirror.com/apk/toyota-motor-europe-tme/mytoyota/ y https://www.apkmirror.com/apk/toyota-motor-europe-tme/mytoyota/mytoyota-2-25-2-release/
- [S4] https://mytoyota-eu.en.uptodown.com/android
- [S5] https://apkcombo.com/mytoyota/com.toyota.oneapp.eu
- [S6] https://github.com/pytoyoda/pytoyoda y https://github.com/pytoyoda/ha_toyota
- [S7] https://github.com/DurgNomis-drol/mytoyota (nota: "2.0.0 solo soporta la nueva API ctpa-oneapi; no compatible con la app MyT antigua") y https://apkcombo.com/myt-by-toyota/app.mytoyota.toyota.com.mytoyota/
- [S8] https://fr.ldplayer.net/apps/myt-by-toyota-on-pc.html
- [S9] https://www.toyotanation.com/threads/toyota-owners-app-on-android-head-units-rooted.1655546/
- [S10] https://developer.android.com/google/play/integrity/verdicts
- [S11] https://developer.android.com/games/playgames/integrity
- [S12] https://github.com/RobThePCGuy/Root-Bluestacks-with-Kitsune-Mask
- [S13] https://developer.android.com/studio/run/managing-avds (imágenes Google Play: release key, sin root, CTS; propiedades `hw.*` de config.ini)
- [S14] https://android-developers.googleblog.com/2017/12/quick-boot-top-features-in-android.html
- [S15] https://en.wikipedia.org/wiki/BlueStacks y https://support.bluestacks.com/hc/en-us/articles/360056129211-System-requirements-for-BlueStacks-5
- [S16] https://support.bluestacks.com/hc/en-us/articles/4415238471053-System-requirements-for-BlueStacks-5-on-Hyper-V-enabled-Windows-10-and-11
- [S17] https://www.ldplayer.net/support/ldplayer9-faster-higher-smother-android-emulator.html
- [S18] https://nz.trustpilot.com/review/es.bignox.com (reseñas de usuarios, no fuente oficial)
- [S19] https://support.genymotion.com/hc/en-us/articles/360005432518-What-are-Genymotion-Desktop-requirements
- [S20] https://docs.genymotion.com/features/opengapps/
- [S21] https://www.genymotion.com/pricing/
- [S22] https://learn.microsoft.com/en-us/windows/android/wsa/
- [S23] https://github.com/microsoft/WSA/discussions/536
- [S24] https://learn.microsoft.com/en-us/answers/questions/3897810/will-windows-subsystem-for-android-wsa-stay-execut y https://www.windowslatest.com/2025/06/08/windows-11s-android-wsa-finally-loses-support-but-can-you-still-install-it/
- [S25] https://github.com/microsoft/WSL/issues/12692
- [S26] https://github.com/waydroid/waydroid/issues/1561 y https://github.com/gfnord/WayDroid-Windows
- [S27] https://github.com/remote-android/redroid-doc/blob/master/deploy/wsl.md
- [S28] https://github.com/ayasa520/redroid-script
- [S29] https://docs.blissos.org/installation/install-in-a-virtual-machine/ y https://xdaforums.com/t/run-bliss-os-v16-4-android-13-w-gapps-kernelsu-on-qemu-on-top-of-windows-11-22h2-23h2.4635588/
- [S30] https://developer.android.com/studio/run/emulator-acceleration
- [S31] https://developer.android.com/studio/releases/emulator
- [S32] https://developer.android.com/studio/run/emulator-troubleshooting
- [S33] https://developer.android.com/studio/run/emulator-commandline
- [S34] https://github.com/microsoft/WSL/issues/7438
- [S35] https://play.google.com/store/apps/details?id=gr.nikolasspyr.integritycheck
- [S36] https://github.com/Genymobile/scrcpy
- [S37] https://developer.android.com/studio/run/emulator
- Local (2026-10-01): `sdkmanager --list`, `emulator -accel-check`, `emulator -help`, `%USERPROFILE%\.android\avd\mytoyota.avd\config.ini`, `Get-Process qemu-system-x86_64`, tamaños de carpetas del SDK.
