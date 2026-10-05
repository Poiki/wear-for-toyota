# Install Wear for Toyota / Instalar Wear para Toyota

## Español

Descarga los instaladores desde el [README del repositorio](https://github.com/Poiki/wear-for-toyota#easy-install-windows-and-macos). Los scripts están en el repositorio, no entre los archivos de la release.

- **Windows:** descarga `scripts/install.cmd` y ábrelo con doble clic. El archivo descarga automáticamente el instalador PowerShell.
- **Mac:** descarga `scripts/install.command`. En Terminal ejecuta `chmod +x ~/Downloads/install.command` y `~/Downloads/install.command`; después puedes abrirlo con doble clic.

El instalador descarga ADB de Google y el APK oficial de la última release, verifica su SHA-256, detecta el reloj y lo instala conservando los datos. No requiere Android Studio, Java ni permisos de administrador. Si macOS bloquea el archivo descargado, usa Terminal o «Abrir» en su menú contextual según los controles de tu sistema.

Primero activa las opciones de desarrollador y la depuración USB o inalámbrica en el reloj. Para Wi-Fi, reloj y ordenador deben compartir red: el instalador pide la dirección y el código de emparejamiento, y luego la dirección de conexión. Son puertos distintos. En USB, acepta la autorización en el reloj; Windows puede necesitar el controlador USB del fabricante. El script no puede activar la depuración ni aceptar esa autorización por ti.

Tras instalar, abre la app e inicia sesión con MyToyota. La app de móvil es opcional. Si ya tenías una copia firmada con otra clave, Android rechazará la actualización: el instalador nunca desinstala ni borra tus datos.

Instalación manual: descarga el APK del reloj de esta release y ejecuta `adb -s SERIAL install -r wear-for-toyota-watch-VERSION.apk`. Actualizaciones posteriores se ofrecen desde la propia app. Para más detalles, consulta el [README en español](https://github.com/Poiki/wear-for-toyota/blob/main/README.es.md).

## English

Get the console installers from the [repository README](https://github.com/Poiki/wear-for-toyota#easy-install-windows-and-macos), not from the release assets. On Windows download `scripts/install.cmd` and double-click it; it downloads its PowerShell installer automatically. On macOS download `scripts/install.command`, run `chmod +x ~/Downloads/install.command`, then open it in Terminal or double-click it.

The installer downloads Google's ADB tools and the latest official watch APK, verifies its SHA-256, selects a physical Wear OS watch and installs without removing existing data. No Android Studio, Java or administrator access is needed.

Enable developer options and USB or wireless debugging yourself, then authorize the computer on the watch. For Wi-Fi use the same network, supply the pairing address/code and the separate connection address. Windows USB connections may require the watch manufacturer's driver. These watch authorization steps cannot be automated.

Sign in on the watch after installation. The phone APK is optional. Updates with a different signing key are refused; the installer never uninstalls your app. For manual installation use `adb -s SERIAL install -r wear-for-toyota-watch-VERSION.apk`. See the [full README](https://github.com/Poiki/wear-for-toyota#install-your-own-copy).
