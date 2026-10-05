# Consumos y viajes — investigación para 0.3.1

## Primera implementación

La rama `feat/0.3.1-trip-consumption` incorpora una primera 0.3.1: gesto hacia arriba o toque en la foto del coche, resumen ponderado, lista y detalle. Se lee una página de hasta 50 viajes de los últimos 30 días al abrirla. La cobertura se muestra de forma explícita y el historial tiene carga/error independientes de los controles. El parser conserva valores ausentes y ceros reales; siete pruebas de core pasan, incluida una regresión de medias ponderadas. La comprobación real de la respuesta de esta cuenta está pendiente.

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

Comparar con el mes anterior exige cobertura completa de ambos periodos. Los consejos y la puntuación de eficiencia requieren campos de Toyota o una fórmula explícita; quedan fuera de la primera versión. EV usa otra unidad de energía si Toyota la proporciona: no mostrar L/100 km como si fueran kWh/100 km.

## Integración y validación

Reutilizar el cliente autenticado, `work`, estado Compose y fondo `cockpit`; sin servidor ni nuevas dependencias. El historial falla por separado del estado del coche. No activar wake ni comandos. No guardar ni registrar coordenadas, tokens o VIN en los informes. Mantener el estado vacío y errores localizados, con reintento explícito.

Confirmar con la cuenta real qué devuelve `/v1/trips`: presencia de viajes, unidades, fechas, combustible y paginación. Después verificar medias ponderadas, cero legítimo, campos ausentes, paginación parcial, gesto arriba y volver, y textos en seis idiomas y dos tamaños. Los datos de demo se reservan al emulador; la release del diseño no anuncia el historial como terminado.
