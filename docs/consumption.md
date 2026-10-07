# Consumos y viajes — investigación para 0.3.1

## Primera implementación

La versión 0.3.1 incluye gesto hacia arriba o toque en la foto del coche, resumen ponderado, lista y detalle. Se lee una página de hasta 50 viajes de los últimos 30 días al abrirla. La cobertura se muestra de forma explícita y el historial tiene carga/error independientes de los controles. El parser conserva valores ausentes y ceros reales; siete pruebas de core pasan, incluida una regresión de medias ponderadas. La comprobación real de la respuesta de esta cuenta está pendiente.

## Lectura de datos

La sesión y las cabeceras de `ToyotaApi` sirven para `GET /v1/trips?from=AAAA-MM-DD&to=AAAA-MM-DD&route=false&summary=true&limit=50&offset=0`. Se identifica el coche con la cabecera `vin`, igual que en las demás lecturas. El límite máximo documentado es 50; `_metadata.pagination.nextOffset` permite continuar. La respuesta contiene `payload.trips` y, cuando Toyota lo proporciona, `payload.summary` con agregados mensuales e histogramas diarios. [Cliente y parámetros de pytoyoda](https://pytoyoda.github.io/pytoyoda/pytoyoda/api.html).

Primera implementación: últimos 30 días, una página de hasta 50 viajes, solo al abrir el historial; `route=false` evita descargar recorridos GPS innecesarios. Etiquetar el resultado como «Viajes recientes» y mostrar su cobertura. Una página no garantiza el mes completo. Para la siguiente iteración, consultar un mes natural y su resumen o paginar hasta completarlo.

## Qué necesitamos

| Dato visible | Campo de Toyota | Tratamiento |
| --- | --- | --- |
| Fecha del viaje | `trips[].summary.startTs/endTs` | Zona horaria e idioma del usuario |
| Distancia | `summary.length` | Metros → km |
| Duración | `summary.duration` | Segundos → minutos |
| Combustible usado | `summary.fuelConsumption` | Mililitros → litros; conservar cero |
| Consumo medio | Combustible y distancia | `100 × litros / km`; distancia > 0 |
| Velocidad media | Distancia y duración | `3600 × km / segundos`; duración > 0 |
| Parte eléctrica | `hdc.evDistance/evTime` | Porcentaje de distancia o tiempo, indicando cuál |
| Puntuación Toyota | `scores.global` | Solo si existe; no fabricar un «82» |
| Media mensual / tendencia diaria | `payload.summary[].summary/histograms` | Usar el periodo real del agregado |

Los campos son opcionales y varían por coche. La documentación distingue combustible en mililitros y distancia en metros. [Modelo de respuestas](https://pytoyoda.github.io/pytoyoda/pytoyoda/models/endpoints/trips.html), [conversiones del modelo Trip](https://pytoyoda.github.io/pytoyoda/pytoyoda/models/trips.html).

La media de varios viajes es `100 × suma(litros) / suma(km)`, no la media aritmética de sus L/100 km. Solo incluir viajes con combustible y distancia válidos, indicando cuántos tienen datos. No convertir un campo ausente en cero; un viaje con cero combustible sí puede ser real. No usar la diferencia del nivel del depósito para estimar consumo.

## Límites de la referencia

Con estos campos podemos mostrar resumen, lista y detalle. Los nombres de origen/destino necesitarían datos adicionales o geocodificación: empezar por la fecha, sin servicios externos. El gráfico ondulado por kilómetro y «mejor tramo» requieren muestras de consumo dentro de cada viaje; el endpoint documentado aporta totales y puntos GPS, sin confirmar esas muestras. No dibujar ese gráfico con valores inventados. Se puede mostrar una tendencia real entre viajes o días.

La siguiente revisión añade al historial un gráfico de hasta 12 viajes con fecha, ordenados de antiguo a reciente, usando Canvas sin dependencias. Cada punto corresponde al consumo medio del viaje entero, en L/100 km; un dato ausente rompe la línea, cero combustible se conserva y un solo viaje muestra un punto. Las fechas, el número de viajes y los valores accesibles explican la cobertura. La media superior sigue siendo ponderada sobre los viajes disponibles, no una media mensual completa. En el detalle, la distancia eléctrica tiene una barra cuando `hdc.evDistance` existe.

La selección cronológica, el límite de 12, los viajes sin fecha, los huecos y los ceros tienen una prueba adicional: ocho tests de core pasan en esta revisión. En la pantalla del coche, el gesto nativo desde el borde izquierdo vuelve al garaje sin competir con el paginador; el gesto hacia arriba sigue abriendo viajes.

## Funciones de la API que aún no mostramos — revisión 2026-10-07

Se ha contrastado el cliente actual con el código y los modelos de **pytoyoda, un cliente no oficial de Toyota EU**. Documentado no significa confirmado para esta cuenta: los campos y permisos dependen del vehículo, la región y la suscripción.

| Función | Lectura o campo | Estado / utilidad |
| --- | --- | --- |
| Resúmenes mensuales y diarios | `/v1/trips`, `payload.summary[].histograms` | El GET actual pide `summary=true`, pero el parser descarta los agregados. Permite barras por día y comparativas con periodos completos, tras validar la respuesta. |
| Puntuaciones y datos híbridos ampliados | `scores.acceleration/braking/constantSpeed`, `hdc.evTime/ecoDist/powerDist`, `behaviours` | Solo usamos puntuación global y distancia EV. Son opcionales; añadir indicadores únicamente si llegan. |
| Avisos y estado mecánico | `GET /v1/vehiclehealth/status`, `warning`, `quantityOfEngOilIcon` | Avisos con fecha; no equivale a diagnósticos completos ni garantiza presiones de neumáticos. |
| Notificaciones del coche | `GET /v2/notification/history` | Mensajes, categoría, fecha y estado de lectura. No hay método documentado para marcarlas como leídas. |
| Historial de mantenimiento | `GET /v1/servicehistory/vehicle/summary` | Fechas, categoría y taller cuando estén disponibles. |
| Datos de carga ampliados | `remainingChargeTime`, `evRangeWithAc`, `chargingSchedules`, `nextChargingEvent` | Información para eléctricos/PHEV; comprobar primero el endpoint válido. El cliente externo usa una ruta eléctrica diferente de la nuestra. |
| Ajustes de climatización | `heatingOptions`, `seatOptions`, duración | Ya conservamos las opciones guardadas, pero no mostramos controles individuales. Se necesita validar capacidad y límites por vehículo. |
| Temperatura interior y estado del clima | `currentTemperature`, `startedAt`, `duration` | El GET actual puede traerlos mientras funciona. No confundir temperatura medida con la seleccionada ni presentar una cuenta atrás como estado confirmado. |
| Localizar con luces o sonido | Órdenes `hazard-on/off`, `headlight-on/off`, `sound-horn`, `buzzer-warning` | Candidatas para una futura versión; la presencia en el enum no confirma soporte real. No se probaron órdenes al coche. |

Fuentes: [modelos de viajes](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/models/endpoints/trips.py), [API del cliente](https://pytoyoda.github.io/pytoyoda/pytoyoda/api.html), [rutas actuales](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/const.py), [estado eléctrico](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/models/endpoints/electric.py), [climatización](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/models/endpoints/climate.py), [órdenes](https://raw.githubusercontent.com/pytoyoda/pytoyoda/main/pytoyoda/models/endpoints/command.py).

Prioridad propuesta: validar agregados reales y añadir gráficos diarios; después avisos y mantenimiento. La comparación con el mes anterior requiere dos periodos completos. No estimar kWh, batería de 12 V, presión por rueda ni consumo por kilómetro a partir de campos que no están confirmados.

Comparar con el mes anterior exige cobertura completa de ambos periodos. Los consejos y la puntuación de eficiencia requieren campos de Toyota o una fórmula explícita; quedan fuera de la primera versión. EV usa otra unidad de energía si Toyota la proporciona: no mostrar L/100 km como si fueran kWh/100 km.

## Integración y validación

Reutilizar el cliente autenticado, `work`, estado Compose y fondo `cockpit`; sin servidor ni nuevas dependencias. El historial falla por separado del estado del coche. No activar wake ni comandos. No guardar ni registrar coordenadas, tokens o VIN en los informes. Mantener el estado vacío y errores localizados, con reintento explícito.

Pendiente: confirmar con la cuenta real qué devuelve `/v1/trips`: presencia de viajes, unidades, fechas, combustible y paginación. Las medias ponderadas, cero legítimo, campos ausentes y cobertura parcial se comprobaron con tests; el gesto arriba, detalle y volver se verificaron en emulador en seis idiomas y dos tamaños. Los datos de demo se reservan al emulador. La release documenta la validación y sus límites.
