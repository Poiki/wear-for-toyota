# Apps Wear OS de fabricantes de coches — referencia UX (estado a 2026-10-01)

Objetivo: inventario de qué OEM publican app para Wear OS (y qué hacen), como referencia para una app Toyota Wear OS.
Método: listados de Google Play (descargados 2026-10-01), páginas de soporte OEM, notas de prensa, reviews (9to5Google, Android Police, Android Authority), foros y reseñas de Play. Varios foros (tollbit) y YouTube no fueron legibles: lo que viene de resúmenes de búsqueda se marca `(sin confirmar)`.
Las fuentes se citan como `[Sn]` y se listan en §5.

## 1. Tabla resumen

| Marca (región) | App (package) | Wear OS | Standalone | Funciones en el reloj | Confirmación de unlock | Tiles / Complications | Fuente |
|---|---|---|---|---|---|---|---|
| Kia (EU) | Kia App (`com.kia.oneapp.eu`), 2.4★ | Sí, Wear OS 3+ | No: "requires connection with the mobile Kia App" | "vehicle remote control and vehicle status management" (sin detalle público) | (sin confirmar) | Watch face + Complication anunciados | [S1] |
| Kia (US) | Kia Access (`com.myuvo.link`), 4.7★ | Sí ("Also available for Wear OS") | No: companion, "cannot be used on its own" | Remote start + climate, lock/unlock, status; Apple Watch idéntico; walkthrough en Galaxy Watch 4 | PIN de Kia Connect se gestiona en la cuenta; uso en reloj (sin confirmar) | (sin confirmar) | [S2][S3][S4][S5] |
| Kia (India/Asia) | Kia Connect (`com.kia.uvo.in.prd`), 3.4★ | Sí (Galaxy Watch, Wear OS 2+) | No: hay que activar "Link Smart Watch" en la app móvil; si no hay sesión → "communication error" | Control remoto y estado "depending on your vehicle options" | (sin confirmar) | No | [S6] |
| Hyundai (US) | MyHyundai with Bluelink (`com.stationdm.bluelink`), 4.6★ | Sí (APK Wear OS separado) | No (companion) | Remote start/stop, lock/unlock, horn/lights, find car; menú o voz ("Start my car", "Lock my car") | (sin confirmar) | No | [S7][S8][S9][S10] |
| Hyundai (EU) | myHyundai (`com.hyundai.oneapp.eu`), 2.7★ | Sí, Wear OS 3+ | No: "requires pairing with the mobile myHyundai app" | Control + estado; "dedicated watch faces and complications" | (sin confirmar) | Watch faces + complications | [S11] |
| Hyundai (India) | Hyundai Bluelink (`com.hyundai.india.bluelink.prd`) | Sí (Galaxy Watch) | No ("Link Smart Watch", igual que Kia) | Control remoto y estado según vehículo | (sin confirmar) | No | [S12] |
| Genesis (US/CA) | Genesis Intelligent Assistant (`com.stationdm.genesis` / `com.canada.genesis`) | Sí | No: la app móvil debe estar logueada; "uses WiFi/cellular to talk to the vehicle" | Remote start, lock/unlock, horn/lights, find car, parking-meter reminders (US); menú o voz | (sin confirmar) | No | [S13][S14][S15] |
| Ford (US/CA) | Ford (ex FordPass) (`com.ford.fordpass`), 4.7★ | Sí, desde nov-2024 (tras ~9 años sin Wear OS) | No: hay que abrir la app móvil periódicamente o te desloguea | Lock/unlock, remote start, fuel/battery, range, charging status (unplugged/plugged/target), location, horn/lights | PIN o patrón obligatorio en el reloj (el usuario lo nota "repetitivo") | Sin tile (queja de usuarios) | [S16][S17][S18][S19][S20] |
| Lincoln (US) | Lincoln (`com.lincoln.lincolnway`) | No (Play sin mención) | — | Apple Watch sí (watchOS 10) | — | — | [S21][S22] |
| VW (US/CA) | myVW (`com.vw.carnet.releaseca`) | Sí, desde 14-nov-2025 (MY2020+/MY22+) | No: sesión en móvil y "smartwatch must be in close proximity to the phone" | Lock/unlock, lock status, vehicle status, honk & flash; ICE: remote start/stop + fuel; EV: charge start/stop, charge status, climate start/stop | (sin confirmar) | (sin confirmar) | [S23][S24][S25][S26] |
| VW (EU) | We Connect (Wear OS) APK separado; app Volkswagen (`com.volkswagen.weconnect`) sin mención de reloj | Histórico (v5.15.1); vigencia (sin confirmar) | No | "Parking Timer", lock status (sin confirmar) | (sin confirmar) | (sin confirmar) | [S27] |
| Škoda (EU) | MyŠkoda (`cz.skodaauto.myskoda`), 2.9★ | Sí | (sin confirmar) | "check your current range and charging status, and lock or unlock your car"; Apple Watch: battery, lock status, heating | (sin confirmar) | (sin confirmar) | [S28][S29] |
| SEAT/CUPRA | My CUPRA (`com.cupra.mycupra`) | No evidencia | — | — | — | — | [S30] |
| Audi | myAudi (`de.myaudi.mobile.assistant`) | No (solo Apple Watch/Digital Key) | — | — | — | — | [S31] |
| Porsche (global) | My Porsche Wear OS (`com.porsche.wear`, dev id `de.porsche.wear`), 3.6★, 1K+ | Sí, app propia de reloj (jul-2025) | Parcial: "active connection to the Google Wear OS app is required for login" | Vehicle status, range/e-range, battery, charging status, remote climate; lock/unlock y precondition según foro (sin confirmar) | (sin confirmar) | Complications (usuarios: a veces no cargan) | [S32][S33][S34] |
| BMW / MINI | My BMW (`de.bmw.connected.mobile20.row`), MINI (`de.mini.connected.mobile20.row`) | No (Play sin mención; Digital Key Plus solo Apple Watch) | — | — | — | — | [S35][S36] |
| Mercedes-Benz | Mercedes-Benz app (`com.daimler.ris.mercedesme.ece.android`) | No; Apple Watch nov-2024 (EU primero) | — | Apple Watch: lock/unlock, range, fuel/battery, locate + ruta peatonal, compass, "windows closed" check | — | — | [S37][S38][S39] |
| Volvo (oficial) | Volvo Cars (`se.volvo.vcc`), 3.5★ | No (Android Wear retirado; reseñas lo piden) | — | Apple Watch: parking climate, remote start, lock/unlock, honk/flash, range, posición | — | — | [S40][S41][S42] |
| Volvo (3rd party) | VConnect (`com.kashamlg.volvowearos`), 4.2★ | Sí, solo Wear OS | Sí: login OAuth con Volvo ID, "no phone companion required" | Locate, fuel/battery, DTE, doors/windows, lock/unlock, remote start 15 min, climate, honk/flash, multi-vehículo | (sin confirmar) | 1 tile (premium) + 3 complications (battery, range, lock) | [S43][S44] |
| Polestar | Polestar app | No (solo Polestar 1 histórico; P2 cancelado, sin confirmar) | — | Apple Watch via watchOS 26 Controls: lock, climate preconditioning | — | — | [S45][S46][S47] |
| Tesla (oficial) | Tesla app | No; Apple Watch oficial dic-2024 | — | Pantalla 1: lock/unlock, frunk, climate on/off, SoC; swipe: trunk, charge port, flash, honk; complications | Sin confirmación visible (sin confirmar) | Complications | [S48][S49] |
| Tesla (3rd) | Tessie (`io.tessie.app`), 4.7★ | Sí: app + tile + complications; Phone & Watch Key BLE | Sí tras login | Control completo; auto-unlock / walk-away lock | — | Tile + complications | [S50][S51][S52] |
| Tesla (3rd) | Watt Key (`com.joshendy.wattkey`, 4,99 $), 3.1★ | Sí, solo reloj | Sí: BLE directo, "no account, no cloud" | Lock/unlock, puertas, frunk, trunk, charge port; unlock on approach; multi-vehículo | Ninguna (es llave) | Tile configurable | [S53][S54] |
| Tesla (3rd) | Tesla Key Wear (`li.power.app.wearos.teslanak`), 4.1★ | Sí | Sí: NFC HCE emula key card | Unlock/lock por NFC en el pilar B | Ninguna | No | [S55] |
| Tesla (3rd) | DRIVE Electric for Tesla (`no.klokkprojects.driveelectricfortesla`), 3.4★ | Sí | Sí tras login en app teléfono (tokens al reloj) | NFC + BLE + Fleet API (suscripción); "On Approach"; mapa/compás | PIN opcional + "confirmation prompts" opcionales | 6 tiles + complication batería/carga | [S56][S57] |
| Tesla (OSS) | HA Watch for Tesla (GitHub, vía Home Assistant) | Sí | Sí (HTTPS a HA) | Lock/unlock, climate, charging, trunk | Sin diálogo: verifica estado a 1,5/5/12 s y avisa si no cambió | Tile (Lock/Unlock + 2 botones) + complication batería | [S58] |
| Rivian | Rivian (`com.rivian.android.consumer`), 3.1★ | No ("Wear OS support is still missing", reseña 2026) | — | Apple Watch feb-2026: lock/unlock, vent windows, alarm, cabin temp, charge target (Digital Crown), 4 quick controls configurables, complication batería, Digital Key | — | — | [S59][S60][S61] |
| Lucid | Lucid Motors (`com.lucidmotors.mobile.prod`), 3.0★ | No | — | Apple Watch (watchOS 10): "view and control certain vehicle features" | — | — | [S62] |
| GM (Chevrolet/GMC/Cadillac/Buick) | myChevrolet (`com.gm.chevrolet.nomad.ownership`) etc. | No ("Yet not available for WearOS!") | — | Apple Watch: start/stop, lock/unlock, horn/lights, estado EV (servicio suspendido mar–jun 2025) | — | — | [S63][S64][S65] |
| Nissan (US, LEAF) | NissanConnect EV & Services (`com.aqsmartphone.android.nissan`), 3.9★ | Sí | No: "companion app and cannot be used without first downloading the app and logging in" | Charging, climate, battery status | (sin confirmar) | (sin confirmar) | [S66] |
| Nissan (MX/LatAm) | NissanConnect Services (`mx.nissan.connectservices`) | Sí | (sin confirmar) | Lock/unlock, lights/horn, engine start/stop, locate, alertas | (sin confirmar) | (sin confirmar) | [S67] |
| Nissan (US/EU otros) | MyNISSAN (`com.nissan.mynissan`), NissanConnect Services | Wear OS (sin confirmar; Play sin mención) | — | Apple Watch: queja de re-login forzado en cada comando y actualizaciones lentas | — | — | [S68][S69] |
| Renault (Corea) | My Renault (`com.myrsm.android`) | Sí, Galaxy Watch 4+/Wear OS 3+ (sep-2023) | No: "log in first on the smartphone My Renault app" | Remote start/AC, lock/unlock, horn/lights, locate, send destination (openR link) | (sin confirmar) | Tiles ("We provide tiles") | [S70][S71] |
| Renault (EU) | My Renault (`com.renault.myrenault.one.fr`), 1.9★ | No evidencia | — | — | — | — | [S72] |
| Stellantis EU (Jeep/FIAT/Alfa) | Jeep (`com.fca.myconnect`), FIAT (`.fiat`), My Alfa Connect (`.alfaromeo`) | Sí: "Compatible Wear OS smartwatches can also access the app and its basic features" | No (reseña: "only works if you access the Jeep app on your mobile") | "Basic features" (sin detalle) | (sin confirmar) | (sin confirmar) | [S73][S74][S75] |
| Peugeot/Citroën/DS/Opel | MyPeugeot (`com.psa.mym.mypeugeot`) etc. | No | — | — | — | — | [S76] |
| Honda | HondaLink (`com.honda.hondalink.connect`), 2.0★ | No (Apple Watch sí); "Honda Connect" India en "any Wear OS" (sin confirmar) | — | — | — | — | [S77][S78] |
| Mazda | MyMazda (`com.interrait.mymazda`) | No (reseña pide "android watch app"); Apple Watch sí | — | — | — | — | [S79][S80] |
| Subaru | MySubaru (`com.subaru.telematics.app.remote`) | No; Apple Watch desde v3.1.0 (oct-2024) | — | Apple Watch: lock/unlock, start/stop con/sin climate, cambio de VIN | — | — | [S81][S82] |
| Toyota (US) | Toyota (`com.toyota.oneapp`), 4.7★ | Sí: "Companion Wear OS App ... Remote Services" | No: Bluetooth con el teléfono obligatorio aunque el reloj tenga LTE; comando reloj→teléfono→DCM y alertas de vuelta por la misma vía | Lock/unlock, engine start/stop, vehicle status (fuel/battery), locate/last parked, guest driver alerts; táctil o voz | PIN de Remote Connect; PIN para comandos de voz configurable | (sin confirmar) | [S83][S84][S85][S86][S87] |
| Toyota (AU/NZ) | myToyota Connect (`com.au.toyota.oneapp`), 4.7★ | Sí: "Companion Wear OS App" | No | "remote commands and vehicle status" | (sin confirmar) | (sin confirmar) | [S88] |
| Toyota (EU) | MyToyota (`com.toyota.oneapp.eu`), 3.1★ | No (Play sin mención; en iOS solo iPhone, sin watchOS) | — | — | — | — | [S89][S90] |
| Lexus (US) | Lexus (`com.lexus.oneapp`), 4.7★ | Sí: "Companion Wear OS App" | No | Start/stop, lock/unlock, status, locate, notificaciones; Samsung Gear no soportado | PIN (igual que Toyota) | (sin confirmar) | [S91][S92] |
| Lexus (AU / EU) | Lexus Connected (`com.au.lexus.oneapp`) sí; Lexus Link+ (`com.lexus.oneapp.eu`) no | AU sí / EU no | — | AU: remote commands + status | — | — | [S93][S94] |
| MG (India / EU) | MG iSMART (`com.saicmotor.iov.india`) sí; MG i-SMART EU (`com.saicmotor.iov.europe`, 1.3★) sin mención | India sí | (sin confirmar) | Lock/unlock, AC, honk/lights, TPMS | (sin confirmar) | (sin confirmar) | [S95][S96] |
| BYD | BYD Auto app + Samsung Smart Card | No app Wear OS; Galaxy Watch 7/8/Ultra como llave NFC (China, jul-2025) | — | Solo llave digital NFC | — | — | [S97] |
| Xpeng / NIO / Zeekr | — | No evidencia (Zeekr: llave Apple Watch) | — | — | — | — | [S98] |
| Multi-marca 3rd party | KeyConnect, KeyAccess ("40+ marcas") | Sin evidencia de versión Wear OS (sin confirmar) | — | — | — | — | [S99] |

## 2. Patrones de UX comunes

**Qué llevan al reloj (conjunto común, 8–10 OEM):**
- Estado glanceable: lock state, fuel/battery %, range, charging status (plugged/charging/target), última posición. Ford, VW, Škoda, Porsche, Mercedes (watchOS), Tesla, VConnect [S16][S23][S28][S32][S38][S48][S43].
- Acciones: lock, unlock, remote start/stop (ICE) o climate start/stop (EV), horn & lights / find car. Es el núcleo de Hyundai/Kia/Genesis/Toyota/Lexus/Ford/VW/Nissan MX/Renault KR [S7][S13][S16][S23][S67][S70][S83].
- Un "find my car": posición en mapa (Mercedes añade ruta peatonal y brújula; DRIVE Electric añade brújula) [S38][S56].
- Voz como atajo de comando (Hyundai/Kia/Genesis/Toyota: "Start my car", "Lock my car") [S8][S13][S86].

**Qué dejan fuera (casi todos):** temperatura fina y asientos/volante calefactados (solo Rivian watchOS cabin temp y Subaru watchOS "start with climate"), trunk/frunk (solo Tesla y Rivian), inicio/parada de carga (VW EV, Rivian charge target; Ford no, queja), alertas/geofences, pagos, Digital Key (solo Tesla 3rd party, BYD/Zeekr NFC, Apple Wallet) [S20][S23][S48][S59][S81].

**Dependencia del teléfono (regla, no excepción):** Toyota exige Bluetooth con el móvil incluso con LTE; Kia/Hyundai India exigen "Link Smart Watch" + sesión; Genesis/Nissan/Renault/Stellantis exigen app móvil logueada; VW exige "proximidad al teléfono"; Ford desloguea si no se abre la app móvil "de vez en cuando"; Porsche necesita la app Wear OS del teléfono solo para el login. Los únicos standalone reales son terceros (VConnect OAuth, Watt Key BLE, DRIVE Electric tokens, Tesla Key Wear NFC) [S6][S12][S15][S17][S26][S32][S43][S53][S56][S66][S70][S84].

**Confirmación de acciones sensibles:** nadie documenta "swipe/long-press to unlock". Las barreras observadas son: (a) PIN/patrón de bloqueo del reloj obligatorio (Ford), (b) PIN de servicio en la cuenta (Toyota Remote Connect, Kia Connect), opcional para voz (Toyota), (c) PIN opcional en app (DRIVE Electric), (d) sin confirmación + verificación posterior del estado (HA Watch for Tesla, llaves BLE/NFC) [S17][S3][S86][S56][S58].

**Comando remoto y estados intermedios:** el patrón mejor documentado es el OSS HA Watch for Tesla: ejecuta sin diálogo, muestra "Sending to the car…", relee el estado a 1,5/5/12 s, banner azul/naranja ~1 min, háptico, nunca reintenta solo, bloquea el botón 10 s contra doble tap, y muestra "No data" hasta tener estado real [S58]. En OEM, lo visible son los fallos: Genesis "spinning endlessly" y errores de auth; Jeep "just buffers"; Tessie cancela la orden si el coche está dormido; Toyota/Lexus devuelven el resultado como notificación por la misma vía reloj→teléfono [S15][S74][S52][S84]. Latencias reportadas: 20 s+ para unlock BLE (Watt Key), 2–5 min en app Mazda (teléfono) [S54][S79].

**Login/sesión:** modelo dominante = el reloj hereda la sesión del teléfono (Toyota, Kia, Hyundai, Genesis, Nissan, Renault, Ford). Problemas recurrentes: la sesión del reloj caduca o pide re-login (Nissan watchOS, Genesis, Porsche "can't connect to my iphone for authentication"), toggles ocultos ("Link Smart Watch") y versiones de app desalineadas tras updates [S69][S15][S34][S6]. Solo terceros usan OAuth propio en el reloj (VConnect) o tokens transferidos una vez (DRIVE Electric) [S43][S56].

**Tiles/complications:** nuevos en 2025–2026: Kia App EU, myHyundai EU (watch faces + complications), Porsche (complications), Renault KR (tiles), Tessie/VConnect/Watt Key/DRIVE Electric (tiles + complications). Ford y Toyota no los anuncian; usuarios de Ford los piden [S1][S11][S32][S70][S50][S43][S53][S56][S20].

## 3. Quejas recurrentes
1. Dependencia del teléfono: "never connects, just buffers, only works if you access the app on your mobile" (Jeep/Galaxy Watch) [S74]; Ford obliga a abrir la app móvil o te desloguea [S17][S19].
2. Sesión/autenticación: Genesis "authentication errors requiring phone app login despite already being logged in" [S15]; Porsche watch sin poder autenticar contra el iPhone (TAG Heuer) [S34]; NissanConnect watchOS obliga a re-login en cada comando (sin confirmar, foro) [S69].
3. Lentitud y timeouts: Tessie lento al despertar el coche y cancela la petición [S52]; Watt Key 20 s para abrir y Bluetooth "disconnected" [S54]; Nissan watch "multiple times longer to update and often don't work" [S68].
4. Fricción de seguridad: PIN/patrón obligatorio en el reloj (Ford) percibido como molesto [S17][S19].
5. Funciones que faltan: trunk/frunk, start/stop de carga, tile (Ford) [S20]; "add android watch app" (Mazda) [S79]; "Wear OS support is still missing" (Rivian) [S59]; "support for WearOS devices" (Volvo) [S40].
6. Distribución/compatibilidad: app Wear OS "present in the APK but not installable from Play", sideload con Wear Installer (Hyundai 2022) [S9]; Samsung Gear/Tizen no soportado (Toyota, Lexus, Kia) [S84][S92][S6]; complications que no cargan (Porsche) [S33].
7. Batería/background: Wear OS limita el escaneo BLE en background, el usuario debe abrir la app o un tile (DRIVE Electric) [S57].
8. Ratings bajos de las apps madre (Kia App 2.4★, myHyundai 2.7★, MyŠkoda 2.9★, My Renault EU 1.9★, MG EU 1.3★): la app de reloj hereda la reputación [S1][S11][S28][S72][S96].

## 4. Lecciones aplicables a Toyota
1. Conjunto mínimo = el consenso del mercado: lock/unlock, remote start/climate start-stop, lock state, fuel/battery + range, charging status, última posición, horn/lights. No añadir ajustes finos en v1 (nadie lo hace salvo Rivian en watchOS) [§2].
2. Diseñar primero el estado intermedio del comando: "Enviando…" → "Enviado" → "Confirmado por el coche / No confirmado", con relectura de estado programada (1,5/5/12 s), háptico, y sin reintentos automáticos ni doble tap (patrón HA Watch) [S58]. Toyota ya devuelve el resultado como notificación por la vía reloj→teléfono [S84]: aprovecharlo.
3. Mostrar "sin datos" en vez de valores placeholder; nunca permitir lock/unlock sobre un estado desconocido [S58].
4. Unlock: una barrera única y explicable. Opciones usadas: PIN/patrón del reloj (Ford) o PIN de servicio (Toyota Remote Connect). Evitar pedir PIN en cada comando (queja Ford) y evitar re-login (queja Nissan/Genesis) [S17][S86][S69][S15].
5. Sesión robusta: heredar la sesión del teléfono es el estándar, pero hay que manejar expiración con un mensaje accionable ("Abre la app Toyota en el teléfono") y no con spinner infinito [S15][S74].
6. Reducir dependencia del teléfono donde sea posible: Toyota hoy exige Bluetooth con el móvil incluso con LTE [S84]; mínimo, cachear el último estado y la posición para que el reloj siga siendo útil sin teléfono.
7. Tiles y complications desde el inicio: es lo que piden los usuarios de Ford y lo que Kia/Hyundai/Porsche/Renault acaban de añadir; un tile Lock/Unlock + complication de batería/range es el patrón repetido [S20][S1][S11][S43][S58].
8. Voz como atajo ("Lock my car") existe en Hyundai/Kia/Genesis/Toyota US: mantener paridad si la app móvil ya lo soporta [S8][S86].
9. Compatibilidad explícita: Wear OS 3+, listar relojes probados (Galaxy Watch 4+, Pixel Watch), y evitar que el reloj quede sin instalar desde Play (caso Hyundai 2022) [S9][S11].
10. Publicar el feature list del reloj en Play y en soporte: la mayoría de OEM solo dicen "companion Wear OS app", y eso genera reseñas negativas por expectativas ("The watch app doesn't allow you to check anything") [S75][S88].

## 5. Fuentes
- [S1] https://play.google.com/store/apps/details?id=com.kia.oneapp.eu
- [S2] https://play.google.com/store/apps/details?id=com.myuvo.link
- [S3] https://owners.kia.com/us/en/uvo-support.html
- [S4] https://owners.kia.com/us/en/about-uvo-link.html
- [S5] https://www.youtube.com/watch?v=8Yt8uqxPhII (Kia EV6 Android Wear Smartwatch App Walkthrough on Galaxy Watch 4) · https://www.k5owners.com/threads/kia-connect-companion-app-for-apple-watch-and-android-watch-wear-os.1050/ (sin confirmar)
- [S6] https://play.google.com/store/apps/details?id=com.kia.uvo.in.prd
- [S7] https://play.google.com/store/apps/details?id=com.stationdm.bluelink
- [S8] https://www.prnewswire.com/news-releases/hyundai-blue-link-smartwatch-app-available-for-download-on-google-play-300045418.html
- [S9] https://xdaforums.com/t/hyundai-blue-link-app-not-loading.4426553/ · https://www.apkmirror.com/apk/hyundai-motor-america/myhyundai-with-bluelink-wear-os/ (sin confirmar)
- [S10] https://www.bgr.com/2254818/galaxy-watch-apps-work-with-your-car/
- [S11] https://play.google.com/store/apps/details?id=com.hyundai.oneapp.eu
- [S12] https://play.google.com/store/apps/details?id=com.hyundai.india.bluelink.prd
- [S13] https://play.google.com/store/apps/details?id=com.stationdm.genesis · https://play.google.com/store/apps/details?id=com.canada.genesis
- [S14] https://www.genesis.com/ca/en/owners/digital-services/genesis-connected-services.html
- [S15] https://genesisowners.com/genesis-forum/threads/samsung-watch-now-support-the-genesis-app.46030/
- [S16] https://www.ford.com/support/how-tos/fordpass/fordpass-remote-features/which-remote-features-are-available-to-use-with-my-apple-watch-and-fordpass/ · https://play.google.com/store/apps/details?id=com.ford.fordpass
- [S17] https://9to5google.com/2024/12/10/ford-brings-car-controls-back-to-wear-os-with-new-app/
- [S18] https://www.androidauthority.com/ford-smartwatch-control-3507506/
- [S19] https://www.androidpolice.com/ford-wear-os-app/
- [S20] https://techissuestoday.com/fordpass-app-finally-arrives-on-wear-os/
- [S21] https://play.google.com/store/apps/details?id=com.lincoln.lincolnway
- [S22] https://apps.apple.com/us/app/the-lincoln-way/id1141482401?platform=appleWatch
- [S23] https://insideevs.com/news/778961/volkswagen-smartwatch-controls-us/
- [S24] https://www.arenaev.com/volkswagen_puts_your_car_key_on_your_wrist-news-5315.php
- [S25] https://play.google.com/store/apps/details?id=com.vw.carnet.releaseca
- [S26] https://phandroid.com/2025/11/14/own-a-modern-volkswagen-car-you-can-now-unlock-it-with-your-wear-os-watch/ · https://media.vw.com/releases/1892
- [S27] https://www.apkmirror.com/apk/volkswagen-ag/we-connect-wear-os/ · https://play.google.com/store/apps/details?id=com.volkswagen.weconnect
- [S28] https://play.google.com/store/apps/details?id=cz.skodaauto.myskoda
- [S29] https://apps.apple.com/gb/app/my%C5%A1koda/id1632202810 (sin confirmar)
- [S30] https://play.google.com/store/apps/details?id=com.cupra.mycupra
- [S31] https://play.google.com/store/apps/details?id=de.myaudi.mobile.assistant · https://www.audi.com/en/press-releases/relaunch-of-the-myaudi-app-16868
- [S32] https://play.google.com/store/apps/details?id=com.porsche.wear · https://wear-pzls.andro.io/
- [S33] https://www.taycanforum.com/forum/threads/my-porsche-app-features-on-a-smartwatch.28457/ (sin confirmar)
- [S34] Reseña Play "Ton V", 2-dic-2023, en https://play.google.com/store/apps/details?id=com.porsche.wear
- [S35] https://play.google.com/store/apps/details?id=de.bmw.connected.mobile20.row · https://play.google.com/store/apps/details?id=de.mini.connected.mobile20.row
- [S36] https://u11.bimmerpost.com/forums/showthread/2149170/bmw-digital-key-plus-and-smartwatch-with-uwb (sin confirmar)
- [S37] https://play.google.com/store/apps/details?id=com.daimler.ris.mercedesme.ece.android
- [S38] https://9to5mac.com/2024/11/26/mercedes-benz-unveils-all-new-apple-watch-app/
- [S39] https://mbworld.org/forums/general-mercedes-discussion/860043-mercedes-me-samsung-smart-watch.html (sin confirmar)
- [S40] https://play.google.com/store/apps/details?id=se.volvo.vcc (reseña ago-2025 pidiendo Wear OS)
- [S41] https://www.swedespeed.com/threads/volvo-no-longer-supports-android-wear.647846/ (sin confirmar) · https://www.engadget.com/2015-05-31-volvo-on-call-smartwatch-app.html
- [S42] https://www.volvocars.com/en-om/support/topic/bef341c961cb209fc0a801513ec21cca/ (sin confirmar)
- [S43] https://play.google.com/store/apps/details?id=com.kashamlg.volvowearos
- [S44] https://volvowearos.com/
- [S45] https://www.polestar.com/us/manual/polestar-1/2021/article/bdcb3a2ddf550ce9c0a801515931ab43/
- [S46] https://www.polestar-forum.com/threads/polestar-connect-app-on-android-wear-os.2796/ (sin confirmar)
- [S47] https://apps.apple.com/us/app/polestar/id1451196635
- [S48] https://driveteslacanada.ca/news/tesla-releases-apple-watch-app-with-mobile-app-update/
- [S49] https://www.tesla.com/support/tesla-app/apple-watch (sin confirmar) · https://www.howtogeek.com/tesla-teases-apple-watch-app/
- [S50] https://help.tessie.com/article/99-use-wear-os-watch-to-control-your-tesla
- [S51] https://help.tessie.com/article/108-phone-watch-key
- [S52] https://play.google.com/store/apps/details?id=io.tessie.app (reseña: "slow to wake the car... cancel the request")
- [S53] https://play.google.com/store/apps/details?id=com.joshendy.wattkey
- [S54] https://www.teslawatchapp.com/ · reseñas Play jul/ago-2026 en [S53]
- [S55] https://play.google.com/store/apps/details?id=li.power.app.wearos.teslanak
- [S56] https://drive.klokkprojects.no/FAQ.php
- [S57] https://play.google.com/store/apps/details?id=no.klokkprojects.driveelectricfortesla
- [S58] https://github.com/MicheleMercuri/WearOS-HA-Watch-for-Tesla
- [S59] https://play.google.com/store/apps/details?id=com.rivian.android.consumer
- [S60] https://9to5mac.com/2026/02/09/rivian-launching-apple-watch-app-with-remote-controls-and-gen-1-digital-key-feature/
- [S61] https://electrek.co/2026/02/19/rivians-new-apple-watch-companion-app-video/
- [S62] https://apps.apple.com/us/app/lucid-motors/id1579793272 · https://play.google.com/store/apps/details?id=com.lucidmotors.mobile.prod
- [S63] https://gmauthority.com/blog/2025/06/apple-watch-connectivity-back-with-gm-myapp-update/
- [S64] https://play.google.com/store/apps/details?id=com.gm.chevrolet.nomad.ownership
- [S65] https://www.chevybolt.org/threads/mychevrolet-app-on-samsung-galaxy-watch.51925/ (sin confirmar)
- [S66] https://play.google.com/store/apps/details?id=com.aqsmartphone.android.nissan
- [S67] https://play.google.com/store/apps/details?id=mx.nissan.connectservices
- [S68] https://apps.apple.com/us/app/nissanconnect-ev-services/id407814405 · https://play.google.com/store/apps/details?id=com.nissan.mynissan
- [S69] https://www.qashqaiforums.co.uk/viewtopic.php?p=116873 (sin confirmar)
- [S70] https://play.google.com/store/apps/details?id=com.myrsm.android
- [S71] https://samsung.com/us/support/answer/ANS00061431 (no menciona coches; contexto Galaxy Watch)
- [S72] https://play.google.com/store/apps/details?id=com.renault.myrenault.one.fr
- [S73] https://play.google.com/store/apps/details?id=com.fca.myconnect
- [S74] Reseña Play "Mark", jul-2025, en [S73] ("Jeep app for my Samsung Galaxy watch, it's all but useless")
- [S75] https://play.google.com/store/apps/details?id=com.fca.myconnect.fiat (reseña 2026: "The watch app doesn't allow you to check anything") · https://play.google.com/store/apps/details?id=com.fca.myconnect.alfaromeo
- [S76] https://play.google.com/store/apps/details?id=com.psa.mym.mypeugeot
- [S77] https://play.google.com/store/apps/details?id=com.honda.hondalink.connect · https://crmshonda.my.salesforce-sites.com/hondaknowledge/articles/Knowledge/Apple-Watch-Integration-with-HondaLink
- [S78] https://www.youtube.com/watch?v=lnNyISGx_JU (Honda Connect India, sin confirmar)
- [S79] https://play.google.com/store/apps/details?id=com.interrait.mymazda (reseña: "add android watch app")
- [S80] https://www.mazda.ca/en/discover-mazda/innovation/my-mazda-app-connected-vehicle/
- [S81] https://apps.apple.com/us/app/mysubaru/id1005186680
- [S82] https://play.google.com/store/apps/details?id=com.subaru.telematics.app.remote
- [S83] https://play.google.com/store/apps/details?id=com.toyota.oneapp
- [S84] https://support.toyota.com/s/article/Remote-Connect-for-Smartwatch?language=en_US (página no renderizable; contenido citado vía dealers) · https://www.wilsonvilletoyota.com/blog/technology/how-to-connect-your-smartwatch-to-your-toyota/
- [S85] https://www.wheelsjoint.com/how-to-connect-apple-watch-or-android-smartwatch-with-toyota-cars/
- [S86] https://www.bluffusedcarscolumbiasc.com/blog/how-to-operate-your-smart-watch-via-the-toyota-remote-control-app/
- [S87] https://www.youtube.com/watch?v=dFlNj3EpztI (How to remotely control your Toyota from your Galaxy Watch) · https://www.youtube.com/watch?v=Br8BGC8AxQ0
- [S88] https://play.google.com/store/apps/details?id=com.au.toyota.oneapp
- [S89] https://play.google.com/store/apps/details?id=com.toyota.oneapp.eu
- [S90] https://apps.apple.com/gr/app/mytoyota/id1617623127
- [S91] https://play.google.com/store/apps/details?id=com.lexus.oneapp
- [S92] https://lexus2.custhelp.com/app/answers/detail/a_id/9647/~/what-is-the-lexus-app-for-smartwatch (sin confirmar; DNS caído)
- [S93] https://play.google.com/store/apps/details?id=com.au.lexus.oneapp
- [S94] https://play.google.com/store/apps/details?id=com.lexus.oneapp.eu
- [S95] https://play.google.com/store/apps/details?id=com.saicmotor.iov.india
- [S96] https://play.google.com/store/apps/details?id=com.saicmotor.iov.europe
- [S97] https://www.sammobile.com/news/galaxy-watches-nfc-digital-keys-byd-cars/
- [S98] https://www.zeekr.eu/connected · https://autonews.gasgoo.com/china_news/70016074.html
- [S99] https://play.google.com/store/apps/details?id=com.keyconnect.android · https://play.google.com/store/apps/details?id=com.smartremote.carkey.carconnect
