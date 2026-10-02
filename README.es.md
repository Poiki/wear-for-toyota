# Wear para Toyota

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/A0A71AC06Z)

App no oficial y autónoma para Wear OS pensada para coches Toyota y Lexus vendidos en Europa (cuentas de MyToyota / Toyota Connected Services Europe). Muestra si el coche está cerrado, la autonomía, el combustible o la batería y el kilometraje, y envía los comandos remotos que MyToyota ofrece para tu coche: cerrar, abrir y climatizador. En español, inglés, alemán, francés, italiano y portugués.

[English version](README.md)

> **No está afiliada a Toyota ni cuenta con su apoyo.** La app habla con el mismo backend que la app oficial MyToyota, tal y como lo ha documentado la comunidad (ver Créditos). Toyota puede cambiar ese backend en cualquier momento y dejar la app inservible. Úsala bajo tu responsabilidad y solo con tu propio coche y tu propia cuenta.

## Qué hace

- **Mi Garaje**: una tarjeta por coche de la cuenta, con la imagen oficial de Toyota.
- **Vehículo**: cerrado o abierto, autonomía, combustible o batería, kilometraje, fecha de los datos y última posición aparcado (abre la app de mapas del reloj).
- **Cerrar / Abrir**: una sola pantalla con ambas acciones. Abrir pide confirmación en pantalla. Tras cada orden la app despierta al coche y verifica su estado real antes de decirte "Vehículo cerrado".
- **Climatizador**: una esfera que giras con la corona o con los botones +/− (18–29 °C); encender o apagar durante 10 minutos.
- **Autónoma**: el reloj habla con Toyota por sí mismo por Wi-Fi o LTE, o a través del Bluetooth del móvil emparejado. La app de móvil es opcional.
- **Pensada para la batería**: sin sondeos en segundo plano, sin servicios, un único fichero de estado cifrado. Solo se despierta al coche cuando tú lo pides.

## Requisitos

- Una cuenta Toyota o Lexus en Europa con Connected Services y un coche con servicios remotos. Lo que MyToyota ofrezca para tu coche es lo que esta app puede hacer.
- Un reloj con Wear OS 3 o superior (Android 11, API 30+). Probado en un OnePlus Watch 3 (Wear OS 6) y en el emulador de Wear OS 5.
- Para compilar: JDK 17 o superior y un Android SDK con la plataforma 37 y build-tools 36. Android Studio es opcional; todo funciona desde la línea de comandos.

## Instala tu propia copia

1. Clona el repositorio y compila las dos apps:

   ```bash
   cd android && ./gradlew :watch:assembleRelease :phone:assembleRelease
   ```

   En Windows usa `gradlew.bat`. Los APK quedan en `android/watch/build/outputs/apk/release/` y `android/phone/build/outputs/apk/release/`. Sin un keystore propio se firman con la clave debug, suficiente para instalarlos a mano.

2. Activa las opciones de desarrollador y la depuración inalámbrica en el reloj: Ajustes → Sistema → Información → toca siete veces "Número de compilación" → Opciones de desarrollador → Depuración inalámbrica. Después, desde el ordenador:

   ```bash
   adb pair <ip-del-reloj>:<puerto-de-emparejamiento>
   adb connect <ip-del-reloj>:<puerto>
   ```

3. Instala la app del reloj:

   ```bash
   adb -s <serial-del-reloj> install -r android/watch/build/outputs/apk/release/watch-release.apk
   ```

4. Inicia sesión de cualquiera de las dos formas:
   - **En el reloj**: abre la app → "Iniciar sesión aquí" → escribe el email y la contraseña de MyToyota con el teclado del reloj.
   - **Desde el móvil** (opcional): instala `phone-release.apk` en el móvil emparejado con `adb install -r`, abre "Wear para Toyota" e inicia sesión. Los tokens viajan al reloj por el enlace Bluetooth; el móvil no guarda nada.

5. Opcional, para una release firmada con tu clave: crea `android/keystore.properties` con `storeFile`, `storePassword`, `keyAlias` y `keyPassword`. Está ignorado por git y se usa automáticamente. Los dos APK deben compartir firma para que funcione el enlace móvil → reloj.

## Seguridad y privacidad

- La contraseña se usa una sola vez, en el inicio de sesión, y nunca se guarda. El reloj conserva los tokens OAuth cifrados con una clave que vive en el Android Keystore; las copias de seguridad están desactivadas.
- Sin analítica ni servidores de terceros. El reloj solo habla con los servidores de Toyota (`*.toyotaconnectedeurope.io`, `b2c-login.toyota-europe.com`) y, una vez por coche, con el CDN de imágenes de Toyota.
- Los identificadores estáticos de la app oficial (client id, API key) son los públicos documentados por pytoyoda. Toyota puede cambiarlos.
- Abrir pide confirmación pero, por diseño, no un PIN del reloj. Quien lleve un reloj desbloqueado podría abrir tu coche. Si quieres esa barrera, configura un bloqueo de pantalla en el reloj: la app exigirá entonces que esté desbloqueado.
- Los logs nunca contienen tokens ni credenciales. Las ayudas de depuración (inyección de tokens, modo demo) no existen en los builds release.
- Política de privacidad completa: [PRIVACY.md](PRIVACY.md).
- ¿Has encontrado una vulnerabilidad o un fallo? Abre una incidencia. No pegues nunca tokens, VIN ni coordenadas.

## Límites conocidos

- Solo Europa; otras regiones usan backends distintos.
- La API no es oficial. Cuando Toyota la cambie, la app necesitará una actualización; las incidencias de pytoyoda son el sistema de alerta temprana.
- Los comandos remotos dependen del coche y de la suscripción. El climatizador funciona 10 minutos y Toyota limita los arranques por ciclo de contacto.
- No está en Google Play: Play prohíbe introducir contraseñas en el reloj y una API no oficial no pasaría la revisión. Solo instalación manual.

## Desarrollo

- `android/core`: cliente Toyota (login, refresh, lecturas, comandos) y parseo de respuestas, con tests unitarios sobre fixtures reales: `./gradlew :core:testDebugUnitTest`.
- `android/watch` y `android/phone`: las dos apps. Compose for Wear OS Material 3; la app de móvil es una sola pantalla.
- Modo demo (solo builds debug): `adb shell am start -n com.poiki.toyotawear/.MainActivity --ez demo true` siembra un coche ficticio para trabajar en la interfaz sin cuenta.
- `probe/probe.py`: volcado de solo lectura de lo que Toyota devuelve para una cuenta (`uv run probe/probe.py`).
- `docs/`: investigación sobre la API y la autenticación de Toyota, el stack de Wear OS, las apps de reloj de otros fabricantes, las decisiones técnicas y las mediciones.
- Los informes de fallos y las ideas de mejora son bienvenidos: abre una incidencia o un pull request.

## Créditos

- [pytoyoda](https://github.com/pytoyoda/pytoyoda) y [ha_toyota](https://github.com/pytoyoda/ha_toyota) (MIT): la documentación comunitaria de la API de MyToyota Europa que sigue esta app (endpoints, cabeceras, flujo de login, estrategia de wake y refresco). Los fixtures de test de `android/core/src/test/resources` proceden de pytoyoda.
- [DurgNomis-drol/mytoyota](https://github.com/DurgNomis-drol/mytoyota), el proyecto del que desciende pytoyoda. También estudiados: [openhab-mytoyota-binding](https://github.com/Prinsessen/openhab-mytoyota-binding), [evcc](https://github.com/evcc-io/evcc) e [ioBroker.toyota](https://github.com/TA2k/ioBroker.toyota).
- [Material Symbols](https://github.com/google/material-design-icons) (Apache 2.0) para los iconos.
- [Wear OS samples](https://github.com/android/wear-os-samples) (Apache 2.0) como base del proyecto Compose for Wear OS.

## Licencia

[PolyForm Noncommercial 1.0.0](LICENSE): úsala, modifícala y compártela libremente para fines no comerciales, conserva el aviso de copyright y da crédito a este proyecto en lo que construyas sobre él.
