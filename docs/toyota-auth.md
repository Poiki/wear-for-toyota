# Toyota Europe (MyToyota) — Autenticación: flujo, tokens y viabilidad en Wear OS (a 2026-10-01)

Basado en el código de [pytoyoda `controller.py`](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/controller.py) y [`const.py`](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/const.py), contrastado con [evcc `identity.go`](https://raw.githubusercontent.com/evcc-io/evcc/master/vehicle/toyota/identity.go) e [ioBroker.toyota](https://github.com/TA2k/ioBroker.toyota). **No se reproducen claves ni secretos**: la cabecera `Authorization: Basic …` del paso 3 y las `x-api-key`/`API_KEY` son valores estáticos embebidos en el cliente open-source (ver `const.py`/`controller.py`). Lo no verificado va marcado **(sin confirmar)**.

---

## 1. Flujo de login paso a paso

Es el flujo estándar de **ForgeRock AM (OpenAM)**: autenticación JSON por *callbacks* → cookie de sesión SSO → OAuth2 *authorization code* (con PKCE trivial) → tokens. Todo son llamadas HTTPS JSON/form; **no hace falta navegador ni WebView**.

| Paso | Petición | Detalle |
|---|---|---|
| 1. Authenticate (callbacks) | `POST https://b2c-login.toyota-europe.com/json/realms/root/realms/tme/authenticate?authIndexType=service&authIndexValue=oneapp` con body `{}` | Responde `{authId, callbacks:[…]}`. El cliente rellena y reenvía **el mismo JSON**: `NameCallback` cuyo `output[0].value == "User Name"` → `input[0].value = email`; `PasswordCallback` → `input[0].value = password`. Si llega `TextOutputCallback` con `"User Not Found"` → usuario inválido. Se repite (pytoyoda: hasta 10 rondas) hasta que la respuesta trae `tokenId`. Cualquier status ≠ 200 → `ToyotaLoginError`. evcc añade cabeceras `X-Brand`/`X-Region` a esta llamada; pytoyoda no. Realm Subaru: `alliance-subaru`. |
| 2. Authorize | `GET https://b2c-login.toyota-europe.com/oauth2/realms/root/realms/tme/authorize?client_id=oneapp&scope=openid+profile+write&response_type=code&redirect_uri=com.toyota.oneapp:/oauth2Callback&code_challenge=plain&code_challenge_method=plain` con cabecera `Cookie: iPlanetDirectoryPro=<tokenId>` | Se espera **HTTP 302** y se lee `code` del query de `Location` (esquema `com.toyota.oneapp:/…`). **El cliente HTTP no debe seguir redirecciones** en este paso. ioBroker usa PKCE `S256` real en vez de `plain`, luego el servidor admite ambos. |
| 3. Token | `POST https://b2c-login.toyota-europe.com/oauth2/realms/root/realms/tme/access_token` con `Authorization: Basic <credencial estática del cliente oneapp>` y form `client_id=oneapp&code=<code>&redirect_uri=com.toyota.oneapp:/oauth2Callback&grant_type=authorization_code&code_verifier=plain` | 200 → JSON con `access_token`, `id_token`, `refresh_token`, `expires_in` (pytoyoda exige los cuatro). |
| 4. GUID | Decodificar `id_token` (JWT RS256, pytoyoda **no verifica firma**, audiencia `oneappsdkclient`) y leer el claim `uuid` | Ese `uuid` se envía después como `x-guid`/`guid` en todas las llamadas a la API y entra en el HMAC de `x-client-ref`. |
| 5. Llamadas API | `https://ctpa-oneapi.tceu-ctp-prd.toyotaconnectedeurope.io/...` con `authorization: Bearer <access_token>` + cabeceras de §3 de `toyota-api.md` | — |

Errores de login visibles: `ToyotaInvalidUsernameError` (email sin `@` o "User Not Found"), `ToyotaLoginError` (cualquier otro fallo: HTTP ≠ 200/302, faltan tokens) ([exceptions.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/exceptions.py)). Un login correcto puede seguir fallando en la API con `APIGW-403 "invalid guid and vin mapping"` si la cuenta no tiene vehículo asociado ([pytoyoda #161](https://github.com/pytoyoda/pytoyoda/issues/161)).

---

## 2. MFA / OTP

- **Realm consumidor (`tme`, b2c-login):** a 2026-10-01 **no hay evidencia** de OTP/2FA. Ningún cliente (pytoyoda, ha_toyota, openHAB, evcc, ioBroker, toyota-mcp) implementa un callback de OTP; la config de ha_toyota sólo pide marca, email, password y unidades ([config_flow.py](https://raw.githubusercontent.com/pytoyoda/ha_toyota/main/custom_components/toyota/config_flow.py)). Búsquedas en issues de pytoyoda, ha_toyota y mytoyota por "2FA", "OTP" → 0 resultados. toyota-mcp advierte: "Accounts with two-factor authentication cannot be used, as Toyota's vehicle API does not support them" ([README](https://raw.githubusercontent.com/zepgram/toyota-mcp/main/README.md)) — es decir, si un usuario activase 2FA el flujo de callbacks actual no lo contempla (sin confirmar que exista tal opción en la cuenta MyToyota).
- **Aviso "2FA coming soon" en `mytoyota.toyota-europe.com`:** ese portal es **TARS**, el IdM corporativo/concesionarios de TME (SiteMinder `idm.toyota-europe.com`, "TME Service Centre", ServiceNow). Su guía describe OTP por email, "Remember this device" y hasta 3 dispositivos de confianza ([TARS PDF](https://idm.toyota-europe.com/siteminderagent/forms/TARSPasswordRules-v2.pdf), [portal](https://mytoyota.toyota-europe.com/)). **No aplica a la app MyToyota** pese al nombre del host.
- **Contraste NA:** Toyota Norteamérica sí exige OTP (email/SMS) en su ForgeRock, y las integraciones lo tuvieron que añadir ([ha-toyota-na #22](https://github.com/widewing/ha-toyota-na/issues/22)). Riesgo real de que TME lo copie: el mecanismo de callbacks ya lo soporta (un `NameCallback` extra con el código), así que la UI de login debe ser **genérica por callbacks**, no "email+password" cableado.
- Registro de cuenta: verificación por código de 6 dígitos al email / SMS; sólo en el alta, no en cada login ([toyota.co.uk guía](https://www.toyota.co.uk/content/dam/toyota/nmsc/united-kingdom/owners/myt-connected-services/new-guides/toyota-myt-app-registration-plate.pdf)). Versiones recientes de la app no mencionan 2FA en sus notas ([App Store](https://apps.apple.com/gb/app/mytoyota/id1617623127)).

---

## 3. Tokens: tipos, duración, refresh, caché

| Aspecto | Dato | Fuente |
|---|---|---|
| Tipos devueltos | `access_token` (Bearer, opaco para el cliente), `id_token` (JWT RS256 con `uuid`), `refresh_token`, `expires_in` (segundos) | [controller.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/controller.py) |
| Vida del access token | pytoyoda calcula `now + expires_in` y no fija un valor; toyota-mcp documenta "access tokens refreshing silently after one-hour expiration" → **~1 h (sin confirmar contra respuesta real)** | [toyota-mcp README](https://raw.githubusercontent.com/zepgram/toyota-mcp/main/README.md) |
| Vida del refresh token | **Desconocida (sin confirmar)**. toyota-mcp persiste sesiones "across restarts" sólo con el refresh token, lo que sugiere días o más; no hay issue que cuantifique caducidad ni reportes de "Token refresh failed" por expiración | [toyota-mcp README](https://raw.githubusercontent.com/zepgram/toyota-mcp/main/README.md), búsqueda issues pytoyoda |
| Refresh | `POST …/access_token` con la misma `Authorization: Basic` estática y form `grant_type=refresh_token&refresh_token=…&client_id=oneapp&redirect_uri=com.toyota.oneapp:/oauth2Callback&code_verifier=plain`. Devuelve el mismo JSON (nuevo `refresh_token` incluido → rotación; guardar siempre el último). **No requiere la contraseña.** Si falla → pytoyoda hace login completo con usuario y contraseña (por eso guarda la contraseña en memoria) | [controller.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/controller.py) |
| Cuándo refresca | Antes de cada petición si `expiration <= now` (sin margen de seguridad); no hay refresco proactivo | idem |
| Caché en pytoyoda | Diccionario **en memoria** a nivel de clase (`_TOKEN_CACHE[username]`), compartido entre instancias del mismo proceso; **no se persiste en disco ni se cifra**; cada reinicio de HA vuelve a hacer login con password | idem |
| Caché en otros | toyota-mcp: refresh token en keyring del SO (Keychain/Credential Locker/Secret Service) o `oauth.json` modo 600; nunca guarda la contraseña. evcc: `RefreshTokenSource` con `TokenWithExpiry` (en memoria) | [toyota-mcp](https://raw.githubusercontent.com/zepgram/toyota-mcp/main/README.md), [identity.go](https://raw.githubusercontent.com/evcc-io/evcc/master/vehicle/toyota/identity.go) |
| Vinculación a dispositivo | No se observa device binding: el mismo refresh token sirve desde cualquier host; `x-client-ref` sólo depende de versión de app + `uuid` | [controller.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/controller.py) |
| Sesiones concurrentes | La app, HA, openHAB y evcc conviven con la misma cuenta (varios tokens válidos a la vez); sólo una cuenta puede tener el control activo de un coche ([toyota.es FAQ](https://www.toyota.es/servicios-conectados/preguntas-frecuentes)) | — |

---

## 4. Implicaciones para un reloj Wear OS sin navegador

**¿Se puede hacer el login con llamadas HTTPS JSON puras, sin WebView?** Sí. Son 3 peticiones (authenticate con callbacks, authorize con cookie y redirección no seguida, access_token con Basic estático) + decodificar un JWT. Todo cabe en OkHttp/Ktor con `followRedirects=false` en el paso 2. No hay CAPTCHA, device-print ni JavaScript en el realm `tme` (a diferencia de NA).

**Qué necesita del usuario:** email y contraseña (hoy). Diseñar la pantalla de login como *renderizador de callbacks* para que un futuro OTP (`NameCallback` adicional) sólo requiera un campo más. En un reloj teclear email+password es hostil; opciones:

| Opción | Pros | Contras |
|---|---|---|
| A. Login en el reloj (teclado/dictado, `RemoteInput`) | App 100 % standalone | UX pésima; contraseña dictada en voz alta |
| B. Login en app companion de móvil y **enviar sólo el refresh token** (y `uuid`) por Wearable Data Layer / `MessageClient` | El reloj nunca ve la contraseña; mejor UX | Requiere app de móvil; si el refresh caduca o falla hay que repetir desde el móvil (pytoyoda hace fallback a password, el reloj no podría) |
| C. Login en el reloj vía **Credential Manager / autofill** de Wear OS | Sin teclear si hay gestor de contraseñas | Soporte desigual según reloj/gestor (sin confirmar) |
| D. Guardar también la contraseña en el reloj (Keystore) para re-login automático | Resiliencia total sin móvil | Secreto de alto valor en wearable; desaconsejado |

**Recomendación:** B como principal (fallback A); persistir `refresh_token` + `access_token` + `expires_at` + `uuid` en `EncryptedSharedPreferences`/Keystore; refrescar con margen (p.ej. 5 min antes) y rotar el refresh token tras cada refresh; si el refresh devuelve ≠200, invalidar y pedir re-vinculación desde el móvil.

**Otros puntos de diseño:**
- Mantener `x-appversion`, `x-channel`, `x-client-ref`, `x-correlationid`, `x-region`/`x-user-region`, `x-brand` idénticos a pytoyoda; omitir alguna (p.ej. `x-appversion`) ha provocado 500 ([#85](https://github.com/pytoyoda/pytoyoda/issues/85)) y `CTP-GENERIC-40017` ([#355](https://github.com/pytoyoda/ha_toyota/issues/355)).
- Peticiones **en serie**, backoff 2/4/8 s en 429/5xx, nunca `asyncio.gather`-style ráfagas ([vehicle.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/models/vehicle.py)).
- Un wake (`POST /v1/remote/status`) + sondeo de 25 s cabe en un `WorkManager`/foreground corto; evitar wakes periódicos en reloj (batería 12 V del coche).
- Marca: pedir Toyota/Lexus/Subaru (cambia realm y cabeceras `x-appbrand`/`brand`).

---

## 5. Riesgos

1. **API no oficial y volátil:** entre 2026-06-26 y 2026-09 Toyota retiró `/v1/global/remote/*` (status, refresh-status, climate-*, electric/status) tras SigV4 y rompió todos los clientes durante semanas ([#267](https://github.com/pytoyoda/pytoyoda/issues/267), [#316](https://github.com/pytoyoda/pytoyoda/issues/316)). Un reloj sin canal de actualización rápido quedará inservible en cada cambio; conviene mantener los endpoints en configuración remota.
2. **Secretos estáticos de la app** (`x-api-key`, Basic auth de `oneapp`): si Toyota los rota o añade attestation/SigV4 al login, el cliente muere; además publicarlos puede tener implicaciones legales/ToS ([toyota-api.md §10](./toyota-api.md)).
3. **2FA futura:** NA ya lo tiene; TME lo está desplegando al menos en su IdM corporativo. Si llega al realm `tme`, un reloj sin flujo de OTP no podrá iniciar sesión; el móvil companion mitiga.
4. **Expiración del refresh token desconocida** → la app de reloj debe degradar con gracia a "vuelve a vincular desde el móvil".
5. **Rate limits estocásticos (429/APIGW-403)** y sin `Retry-After`; polling agresivo también drena la batería 12 V del coche ([ha_toyota README](https://raw.githubusercontent.com/pytoyoda/ha_toyota/main/README.md)).
6. **Gating silencioso:** con `remoteDisplay ≠ 7` o suscripción Remote Services caducada el gateway acepta y el coche ignora; el reloj debe leer `remoteDisplay` y las suscripciones antes de ofrecer botones ([#296](https://github.com/pytoyoda/ha_toyota/issues/296)).
7. **Seguridad del dispositivo:** un refresh token sin device binding en un reloj perdido da acceso a abrir el coche hasta que caduque; no hay endpoint de revocación documentado (sin confirmar que `/oauth2/.../token/revoke` de ForgeRock esté habilitado) → ofrecer "desvincular" que borre tokens y recomendar cambio de contraseña.
8. **Políticas de tienda:** Google Play exige transparencia al pedir credenciales de un servicio de terceros; la app no puede presentarse como oficial.

---

## 6. Fuentes

- pytoyoda: [controller.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/controller.py) · [const.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/const.py) · [client.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/client.py) · [exceptions.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/exceptions.py) · [vehicle.py](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/models/vehicle.py) · [#85](https://github.com/pytoyoda/pytoyoda/issues/85) · [#161](https://github.com/pytoyoda/pytoyoda/issues/161) · [#267](https://github.com/pytoyoda/pytoyoda/issues/267) · [#316](https://github.com/pytoyoda/pytoyoda/issues/316) · [PyPI](https://pypi.org/project/pytoyoda/)
- ha_toyota: [config_flow.py](https://raw.githubusercontent.com/pytoyoda/ha_toyota/main/custom_components/toyota/config_flow.py) · [README](https://raw.githubusercontent.com/pytoyoda/ha_toyota/main/README.md) · [#296](https://github.com/pytoyoda/ha_toyota/issues/296) · [#355](https://github.com/pytoyoda/ha_toyota/issues/355)
- Otros clientes: [evcc identity.go](https://raw.githubusercontent.com/evcc-io/evcc/master/vehicle/toyota/identity.go) · [ioBroker.toyota](https://github.com/TA2k/ioBroker.toyota) · [toyota-mcp README](https://raw.githubusercontent.com/zepgram/toyota-mcp/main/README.md) · [openHAB hilo](https://community.openhab.org/t/mytoyota-binding-toyota-lexus-and-subaru-europe/170453) · [ha-toyota-na #22](https://github.com/widewing/ha-toyota-na/issues/22)
- Toyota: [TARS portal](https://mytoyota.toyota-europe.com/) · [TARS 2FA PDF](https://idm.toyota-europe.com/siteminderagent/forms/TARSPasswordRules-v2.pdf) · [toyota.es FAQ](https://www.toyota.es/servicios-conectados/preguntas-frecuentes) · [toyota.co.uk registro](https://www.toyota.co.uk/content/dam/toyota/nmsc/united-kingdom/owners/myt-connected-services/new-guides/toyota-myt-app-registration-plate.pdf) · [App Store](https://apps.apple.com/gb/app/mytoyota/id1617623127)
