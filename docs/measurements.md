# Mediciones

Presupuestos en `technical-decisions.md` §9. Cada fila es una medición real, no una estimación.

## 2026-10-01 — base del reloj (Compose M3, sin comandos)

Entorno: emulador Wear OS 5 (API 34, `wearos_large_round`, 2 vCPU, 512 MB), build `release` con R8 y shrinkResources, firmado con la clave debug.

| Métrica | Medido | Presupuesto | Cómo |
|---|---|---|---|
| APK release reloj (fichero / descarga) | 2,06 MB / 0,99 MB | ≤ 4 MB | `apkanalyzer apk file-size` / `download-size` |
| APK release teléfono | 2,30 MB / 1,10 MB | — | idem |
| APK debug reloj | 30,6 MB | — | sin R8; no representativo |
| Arranque en frío (release, 3 medidas) | 850 / 831 / 799 ms | ≤ 1,0 s | `am force-stop` + `am start -W` → `TotalTime` |
| Arranque en frío (debug) | 2,5–3,0 s | — | idem; sin R8 ni baseline profile compilado |
| RAM PSS en pantalla principal (release) | 24,3 MB | ≤ 60 MB | `dumpsys meminfo` |
| RAM PSS (debug) | 71,3 MB | — | idem |
| Tests unitarios del núcleo | 3/3 OK (fixtures reales de pytoyoda) | — | `gradlew :core:testDebugUnitTest` |

Comportamiento verificado en el emulador con tokens inyectados por adb (solo builds debug):

| Escenario | Resultado |
|---|---|
| Sin tokens | Pantalla "Vincula el reloj desde la app Toyota Wear del teléfono" |
| Tokens caducados con refresh inválido | Toyota rechaza el refresh → "Sesión caducada. Vincula de nuevo desde el móvil." y los tokens se borran |
| Tokens "frescos" pero inválidos | `GET /v2/vehicle/guid` responde `APIGW-403` → "Toyota rechazó el acceso (APIGW-403)."; se conserva el botón Actualizar |

## 2026-10-01 — reloj real OnePlus Watch 3 (Wear OS 6, API 36, 466×466)

| Build | Arranque en frío | dexopt | Sensación |
|---|---|---|---|
| debug (sin R8) | no medido; app `run-from-apk` (sin compilar, solo JIT) | run-from-apk | "lag terrible" reportado por el usuario |
| release (R8 + shrink), primer arranque | 2,2 s | verify (perfil aún no instalado) | — |
| release tras `cmd package compile -m speed-profile` | 897 / 811 / 679 ms | speed-profile | fluida |

RAM PSS en release: 23,9 MB. APK release 2,61 MB (con iconos vectoriales y Compose Foundation text).

Lección: en reloj nunca evaluar rendimiento con un build debug; instalar siempre `assembleRelease` (firmado con la clave debug) y, tras el primer arranque, dejar que ProfileInstaller aplique el baseline profile (o forzarlo con `cmd package compile -m speed-profile -f <pkg>`).

## 2026-10-02 — primer comando real (Corolla Hybrid TS MY24, reloj OnePlus Watch 3 por Wi-Fi)

| Paso | Hora | Observación |
|---|---|---|
| Toque "Cerrar" | 00:47:04 | POST /v1/global/remote/command door-lock aceptado al instante |
| "Verificando…" visible | 00:47:14 | wake POST /v1/remote/status + sondeo 5/5/10/10 s |
| Estado actualizado "Hoy, 00:47" | 00:47:38 | lastUpdateTimestamp avanzó → ~24 s desde el envío hasta la confirmación |

Datos reales leídos: Autonomía 264 → 251 km entre dos aperturas de la app (coche en uso), 43 % combustible, 45.334 km. Arranque en frío v2/v3 en el reloj: 0,9–1,9 s en la primera ejecución tras instalar (perfil sin compilar), 0,7–0,9 s después.

Pendiente de medir con dispositivos reales: tráfico por apertura (`dumpsys netstats`), batería en reposo (`batterystats`), tiempos reales de clima y apertura.

## 2026-10-02 — release 0.1.0

| Artefacto | Tamaño |
|---|---|
| watch-release.apk (R8, 6 idiomas, icono vectorial) | 2,87 MB |
| phone-release.apk | 2,30 MB |

Tests del núcleo: 4/4.

## 2026-10-03 — release 0.2.0

| Artefacto | Tamaño |
|---|---|
| watch-release.apk (+ contraseña opcional y auto-update) | 2,88 MB (+6,8 KB) |
| phone-release.apk | 2,30 MB |

Tests del núcleo: 5/5. Comprobación de actualizaciones: 1 petición a `api.github.com` como mucho al día (solo tras una respuesta correcta); en el emulador la descarga del APK de 2,9 MB a la sesión de PackageInstaller tardó ~1 s.

## 2026-10-03 — arranque en el reloj real y release 0.2.1

Arranque en frío de la 0.2.0 en el OnePlus Watch 3 (Wear OS 6, `speed-profile`, 4 medidas con marcas de tiempo temporales):

| Tramo | Tiempo |
|---|---|
| Inicio del proceso → `onCreate` | 108–139 ms |
| `Store.init` en el hilo principal | 89–107 ms: descifrar tokens con el Keystore 56–60, prefs 10–13, JSON + snapshot 8–18, una imagen 8–11 |
| Primer frame | 268–336 ms desde el inicio del proceso |

La 0.2.1 saca de `Store.init` el descifrado (se hace en la primera petición, ya en segundo plano) y el parseo del snapshot (ya no se muestran datos guardados), y desactiva `EmojiCompatInitializer`: quedan prefs + JSON + imagen (~25 ms estimados). Pendiente de confirmar en el reloj. OJO: en el cargador el overlay de carga de OnePlus tapa la app y `am start -W` no da `TotalTime`; las marcas de tiempo en logcat sí funcionan.

| Artefacto | Tamaño |
|---|---|
| watch-release.apk (dos esferas, pager, texto autoajustable) | 2,95 MB (+65 KB) |
| phone-release.apk | 2,30 MB |

Comprobado en el emulador a 227 dp y a 192 dp (densidad 378) en los 6 idiomas: ningún texto parte línea ni se corta.
