# Wear for Toyota — diseño

La interfaz usa negro, metal oscuro y luz roja. El coche y sus datos ocupan el centro; el fondo acompaña sin competir. La referencia de consumos se adapta a una pantalla redonda real, sin copiar cifras, nombres de rutas ni gráficos ficticios. Las imágenes de referencia permanecen locales y no se publican.

## Colores y materiales

| Uso | Valor |
| --- | --- |
| Fondo OLED | `#000000` |
| Superficie metálica | `#14161A` |
| Acento rojo | `#FF1838` |
| Rojo oscuro | `#B8081A` |
| Texto principal | `#F5F6F8` |
| Texto secundario | `#B9BDC4` |
| Borde neutro | `#393C43` |

Reutilizar `cockpit`, `neonSurface`, `NeonGauge` y `NeonAction`. Fondo estático dibujado con Canvas y caché; sin imágenes decorativas grandes, nuevas dependencias ni animaciones permanentes. El rojo indica acción o selección, no convierte un dato desconocido en una alarma.

## Composición en el reloj

- Esferas sobre un lienzo lógico de 227 dp, escalado para 192 dp y tamaños superiores. Comprobar también el reloj físico de 466 px.
- Logo pequeño arriba; foto completa con `ContentScale.Fit`. El indicador de combustible mantiene su tamaño y posición junto al coche.
- Estado del cierre y avisos debajo de la foto. Ningún aviso puede invadir combustible, métricas ni botones.
- Botones redondos de 48 dp; icono y etiqueta dentro, centrados como un conjunto. Botones principales de clima de 132 × 40 dp y confirmación de 68 × 40 dp.
- Bordes luminosos y arcos tienen margen interno para que el resplandor no se corte. Los extremos de cada cápsula deben caber dentro del círculo de pantalla.
- Las listas largas usan el desplazamiento nativo de Wear OS y su margen curvo, sin reducir una página completa hasta hacerla ilegible.

## Texto y accesibilidad

Datos principales en negrita; unidades y fechas en gris. Etiquetas visibles y descripciones accesibles completas. Números, separadores decimales y fechas siguen el idioma de la app. Autosize para nombres y textos largos, sin puntos suspensivos en acciones esenciales. Revisar español, inglés, alemán, francés, italiano y portugués en 192 y 227 dp.

Las acciones deshabilitadas se distinguen por el icono y su estado accesible. Abrir conserva la confirmación y el bloqueo del sistema si existe; un cambio de estilo no elimina estas condiciones. La opción del sistema de reducir movimiento sigue aplicándose.

## Consumos y viajes — siguiente versión

Desde el panel del coche, deslizar arriba abre el historial; volver usa el gesto o botón de atrás del sistema. Mantener el deslizamiento horizontal hacia controles. La pista «↑ Viajes» hace visible la función; tocar la foto del coche ofrece también un acceso con etiqueta accesible «Viajes».

El historial empieza por una tarjeta de resumen y continúa con viajes recientes. Cada fila muestra fecha, distancia y consumo; tocarla abre duración, combustible, velocidad media y datos híbridos cuando existan. Las tarjetas conservan el metal oscuro, borde fino y selección roja de la referencia.

Mostrar el periodo y el número de viajes que fundamentan cada media. Una media de una página parcial se llama «Media de estos viajes», no «Media mensual». Para un mes completo usar el resumen mensual de Toyota o toda su paginación.

El detalle puede dibujar tendencias entre viajes o días reales. No dibujar consumo instantáneo por kilómetro cuando solo hay un total por viaje. No inventar nombres como «Casa → Oficina», puntuaciones, porcentajes EV, comparativas ni consejos. Mostrar «—» o explicar qué dato no está disponible; cero es un valor válido.

El historial dibuja hasta 12 viajes recientes con fecha, de antiguo a nuevo, con puntos rojos, segmentos rectos y relleno rojo tenue. Cada punto representa el consumo medio de un viaje, no una muestra instantánea ni un intervalo de tiempo constante. El eje vertical parte de cero; los datos ausentes dejan huecos y nunca se conectan. Un único viaje muestra un punto y cero combustible se dibuja en la base. Las fechas y la cobertura permanecen visibles y los valores tienen descripción accesible. El detalle muestra una barra de distancia eléctrica cuando Toyota devuelve el porcentaje; no estima energía ni autonomía.

## Comprobación

«Opciones de clima» reutiliza la lista nativa, el fondo cockpit y los botones metálicos. Iconos de 22 dp para parabrisas, luneta, volante y asiento; ventilación usa el ventilador. Etiquetas cortas de 10 sp y modos de 9 sp, centrados como un conjunto con el icono, sobre botones de al menos 48 dp que crecen si el texto necesita más líneas. La explicación larga va junto a Aplicar, dejando los controles visibles al entrar. El rojo indica selección local; no estado confirmado del coche. La pantalla explica que «Aplicar y encender» inicia o reinicia el climatizador. Los controles desconocidos no se dibujan como apagados y solo se muestran funciones respaldadas por los datos del vehículo. `android/check_frontend.py --climate-options` comprueba los modos, las etiquetas y volver al dial en los seis idiomas y ambos tamaños; nunca envía órdenes al coche.

«Información» mantiene fondo cockpit, textos centrados y botones que crecen si la traducción necesita más líneas. Muestra versión y compilación, búsqueda manual con progreso/resultado y limpieza de credenciales con confirmación. Está disponible desde el garaje, incluso sin coches cargados, y antes de iniciar sesión. El borrado elimina sesión, contraseña guardada y caché del vehículo de este reloj; no se ofrece durante una operación del coche.

`android/check_frontend.py --labels` revisa textos y controles; `--status` comprueba además que el aviso de puerta abierta no tape combustible. El historial necesita comprobar gesto vertical/horizontal, volver, carga/error/vacío, datos incompletos, unidades y medias ponderadas. Las pruebas de interfaz usan exclusivamente el emulador; la revisión física nunca activa una orden remota para comprobar el diseño.
