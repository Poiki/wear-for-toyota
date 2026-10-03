# Privacy Policy — Wear for Toyota

Last updated: 2026-10-03 (version 0.2.0). [Versión en español más abajo.](#política-de-privacidad--wear-para-toyota)

Wear for Toyota is an unofficial, open-source app that lets you see and control your Toyota or Lexus car from a Wear OS watch. It is not affiliated with Toyota.

## What the app collects

Nothing. The app has no analytics, no crash reporting, no advertising and no servers of its own. The developer never receives any data from the app.

## What the app sends, and where

- Your MyToyota email and password are sent over HTTPS to Toyota's own login service (`b2c-login.toyota-europe.com`) to obtain access tokens. By default the password is used only for that request and is not stored. If you turn on "Save password", it is kept encrypted on the watch (see below) and sent to the same service again only when Toyota ends your session.
- Vehicle data (lock state, range, fuel or battery, mileage, climate, last parked position) and remote commands travel only between your watch (or the optional phone app) and Toyota's servers (`*.toyotaconnectedeurope.io`), exactly as with the official MyToyota app.
- The picture of your car is downloaded once from Toyota's image CDN.
- Update check: at most once a day, when you open the app, the watch asks GitHub (`api.github.com`) for the latest release of this project. If you accept an update, the APK is downloaded from GitHub. No account or identifier is sent; GitHub sees your IP address, as with any web request.

## What the app stores on your devices

- On the watch: the Toyota access and refresh tokens, encrypted with a key held in the Android Keystore; your MyToyota email and password only if you turned on "Save password", encrypted with a separate 256-bit Keystore key (in StrongBox when the watch has one) that works only while the watch is unlocked, if it has a screen lock; the list of your cars and the last data read from Toyota, including the last parked position; the picture of your car. Backups are disabled. A saved password is deleted as soon as Toyota rejects it.
- On the phone (optional app): nothing. It only forwards the tokens to the watch over the Bluetooth link, plus your email and password if you chose to save them on the watch.
- Tapping "Unlink" in the watch app deletes everything the app stored.

## Third parties

Toyota Motor Europe processes your account and vehicle data under its own privacy notice when you use MyToyota or this app. Google Play Services on the phone and watch carry the Bluetooth message between the two apps. GitHub serves the update check and the update download. No other third party is involved.

## Permissions

The watch app requests Internet access and permission to install apps. The second one is used only to install an update of this same app, after you accept it and Android's installer asks you to confirm. It does not use location, contacts, sensors or any other permission.

## Contact

Questions or concerns: open an issue at https://github.com/Poiki/wear-for-toyota/issues.

---

# Política de privacidad — Wear para Toyota

Última actualización: 3 de octubre de 2026 (versión 0.2.0).

Wear para Toyota es una app no oficial y de código abierto que permite ver y controlar tu Toyota o Lexus desde un reloj Wear OS. No está afiliada a Toyota.

## Qué recoge la app

Nada. La app no tiene analítica, ni informes de fallos, ni publicidad, ni servidores propios. El desarrollador nunca recibe ningún dato de la app.

## Qué envía la app y adónde

- Tu email y contraseña de MyToyota se envían por HTTPS al servicio de inicio de sesión de Toyota (`b2c-login.toyota-europe.com`) para obtener los tokens de acceso. Por defecto la contraseña solo se usa en esa petición y no se guarda. Si activas "Guardar contraseña", se conserva cifrada en el reloj (ver más abajo) y solo se vuelve a enviar a ese mismo servicio cuando Toyota cierra tu sesión.
- Los datos del vehículo (cierre, autonomía, combustible o batería, kilometraje, climatizador, última posición aparcado) y los comandos remotos viajan únicamente entre tu reloj (o la app opcional del móvil) y los servidores de Toyota (`*.toyotaconnectedeurope.io`), igual que con la app oficial MyToyota.
- La imagen de tu coche se descarga una vez del CDN de imágenes de Toyota.
- Búsqueda de actualizaciones: como mucho una vez al día, al abrir la app, el reloj pregunta a GitHub (`api.github.com`) por la última release de este proyecto. Si aceptas una actualización, el APK se descarga de GitHub. No se envía ninguna cuenta ni identificador; GitHub ve tu dirección IP, como en cualquier petición web.

## Qué guarda la app en tus dispositivos

- En el reloj: los tokens de acceso y refresco de Toyota, cifrados con una clave que vive en el Android Keystore; tu email y contraseña de MyToyota solo si activaste "Guardar contraseña", cifrados con otra clave de 256 bits del Keystore (en StrongBox si el reloj lo tiene) que solo funciona con el reloj desbloqueado, si tiene bloqueo de pantalla; la lista de tus coches y los últimos datos leídos de Toyota, incluida la última posición aparcado; la imagen de tu coche. Las copias de seguridad están desactivadas. La contraseña guardada se borra en cuanto Toyota la rechaza.
- En el móvil (app opcional): nada. Solo reenvía los tokens al reloj por el enlace Bluetooth y, si elegiste guardarlos en el reloj, tu email y contraseña.
- Pulsar "Desvincular" en la app del reloj borra todo lo que la app guardó.

## Terceros

Toyota Motor Europe trata los datos de tu cuenta y de tu vehículo conforme a su propia política de privacidad cuando usas MyToyota o esta app. Los servicios de Google Play del móvil y del reloj transportan el mensaje Bluetooth entre las dos apps. GitHub sirve la búsqueda y la descarga de actualizaciones. No interviene ningún otro tercero.

## Permisos

La app del reloj solicita acceso a Internet y permiso para instalar apps. El segundo solo se usa para instalar una actualización de esta misma app, después de que la aceptes y de que el instalador de Android te pida confirmación. No usa ubicación, contactos, sensores ni ningún otro permiso.

## Contacto

Dudas o incidencias: abre un issue en https://github.com/Poiki/wear-for-toyota/issues.
