# Privacy Policy — Wear for Toyota

Last updated: 2026-10-02. [Versión en español más abajo.](#política-de-privacidad--wear-para-toyota)

Wear for Toyota is an unofficial, open-source app that lets you see and control your Toyota or Lexus car from a Wear OS watch. It is not affiliated with Toyota.

## What the app collects

Nothing. The app has no analytics, no crash reporting, no advertising and no servers of its own. The developer never receives any data from the app.

## What the app sends, and where

- Your MyToyota email and password are sent once, over HTTPS, to Toyota's own login service (`b2c-login.toyota-europe.com`) to obtain access tokens. The password is used only for that request and is never stored anywhere.
- Vehicle data (lock state, range, fuel or battery, mileage, climate, last parked position) and remote commands travel only between your watch (or the optional phone app) and Toyota's servers (`*.toyotaconnectedeurope.io`), exactly as with the official MyToyota app.
- The picture of your car is downloaded once from Toyota's image CDN.

## What the app stores on your devices

- On the watch: the Toyota access and refresh tokens, encrypted with a key held in the Android Keystore; the list of your cars and the last data read from Toyota, including the last parked position; the picture of your car. Backups are disabled.
- On the phone (optional app): nothing. It only forwards the tokens to the watch over the Bluetooth link.
- Tapping "Unlink" in the watch app deletes everything the app stored.

## Third parties

Toyota Motor Europe processes your account and vehicle data under its own privacy notice when you use MyToyota or this app. Google Play Services on the phone and watch carry the Bluetooth message between the two apps. No other third party is involved.

## Permissions

The watch app requests only Internet access. It does not use location, contacts, sensors or any other permission.

## Contact

Questions or concerns: open an issue at https://github.com/Poiki/wear-for-toyota/issues.

---

# Política de privacidad — Wear para Toyota

Última actualización: 2 de octubre de 2026.

Wear para Toyota es una app no oficial y de código abierto que permite ver y controlar tu Toyota o Lexus desde un reloj Wear OS. No está afiliada a Toyota.

## Qué recoge la app

Nada. La app no tiene analítica, ni informes de fallos, ni publicidad, ni servidores propios. El desarrollador nunca recibe ningún dato de la app.

## Qué envía la app y adónde

- Tu email y contraseña de MyToyota se envían una sola vez, por HTTPS, al servicio de inicio de sesión de Toyota (`b2c-login.toyota-europe.com`) para obtener los tokens de acceso. La contraseña solo se usa en esa petición y no se guarda en ningún sitio.
- Los datos del vehículo (cierre, autonomía, combustible o batería, kilometraje, climatizador, última posición aparcado) y los comandos remotos viajan únicamente entre tu reloj (o la app opcional del móvil) y los servidores de Toyota (`*.toyotaconnectedeurope.io`), igual que con la app oficial MyToyota.
- La imagen de tu coche se descarga una vez del CDN de imágenes de Toyota.

## Qué guarda la app en tus dispositivos

- En el reloj: los tokens de acceso y refresco de Toyota, cifrados con una clave que vive en el Android Keystore; la lista de tus coches y los últimos datos leídos de Toyota, incluida la última posición aparcado; la imagen de tu coche. Las copias de seguridad están desactivadas.
- En el móvil (app opcional): nada. Solo reenvía los tokens al reloj por el enlace Bluetooth.
- Pulsar "Desvincular" en la app del reloj borra todo lo que la app guardó.

## Terceros

Toyota Motor Europe trata los datos de tu cuenta y de tu vehículo conforme a su propia política de privacidad cuando usas MyToyota o esta app. Los servicios de Google Play del móvil y del reloj transportan el mensaje Bluetooth entre las dos apps. No interviene ningún otro tercero.

## Permisos

La app del reloj solo solicita acceso a Internet. No usa ubicación, contactos, sensores ni ningún otro permiso.

## Contacto

Dudas o incidencias: abre un issue en https://github.com/Poiki/wear-for-toyota/issues.
