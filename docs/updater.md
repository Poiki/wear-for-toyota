# Actualizador 0.3.5

## Correcciones 0.3.7

Se reprodujo una descarga real con el texto al 26 % y el aro vacío. La vista fija al 60 % usada en 0.3.5 no detectaba el fallo: la lambda del indicador conservaba el porcentaje inicial. `UpdateScreen` usa ahora `rememberUpdatedState` para que esa lambda observe los porcentajes siguientes; se conservan el indicador Wear, el rojo, las dimensiones y los estados indeterminados.

La comprobación automática se ejecutaba una sola vez al componer la app y reutilizaba durante 24 horas el resultado guardado. Ahora la Activity la ejecuta al entrar en `STARTED`, tanto en un inicio nuevo como al volver desde otra app. Se retira la espera diaria. La exclusión de comprobaciones simultáneas sigue activa; una oferta abierta o una instalación en curso se preservan, incluso si llega después una respuesta de GitHub. No hay comprobaciones periódicas en segundo plano. El botón manual conserva sus mensajes de error.

Las regresiones `check_frontend.py --updater-live` y `--updater-foreground` fallaron antes del cambio: aro al 0 % con texto al 26 %, y ninguna consulta al abrir tras una comprobación reciente. La primera transmite el APK debug real mediante un servidor local lento, compara los píxeles rojos del aro con varios porcentajes y cancela la sesión. Pausa la transmisión entre muestras para que UIAutomator pueda esperar un fotograma estable. Solo el APK debug permite HTTP a `127.0.0.1`; el manifiesto y la configuración de esa prueba quedan fuera de la release. La segunda comprueba el resultado de nuevas consultas en inicios consecutivos y al recuperar la tarea existente.

La implementación conserva [el indicador circular de Wear](https://developer.android.com/reference/kotlin/androidx/wear/compose/material3/package-summary) y usa [el ciclo de vida de Android](https://developer.android.com/topic/libraries/architecture/coroutines#restart) que ya se empleaba para abrir el instalador.

La revisión de limpieza queda acotada a estos fallos: se elimina la espera obsoleta, se repara la lectura de estado y se reutilizan Activity, Store y las pruebas existentes. El porcentaje desconocido y la confirmación de Android siguen siendo estados explícitos de compatibilidad. Los fallos de la comprobación automática no interrumpen el uso del coche; el botón manual informa del error. No se cambian Toyota, credenciales, permisos de publicación ni datos del usuario. La prueba física en OnePlus Watch 4 sigue pendiente.

Validación 0.3.7: 47 pruebas JVM; compilaciones debug/release y lint de reloj debug/release y móvil sin errores; ambas regresiones nuevas y las seis comprobaciones del instalador nativo superadas. Se verificaron firma compatible con 0.3.6 y ausencia de la configuración de red debug en el manifiesto de publicación. Python y diff correctos. Sin dependencias Android nuevas ni cambios de autenticación.

El fallo comunicado en un OnePlus Watch 4 era volver a Información después de aceptar la descarga. El código dibujaba `WaitFace` en el contenido de la app mientras Información seguía en un `Dialog` por encima. No mostraba bytes, porcentaje ni estimación; al terminar de descargar quitaba `updating` antes de que Android resolviera la instalación. Los errores solo llegaban a pantallas concretas, por lo que podían quedar ocultos. El permiso de instalación ya estaba resuelto en el reloj del usuario.

## Cambio

Se reutilizan `Store`, los diálogos Wear OS y el diseño cockpit. Un estado de instalación mantiene descarga, preparación, confirmación de Android y fallo. El progreso es otro diálogo situado por encima de Información y del acceso; al iniciar desde la oferta también se cierra Información. Los fallos permanecen visibles hasta cerrarlos.

El porcentaje usa bytes escritos y el tamaño del asset de GitHub o del HTTP. La estimación usa el caudal medio observado con reloj monotónico, después del primer segundo, y se actualiza como máximo cuatro veces por segundo. Sin tamaño conocido no se inventan porcentaje ni tiempo. La estimación no incluye el tiempo que el usuario tarde en confirmar Android ni la instalación del sistema.

El APK se transmite directamente a una sesión de `PackageInstaller`, sin otro archivo de descarga. Se comprueban tamaño y SHA-256 cuando GitHub los proporciona; assets antiguos sin digest conservan la verificación de firma/paquete/versión de Android. Se rechazan fuentes ajenas al repositorio y assets ambiguos. El receiver no exportado entrega la confirmación a `Store`; la Activity solo la abre estando `RESUMED`. Esto evita depender de un lanzamiento de interfaz desde un receptor en segundo plano. Véanse [sesiones de Android](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session?authuser=0) y [restricciones de apertura de actividades](https://developer.android.com/guide/components/activities/secure-bal).

Cancelar o fallar abandona la sesión propia; Android retira los datos de staging al finalizar. El inicio de la app y un nuevo intento limpian sesiones propias interrumpidas y archivos `update.apk`/`wear-for-toyota-watch*.apk` en los directorios privados de la app. No borran tokens, fotos, otros APK ni Descargas públicas. El APK instalado en `/data/app` es el programa que Android necesita ejecutar y lo administra el sistema.

Si el proceso muere, Android puede conservar su propia confirmación y completar la instalación. Si al abrir la app quedan sesiones interrumpidas sin estado activo recuperado, se limpian y se avisa de que hay que descargar de nuevo. No se implementa reanudación parcial. Los callbacks terminales solo afectan al ID actual; una respuesta tardía de una sesión cancelada no altera la siguiente operación.

## Validación

- 47 pruebas JVM, cero fallos: comparación y selección de versiones preservadas; bytes/porcentaje/ETA, tamaño desconocido, cancelación/IO, truncado, tamaño excesivo, hash incorrecto, metadata y limpieza acotada.
- Compilaciones debug/release de reloj y móvil correctas; lint de los tres módulos sin errores, 32 avisos. Firma de publicación compartida y compatible con 0.3.4.
- `check_frontend.py --updater`: 20 escenarios de interfaz en 227/192 dp: descarga por encima de Información en seis idiomas, tamaño desconocido, preparación, confirmación y fallo. Capturas locales revisadas.
- `check_frontend.py --updater-install`: confirmación nativa, cancelar, reinstalación real del APK debug del propio emulador, fallo real de descarga, limpieza de sesiones/archivos propios y callback tardío. También se mató el proceso con una sesión ya comprometida y la confirmación de Android abierta: siguió operativa, completó la instalación y no quedó staging. Los fixtures de esta prueba son locales y no requieren una cuenta Toyota.
- Python y `git diff --check` correctos. No se enviaron órdenes a vehículos ni se probó el firmware del OnePlus Watch 4.

## AI SLOP CLEANUP REPORT

- **Scope:** actualizador de `Releases`, `Store`, Activity, About, diálogos, traducciones, versiones y pruebas; sin cambios en Toyota, autenticación ni datos del vehículo.
- **Behavior Lock:** pruebas JVM del transporte/medidas antes de implementar; comparación de versiones existente preservada; pruebas Android de ciclo de sesión y cancelación.
- **Cleanup Plan:** reparar orden de ventanas; sustituir el booleano que terminaba demasiado pronto por un estado de instalación; progreso por bytes; limpieza propia y errores visibles; regresiones y revisión independiente.
- **Fallback Findings:** progreso oculto y errores por ruta eran fallos de visibilidad, corregidos. Tamaño/digest ausentes se tratan como compatibilidad acotada: porcentaje/ETA desconocidos y validación final de Android. Una interrupción exige un mensaje explícito, sin fingir reanudación ni éxito. No se añaden permisos para borrar Descargas públicas.
- **UI/Design Findings:** componentes y colores existentes; indicador determinable cuando hay tamaño, textos accesibles y cancelación. Sin dependencias nuevas ni pantallas comerciales.
- **Passes Completed:** estado antiguo eliminado; centralización de descarga/limpieza; errores explícitos; pruebas reforzadas. Revisión independiente detectó callbacks tardíos y pérdidas de estado al morir el proceso; ambos casos están cubiertos.
- **Quality Gates:** JVM, compilación, lint y UI PASS. Revisión estática acotada PASS tras corregir hallazgos; sin escáner externo ni hardware del usuario.
- **Changed Files:** `Releases` y tests: streaming, cálculo e integridad; `Store`: sesiones y resultados; `MainActivity`/`AboutScreen`/`Screens`: visibilidad y confirmación; recursos: seis idiomas; `check_frontend`: pruebas demo/nativas; Gradle/documentos: 0.3.5 y límites.
- **Remaining Risks:** el fabricante puede personalizar el instalador; porcentaje solo de descarga; ETA variable; muerte del proceso puede exigir repetir la descarga. Los APK descargados manualmente fuera del almacenamiento privado no se borran.
