# Análisis funcional de MyToyota (app oficial, Europa)

Estado: en curso, por observación directa en el teléfono del usuario (MyToyota 2.25.2, sesión iniciada por él). Sin credenciales, VIN ni coordenadas reales. El emulador quedó descartado (ver mytoyota-emulator-compatibility.md).

Vehículo observado: Toyota Corolla Hybrid Touring Sports (híbrido no enchufable).

## 1. Navegación
- Al abrir la app (o volver a ella) pide autenticación del dispositivo: `BiometricPrompt` del sistema con huella y alternativa "Introducir contraseña" (del dispositivo). Es decir, Toyota protege el acceso a la app con la biometría del teléfono, no con la contraseña Toyota. Equivalente en el reloj: exigir PIN de pantalla y reloj desbloqueado antes de acciones sensibles.
- Actividad raíz: `DashboardOldActivity` (vistas Android nativas; el árbol de accesibilidad es legible con uiautomator).
- Barra inferior con cinco pestañas (iconos de línea, sin texto): coche (dashboard), mapa, notificaciones (con punto rojo cuando hay nuevas), perfil, chat/asistencia.
- Selector de vehículo arriba: chip "Mi Garaje".
- Pendiente: profundidad hasta cada acción, gestos, pantalla de estado detallado.

## 2. Dashboard
Orden vertical: chip "Mi Garaje" → imagen del coche (render oficial, fondo blanco) → tres cifras con etiqueta debajo: **Kilometraje** (45.334 km), **Combustible** (43%), **Autonomía** (264 km) → fecha de los datos ("Hoy, 21:07") → estado de carga ("Actualizando…" mientras consulta al abrir) → tarjeta **Control Remoto**: botón circular grande central "Encender Climatizador" (icono ventilador) y dos botones circulares menores con candado cerrado (izquierda) y candado abierto (derecha).
Estilo: fondo blanco, tipografía Toyota Type fina, iconos de línea negros, rojo solo en avisos (punto de notificaciones); mucho espacio en blanco; los valores en negrita y las etiquetas en gris.
Lo que está a un toque: clima, cerrar, abrir, cambiar de vehículo, pestañas.

## 3. Estado del vehículo
Campos visibles (cierre, puertas, ventanas, maletero, capó, luces, km, combustible, autonomía, batería 12V/HV, carga) · timestamp mostrado · cómo distingue cacheado vs actual.

## 4. Actualizar (refresh)
Dónde está · qué ocurre al pulsar · tiempo de espera medido · indicador · resultado si el coche no responde.

## 5. Cerrar / abrir
Flujo estado inicial → acción → carga → respuesta → verificación → estado final · confirmación pedida · tiempos medidos · mensajes.

## 6. Climatización
Opciones disponibles para este vehículo (temperatura, duración, desempañado, asientos, volante) · si se envían en una sola operación · tiempos.

## 7. Ubicación
Mapa · timestamp · dirección · "navegar hasta".

## 8. Errores observados
Texto literal mostrado por Toyota → causa aparente → cómo lo traduciremos en el reloj.

## 9. Terminología Toyota
Observada (ES): "Mi Garaje", "Kilometraje", "Combustible", "Autonomía", "Hoy, 21:07" (fecha relativa + hora), "Actualizando…", "Control Remoto", "Encender Climatizador". Pendiente: textos de estado de cierre, puertas, ventanas, errores.

## 10. Conclusiones para Wear OS
Qué merece ir al reloj · qué no · estados intermedios a reproducir.
