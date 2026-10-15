# MyToyota 2.25.2 frente a Wear for Toyota

Investigación y mejoras de la 0.3.4, del 15 de octubre de 2026. Revisión del código del proyecto, análisis estático del APK y comprobaciones locales. No se ha iniciado sesión en Toyota, capturado tráfico de una cuenta ni enviado órdenes a un vehículo.

## Evidencia y alcance

El archivo local `MyToyota.ver.2.25.2.build.382.apks` contiene `base.apk` y splits ARM64 y xxxhdpi. REA verificó el inventario y sus hashes. El manifiesto del APK base declara `com.toyota.oneapp.eu`, versión `2.25.2`, código `382`, Android mínimo 29 y target 36. JADX cargó 47.330 clases y 4.963 recursos. Esto identifica el contenido analizado; no acredita por sí solo su firma, procedencia de Google Play ni equivalencia con otras versiones o regiones.

| Artefacto | SHA-256 |
| --- | --- |
| APKS original | `0f5365b152ecc5e8d81a85f0535f7a4885230721e58bb3cdf51c4a18fc7ccccb` |
| APK base | `11cb9f6d9a8f1c8f4d8d2949ce8a6c4caed355f07028edb8e7415038e6f8ccf5` |
| JADX MCP 0.7.1 | `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75` |

REA 6.5.0 estaba instalado. Faltaba su proveedor Android: se descargó el JAR publicado por su autor y se verificó contra el SHA-256 de la release. Se configuraron `REA_JADX_MCP_JAR` y el JDK 21 existente en `~/.codex/config.toml`, con copia previa `config.toml.rea-backup-20261015`. Una sesión nueva de REA con ese entorno permitió investigar durante esta tarea; el servidor que ya estaba conectado requiere reinicio para recibir las variables nuevas. No se instaló otro JDK ni un desensamblador nativo.

Los resultados completos permanecen locales en `notes/rea-mytoyota-2.25.2/`, ignorado por Git. Los identificadores de evidencia se enumeran al final. Las anotaciones Retrofit no aparecen en el inventario de miembros de REA; `inspect_android_method` devuelve correctamente «sin cuerpo» para métodos abstractos. Por eso se contrastaron con el desensamblado DEX de `apkanalyzer` ya instalado. Las rutas de la tabla siguiente proceden de esas anotaciones, no de búsquedas de cadenas sin contexto.

**Observado** significa presente en código, manifiesto o anotación. **Inferido** identifica una conclusión sobre ese código. **Pendiente** requiere respuestas, ejecución o hardware que no se han observado. No se ha decompilado exhaustivamente toda la app: las conclusiones cubren los subsistemas y selectores documentados.

## Arquitectura del proyecto

- `core`: cliente HTTP bloqueante de plataforma, login ForgeRock/OAuth, tokens, endpoints, parsers `Snapshot`, `Climate`, `Trips` y actualizaciones. JSON sin nueva dependencia de producción.
- `watch/Store`: llamadas en IO, estado Compose, última respuesta por vehículo en preferencias privadas, imágenes pequeñas y resultados de comandos. Viajes reducidos en memoria, bajo demanda; sin base de datos ni polling de fondo.
- `watch/Vault`: tokens cifrados AES-GCM con Android Keystore; contraseña opcional con clave separada y protección al bloquear el dispositivo. Se conserva la alternativa StrongBox → Keystore existente.
- `phone`: pantalla de acceso que transfiere sesión y, solo por elección del usuario, contraseña mediante Wear Data Layer. No mantiene una sesión persistente propia.
- UI: garaje, estado/controles, clima, viajes y detalles, información/actualizaciones. Mantiene el diseño cockpit negro/rojo, traducciones y confirmación de apertura. La decisión existente de no exigir PIN no se modifica.

## API: contraste con el APK

Las interfaces relevantes del APK son `i60.d0` —vehículo y telemetría—, `i60.a0` —servicios remotos— e `i60.i` —viajes—. REA identifica sus miembros [E4–E6]; el SDK permite leer verbo, ruta y parámetros. El barrido se acotó a las 32 clases `i60.*`: obtuvo 197 declaraciones HTTP. No es un inventario de todos los servicios de todas las bibliotecas del APK.

| Función | Declaración del APK | Nuestra implementación y valoración |
| --- | --- | --- |
| Garaje | GET `/v2/vehicle/guid` | Coincide. Faltan decisiones por capacidad concreta, además de la activación remota global. |
| Estado | GET `/v1/vehicle/status`, cabecera `vin` | Coincide. Se leen cierre, aperturas y capó; la UI usa solo parte de esos datos. |
| Telemetría | GET `/v3/telemetry` | Coincide: odómetro, combustible, batería, carga, autonomía y fecha propia [E7]. |
| Posición | GET `/v1/location` | Coincide. La fecha de posición ya se conserva; la pantalla no expone toda la antigüedad por fuente. |
| Wake | POST `/v1/remote/status`, cabecera `vin` | Coincide. Acuse de recibo y ejecución son hechos distintos. |
| EV/PHEV | GET `/v1/vehicle/electric/status` | Coincide. El APK conserva también la ruta global antigua; su presencia no justifica volver a ella. |
| Clima: lectura | GET `/v1/vehicle/climate-status` y `/v1/vehicle/climate-settings` | Coincide. Se incorpora POST `/v1/remote/refresh-climate-status` como petición auxiliar previa al sondeo. |
| Clima: orden | POST `/v2/remote/climate-control` | Coincide con el modelo oficial de una sola petición [E9]. |
| Cierre/apertura | POST `/v1/global/remote/command` | Coincide. No se ha demostrado que todas las capacidades/modelos acepten los mismos comandos. |
| Viajes | GET `/v1/trips` | Coinciden `from`, `to`, `route`, `summary`, `limit`, `offset`; el APK añade `category`. |
| Detalle de viaje | GET `/v1/trips/{id}` | Ausente: nuestro detalle presenta el subconjunto ya descargado. |
| Clasificación/preferencias | PUT `/v1/trips/{id}/category`, GET/PUT `/v1/trips/preferences` | Ausentes, sin necesidad demostrada para el reloj. |
| Horarios de clima | GET/POST/PUT/DELETE `/v2/remote/climate-schedule` y `/v3/remote/climate-schedule` | Ausentes. No añadir sin comprobar capacidades, zona horaria, consentimiento y persistencia. |
| Carga | Órdenes y horarios eléctricos, refresco eléctrico v2 | Ausentes de controles; lectura parcial existente. Requieren validación BEV/PHEV. |

Los hosts y claves configurados en nuestro cliente se mantienen. `NetworkDataProviderImpl` del APK obtiene configuración de un proveedor dinámico: no se concluye que todos los clientes, regiones o vehículos compartan siempre el mismo host. Tampoco se cambia `x-appversion` a 2.25.2 por el nombre del APK: afecta al protocolo y a `x-client-ref`, y su compatibilidad necesita comprobación.

La información oficial de Toyota confirma que las funciones disponibles dependen del vehículo: [servicios conectados](https://www.toyota.es/servicios-conectados) y [guías MyToyota](https://www.toyota.es/servicios-conectados/guia-mytoyota). Son contexto funcional, no documentación del contrato REST.

## Autenticación

**Observado en el APK:** ForgeRock, callbacks, intercambio de sesión SSO por código OAuth y tokens. `OAuth2Client.generateCodeChallenge` crea 64 bytes con `SecureRandom`, codifica Base64URL sin padding y calcula SHA-256/S256. `getAuthorizeUrl` usa ese desafío; `exchangeToken` entrega el mismo objeto PKCE al canje [E13–E15]. `IDPHelper` llama a la renovación de ForgeRock. `TokenAuthenticator` obtiene un token fresco y marca la petición reconstruida con `AFTER-AUTHORIZATION` para evitar un ciclo de renovación [E16]. Esto describe caminos estáticos, no una sesión ejecutada.

**Diferencias corregidas:** el cliente usaba un desafío/verificador fijo `plain`. Ahora genera un verificador por login, envía S256 en authorize y el mismo verificador en el canje del código. La renovación no adjunta un verificador ajeno al flujo. Se valida el callback contra el redirect esperado y se rechazan códigos duplicados, error combinado con código, fragmentos y consultas malformadas. Los POST de autenticación tampoco siguen redirecciones que pudieran sacar información del endpoint previsto. No hay degradación automática a `plain`.

La derivación S256 tiene prueba con el [vector de RFC 7636, apéndice B](https://www.rfc-editor.org/rfc/rfc7636#appendix-B). Su compatibilidad con este APK está respaldada por REA; no se ha probado un inicio de sesión real tras el cambio.

**Pendiente:** nuestro login solo resuelve los callbacks de usuario/contraseña y ciertos mensajes. El APK incluye flujos MFA, consentimiento, recuperación y cambios de contraseña; no se deduce que los exija esta cuenta. La firma del JWT no se valida localmente: se usa un claim para identificar la cuenta, y Toyota valida el bearer. Añadir OIDC completo, navegador o MFA necesita diseñar el flujo de errores; no se simula con respuestas ficticias.

El cliente renueva una sola vez ante un 401, aunque el token parezca vigente por su fecha local. Solo repite GET; otro 401 con la sesión renovada exige autenticación. Un POST rechazado puede renovar para la siguiente operación, pero nunca se reenvía automáticamente. La identidad y marca deben coincidir al rotar tokens. Los errores de autenticación y cancelación ya no quedan absorbidos por `optional`.

Cada enlace nuevo invalida el cliente anterior, borra preferencias de vehículos, fotos `car-*.png` —incluidas huérfanas— y credenciales guardadas, y recarga el garaje. Los commits de red exigen que el cliente siga perteneciendo a la sesión activa, también para errores, resultados y liberación de `busy`. La renovación ordinaria conserva los datos de esa cuenta. La red de renovación/relogin queda fuera del monitor de `Store`; un lock separado serializa la rotación de tokens y la revisión de sesión impide persistir respuestas de una identidad anterior.

## Estado del vehículo y comandos

`VehicleStatusPayload` modela puertas, ventanas, luces, recordatorio del asiento trasero, avisos y fecha [E8]. Antes, cualquier valor de cierre distinto de `unlocked` podía convertirse en `true`; contenedores vacíos podían parecer cero aperturas. Ahora un valor explícito desconocido sigue siendo desconocido. Una apertura conocida se mantiene como aviso, aunque falten otras lecturas. No se exige un número fijo de puertas a todos los modelos: componentes omitidos pueden no ser aplicables. Por tanto, «cerrado» resume los componentes reportados, no sensores ausentes.

Se corrigen tres causas de falsos éxitos:

1. POST ya no repite ante 429/5xx. Una respuesta fallida del gateway no demuestra que la orden no llegara al vehículo. GET conserva el backoff 2/4/8 s.
2. El cierre/apertura exige wake aceptado, avance del estado, VIN coincidente y fechas posteriores al acuse de la API tanto globales como de todos los `lockStatus` reportados. El umbral se toma tras la llamada para excluir datos previos a una orden que haya esperado en cola. Una fecha individual omitida o antigua da «sin confirmar».
3. La admisión del trabajo usa `busy.compareAndSet`; el cliente API serializa las llamadas compartidas, incluidas las lecturas independientes de viajes y ajustes de clima.

Las redirecciones del backend se rechazan antes de reenviar cabeceras de sesión. Una prueba de transporte cubre este límite sin sockets ni tráfico real.

Los porcentajes fuera de 0–100, distancias negativas/no finitas, unidades no reconocidas y coordenadas fuera de rango pasan a «sin dato». Se conserva la conversión km/mi. Unidades ausentes no se presuponen km.

**Límites:** timestamp posterior es un requisito conservador, no una correlación causal con un identificador de comando. Un reloj o backend desajustado puede impedir confirmar cierre/apertura. Su sondeo sigue siendo 5/5/10/10 s; no se ha calibrado en nuevos vehículos. No se ha identificado de forma exhaustiva un endpoint de ejecución de órdenes: la ausencia de uno en estos selectores no prueba su inexistencia.

## Climatización

El APK modela estado, temperatura actual/objetivo, duración, `startedAt`, `updatedAt`, calefacción y asientos [E9–E10]. La petición V2 contiene comando, duración, temperatura, opciones y `saveSettings`, coherente con nuestro diseño. El inventario de tipos no demuestra los rangos 18–29 °C, diez minutos ni los enums de cada vehículo: se conservan los límites existentes, pendientes de calibración por capacidades.

Se corrigió la reintroducción de opciones que `extendedCapabilities` declara falsas: la orden se construye solo con las elecciones admitidas. Se elimina el mapa redundante `climateSaved`; la normalización existente de niveles de asiento a modos V2 sigue intacta. Un fallo de ajustes bloquea encender/aplicar y permite detener. El motivo se consulta en Opciones, con reintento existente.

Se sustituye el único GET a los ocho segundos por una lectura previa, una sola orden, la petición auxiliar de refresco y lecturas progresivas con ventana de un minuto y timeout acotado. Es un presupuesto local de sondeo: esperas en cola, renovación de sesión o DNS pueden alargar el tiempo total. Los fallos temporales de lectura se toleran dentro de esa ventana; un rechazo o fallo de red del refresco auxiliar permite consultar el GET autoritativo. Los errores de sesión y cancelación se propagan. El sondeo no repite la orden física.

La confirmación exige un estado terminal `running`/`stopped` con avance respecto a la lectura previa, utilizando fechas del servidor (`updatedAt`/`startedAt`) y sin depender del reloj del Wear. Toyota puede devolver únicamente `status=stopped`; cuando falta una fecha se admite una transición observada entre estados reconocidos. Sin lectura previa no se afirma ejecución. El máximo timestamp observado se conserva aunque aparezcan respuestas sin fecha, para impedir una regresión posterior. Esto acredita el estado observado, no la causalidad de una orden concreta.

`starting` y `stopping` siguen siendo estados intermedios. Los mensajes distinguen encendido, apagado, transición y fallo de verificación, sin usar el genérico «orden enviada, sin confirmar» en clima. Si se confirma encendido pero faltan opciones solicitadas, se informa de ambos hechos. El resultado y su vibración son globales a la app; salir de la pantalla del clima no cancela el sondeo. El diálogo permanece seis segundos, ampliables por accesibilidad del sistema. No se añade un servicio ni una notificación en segundo plano.

La esfera muestra temperatura interior solo con valor finito y unidad C/F reconocida. El tiempo restante es una estimación local de `startedAt + duration`, únicamente con clima activo y duración válida; se actualiza cada quince segundos sin red. No se inventan lecturas ausentes.

**Pendiente:** comprobar que temperatura objetivo y duración reportadas coinciden con la orden, además de las opciones; calibrar la latencia y validar el refresco/sondeo con un coche real. Un timeout del POST físico se muestra como error y no se reenvía; podría haber llegado al coche. La ventana de verificación no puede garantizar una respuesta de Toyota ni convertir datos ausentes en éxito. No se añaden órdenes, horarios ni persistencia de ajustes sin contrato verificado.

## Viajes y consumos

El modelo `TripPayload` incluye resumen, datos híbridos, puntuaciones, comportamientos, ruta y categoría [E11]. El resumen contiene `length`, `duration`, `fuelConsumption`, `averageSpeed` y fechas [E12]. Nuestro parser conserva el subconjunto necesario y calcula correctamente `100 × litros / km`; la media usa suma de combustible dividida por suma de distancia, no la media de tasas. Las pruebas incluyen recorridos de distinta longitud, cero combustible, datos ausentes y huecos del gráfico.

La consulta permanece limitada a una página de 50 viajes en 30 días. La interfaz informa de cobertura y habla de la muestra, sin presentarla como mes completo. No se implementa paginación automática sin comprobar orden, metadatos y respuesta real. El APK confirma los parámetros de paginación, pero el inventario de miembros no prueba sus reglas operativas ni un límite máximo de 50. Tampoco acredita por sí solo metros, mililitros y segundos; esas conversiones proceden del contrato comunitario y fixtures previos, y siguen pendientes de contraste con una respuesta real.

`TripsPayload` contiene viajes, fechas, metadatos y resumen agregado [E17]. Descartamos agregados mensuales/histogramas: permitirían comparaciones de periodos completos después de validar unidades y cobertura. No hay base en este inventario para inventar consumo instantáneo ni energía kWh a partir de litros o porcentajes de batería.

**Privacidad observada:** `TripPayload.Summary` incluye `startLat/startLon/endLat/endLon`. `route=false` evita pedir el recorrido completo, pero no garantiza ausencia de coordenadas en el resumen. `Trips.kt` las descarta y no persiste el JSON bruto. Se aclara en `PRIVACY.md` y `consumption.md`. No se incorporan datos del usuario a fixtures, informes ni logs de investigación.

## Prioridad de mejoras

| Orden | Mejora | Impacto / viabilidad / seguridad | Resultado |
| --- | --- | --- | --- |
| P1 | No repetir POST; confirmaciones con frescura y estados reconocidos | Alto / alta / evita órdenes repetidas y falsa confianza | Implementado y probado. |
| P1 | PKCE aleatorio S256 y callback restringido | Alto / alta con evidencia oficial / refuerza el canje | Implementado y probado localmente; login real pendiente. |
| P1 | Cambio de cuenta durante una operación | Alto / media / privacidad y asociación sesión-VIN | Implementado: borrado de caché/credenciales/fotos, invalidación de cliente y commits por sesión; recarga del garaje. |
| P2 | Serializar API y admisión atómica | Medio-alto / alta / evita solapamiento y doble envío | Implementado; prueba concurrente. |
| P2 | Clima filtrado, estados finales y frescos | Alto / alta / evita opciones incompatibles y falsos éxitos | Implementado; temperatura/duración completas pendientes. |
| P2 | Renovación controlada ante 401 y fallos de autenticación opcionales | Medio-alto / media / no ocultar sesión inválida | Implementado: una renovación, repetir solo GET y no ocultar errores de sesión. |
| P2 | Confirmación climática e información interior | Alto / alta con evidencia de campos/ruta / sin reenvío físico | Implementado: refresco auxiliar, sondeo y mensajes de estado; temperatura interior y tiempo estimado si hay datos. |
| P2 | Fechas visibles por origen | Medio / alta / no presentar energía vieja con fecha reciente de cierre | Pendiente de ajuste de contenido en la esfera existente. |
| P2 | Un refresh token rotatorio por reloj | Medio / media / evitar competencia entre nodos | Pendiente: el móvil envía la misma sesión a todos los nodos. No se verificó la política de reutilización del servidor. |
| P3 | Resúmenes completos, doble energía PHEV, carga y estado ampliado | Variable / media / solo lectura primero | Posible siguiente incremento tras fixtures reales anonimizados. |
| P3 | Horarios, luces/bocina/ventanas, Digital Key, inmovilización | Variable / baja sin vehículo/contrato / efectos físicos y más credenciales | Fuera del cambio. Su presencia en el APK no autoriza a habilitarlos para cualquier coche. |

No se amplía la arquitectura para abarcar todo MyToyota: mantenimiento, seguros, navegación, chat, servicios comerciales y llaves digitales no tienen una necesidad demostrada en esta app de reloj.

## Validación y limpieza acotada

Las nuevas pruebas reproducen primero los errores de estados desconocidos, fechas de componentes antiguas, reenvío de POST, redirecciones, solapamiento y confirmaciones climáticas antiguas/intermedias. El test S256 usa un vector conocido y prueba verificadores distintos, formato y URL; callback valida casos legítimos y adversos. Los cálculos y compatibilidad existentes siguen en `CoreTest`.

Validación de código 0.3.4: **42 tests JVM, cero fallos** —9 existentes y 33 añadidos—; compilación debug de reloj y móvil correcta; lint de los tres módulos sin errores, con 31 avisos/recomendaciones de versiones, manifiestos, recursos y estilo que no se corrigen en este cambio. Comandos: `:core:testDebugUnitTest`, `:watch:assembleDebug`, `:phone:assembleDebug`, `:core:lintDebug`, `:watch:lintDebug`, `:phone:lintDebug`. Las pruebas adicionales cubren recuperación 401, no reenvío de POST, identidad al renovar, invalidación en vuelo, sondeo con errores/cancelación, C/F, cuenta atrás y regresión de fecha entre respuestas fechadas y sin fecha. Esta última falló antes del arreglo y pasó después.

UI: `python android/check_frontend.py emulator-5554 --climate-states` pasó 24 escenarios de entrada en 227 y 192 dp: desconocido en seis idiomas, cuatro estados de clima y habilitación de iniciar/detener con error de ajustes. También comprueba acceso al error en Opciones. Capturas locales; revisión visual de alemán en pantalla pequeña correcta. Solo AVD con datos demo, sin órdenes reales. `python -m py_compile android/check_frontend.py` y `git diff --check` correctos. No se ejecutó el recorrido completo de opciones ni de todos los módulos de UI en esta tarea.

Ampliación 0.3.4 verificada por bloques del mismo módulo: **18 comprobaciones adicionales** en el AVD. Doce cubren temperatura interior, símbolo °C único, tiempo restante y botones dentro de la esfera en seis idiomas y ambos tamaños; cuatro comprueban resultados de éxito/fallo; una comprueba reenlace y borrado de caché/foto huérfana; otra muestra la confirmación parcial más larga, en francés y 192 dp. Revisión visual correcta de los mensajes completos en pantalla pequeña. Para capturar diálogos temporales se amplió el tiempo de lectura mediante ajustes de accesibilidad del emulador, restaurados al acabar. La opción `--release-034` agrupa los nuevos casos principales.

Los APK release de reloj y móvil se compilaron con reducción de código/recursos; `apksigner` verificó la misma firma de publicación, distinta de debug. Versiones 0.3.4: reloj code 10, móvil code 8. Los binarios, las instrucciones de instalación y SHA-256 se distribuyen en la [release 0.3.4](https://github.com/Poiki/wear-for-toyota/releases/tag/v0.3.4); la preparación local permanece en `notes/releases/0.3.4/`. No se han instalado en dispositivos reales. Se mantuvieron intactos el APKS aportado y la eliminación local previa de `scripts/install.ps1`.

### AI SLOP CLEANUP REPORT

- **Scope:** cliente API/autenticación, `Snapshot`, clima, consumidores `Store`/pantalla y pruebas asociadas; sin refactorización general.
- **Behavior Lock:** pruebas de regresión antes de los arreglos descritos; se preservan GET con backoff, payloads existentes, opciones normalizadas, conversión mi/km y cálculos de viajes.
- **Cleanup Plan:** resolver valores seguros inventados y repetición de órdenes; eliminar estado redundante; aclarar estados; reforzar pruebas. Plan y diff revisados por un segundo agente con ámbito de solo lectura.
- **Fallback Findings:** desconocido→cerrado/apagado y reenvío de POST eran fallbacks que ocultaban evidencia: corregidos. Componentes omitidos, conversión de modos y StrongBox→Keystore son compatibilidad acotada: preservada. En la ampliación 0.3.4 se propagan errores de autenticación/cancelación de `optional`; el refresco climático auxiliar puede fallar, pero la confirmación sigue requiriendo evidencia del GET. `stopped` sin fecha admite solo transición reconocida respecto a una lectura previa. El borrado fallido de la caché impide exponer una cuenta nueva.
- **UI/Design Findings:** mismo cockpit y recursos en seis idiomas; se muestran los estados reales. Se reutiliza `bodyMedium` y se permiten cuatro líneas en el resultado para evitar recortar explicaciones en 192 dp. No se añadieron tarjetas ni pantallas.
- **Passes Completed:** puerta de fallbacks; eliminación de `climateSaved`; eliminación de reutilización de PKCE fijo; manejo explícito de desconocidos/fechas; pruebas de regresión y concurrencia; aislamiento de sesiones, renovación 401 y sondeo climático. Limpieza fuera del cambio aplazada por alcance.
- **Quality Gates:** regresiones y suite JVM PASS; compilación de ambos consumidores PASS; lint PASS con los avisos indicados; revisión independiente acotada de seguridad, carreras y confirmaciones, con sus hallazgos corregidos y límites explícitos. No se ejecutó un escáner de seguridad externo ni pruebas contra el backend.
- **Changed Files:** `ToyotaApi` y sus tests: transporte, reintentos y exclusión; `ToyotaAuth`/`ToyotaConst` y tests: PKCE/callback; `Snapshot` y tests: datos y frescura; `Climate`/`CoreTest`/tests: confirmación; `Store`/`TokenListenerService`: sesiones, consumidores y estado mínimo; `Screens`/`MainActivity`/`check_frontend`: estados y comprobación demo; versiones de ambos APK; documentos: evidencia, límites y privacidad.
- **Remaining Risks:** API privada y dependiente del coche; sin login ni órdenes reales; rotación entre nodos, calibración de fechas/latencia y temperatura/duración objetivo pendientes. Una renovación vieja puede retrasar la siguiente al compartir el lock de rotación, pero no mezcla cuentas ni bloquea el monitor de UI.

## Registro de evidencia REA

Cada JSON local conserva operación, selectores, SHA-256 del APK, proveedor, cobertura, limitaciones y resultado completo. Los nombres de clases ofuscadas corresponden solo a este APK. Los archivos de resultados fallidos no se usan como evidencia positiva.

| Ref. | Evidencia | Objeto / resultado local |
| --- | --- | --- |
| E1 | `ev_82c7beb296189ecaac8d5e29b83859598a4211f3abb6edd3140998252858e120` | `apks-inventory.json`: inventario APKS, miembros y hashes. |
| E2 | `ev_c496d4b7f4d9c4e058b6acfcdaee61e92401020677a7db2f0b484ba03db0b559` | `package.json`: identidad, manifiesto, permisos. |
| E3 | `ev_ae36c2da7432531494fccb60b671616cc895d6b8fd824f0c7d0485febaa1df1f` | `classes.json`: inventario completo de clases. |
| E4 | `ev_bca2c5d84e351d39a192c792370c8d056d52cad7ffe07cfe2f2e604d41921311` | `vehicle-api.json`: miembros de `i60.d0`. |
| E5 | `ev_f8f19d298ca1ce9c97bf00a4dc898a433743ac218283969548a5eaaa2f6fe496` | `remote-api.json`: miembros de `i60.a0`. |
| E6 | `ev_4f2d55018362fc8ffed00012db6270a69dca41e6e9514404a1790214285e200c` | `trips-api.json`: miembros de `i60.i`. |
| E7 | `ev_b621c5668f873012de22928f2c1e9c0d481460da27a33ab73603e3c1ff1f4b90` | `telemetry-model.json`. |
| E8 | `ev_7151dcaf873cce4816bc64b1e708d103b63fbb0f1531e109b8f4f0792e430da9` | `status-model.json`. |
| E9 | `ev_490a595d3d95b632653c55700b655d314720f0b906005463832fbb8e18a8a297` | `climate-control-model.json`. |
| E10 | `ev_4d633b0e80f5ef55f2769d778e837cc42d3fa60133cae4eb5f5c5a58aea0e0ad` | `climate-status-model.json`. |
| E11 | `ev_f5db0ba61d444d9c86bded295b9b78beb00132881d35467ba1a820145737e533` | `trip-payload.json`. |
| E12 | `ev_357a919912e97ccd2be50a1ebdb287b7fab2e2cf60580913b38083602a81e3ab` | `trip-summary-exact.json`. |
| E13 | `ev_86ba2f4de24448a474e2d6060db03ad3b2d20a2432a7c3278a5a5461610c43ac` | `oauth-pkce-generation.json`: método de generación de PKCE. |
| E14 | `ev_61742da4f601fc814324ea11c981c0e5d35dcc9d82b0a42b43e6fa709e58bb59` | `oauth-authorize.json`: construcción de authorize. |
| E15 | `ev_0ec9a4212bbec98ed5b09ad6e8b064335ce52e8415abb9f41047306243419a27` | `oauth-exchange.json`: sesión SSO → código → tokens. |
| E16 | `ev_804f1dbb75de9f04fef03234260228a3bca5bc8c4f442726e890275c469f1751` | `token-authenticate.json`: renovación y marca de una repetición. |
| E17 | `ev_dfe716efb604e9552da9ef37859e29f29b4dc9f0861b0f61ea62336b8fa54e77` | `trips-model.json`: envoltorio y agregados. |

Evidencia complementaria del SDK, sin atribuirle IDs de REA: `i60.d0.smali`, `i60.a0.smali`, `i60.i.smali`, `endpoints.json`. Hash de `i60.a0.smali`: `f3f6a3b7566a466f25ec976fec1870d94f270ec87a51d267577708f9c3d7dc82`; de `i60.i.smali`: `fdc96a4db8a26a4e5d21606f002558c0236d82259e87de4293fb05d3ce47ed09`. Son lecturas estáticas del mismo APK, nunca resultados HTTP reales.
