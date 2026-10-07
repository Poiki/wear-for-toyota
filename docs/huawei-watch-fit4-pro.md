# Huawei Watch Fit 4 Pro — viabilidad desde 0.3.1

Revisión: 2026-10-07. Se ha revisado el código de Wear OS y la documentación pública; no se ha ejecutado una app en un Fit 4 Pro ni se ha confirmado su API de comunicación con hardware.

El Fit 4 Pro admite apps de terceros. Su pantalla AMOLED es rectangular, de 480 × 408 píxeles, con corona y botón lateral. La ficha lista Bluetooth, sin Wi-Fi ni eSIM: para este proyecto se propone el teléfono como acceso a Toyota, sin prometer acceso autónomo desde el reloj. La instalación de apps y sus posibilidades con iPhone deben verificarse en el firmware y región del usuario. [Especificaciones de Huawei](https://consumer.huawei.com/es/wearables/watch-fit4-pro/specs/), [gestión de apps](https://consumer.huawei.com/nz/support/content/en-gb16066729/).

La dificultad es media-alta. La interfaz Compose, el almacén Android Keystore, Google Wearable Data Layer y el instalador APK son específicos de Android/Wear OS. Los diseños, traducciones, pruebas de datos y protocolo Toyota sirven como base; la app nativa de Huawei requiere su propio proyecto y componentes. Huawei diferencia relojes completos y ligeros: no asumir que todos comparten el mismo SDK o que basta con usar ArkTS. Confirmar el perfil y las APIs concretas del Fit 4 Pro antes de elegir el runtime. [Desarrollo de wearables](https://developer.huawei.com/consumer/en/multidevice/wearables/get-started/).

## Ruta mínima propuesta

1. Instalar una app de prueba en el Fit 4 Pro y confirmar mensajes bidireccionales con el móvil mediante Wear Engine. El soporte de AppGallery no prueba por sí solo el soporte P2P necesario.
2. Mantener Kotlin y `core` en un companion Android: inicio de sesión, renovación de tokens, consultas y ejecución/verificación de órdenes. La app de móvil actual solo vincula el reloj; habría que ampliar esa responsabilidad.
3. Enviar al reloj datos mínimos y recibir acciones. Las credenciales permanecerían cifradas en el móvil; conservar confirmación de apertura, protección del reloj disponible y comprobación posterior del estado.
4. Recrear estado, controles, clima y gráficos adaptados a la pantalla rectangular. Mostrar conexión perdida y antigüedad de datos.
5. Preparar firma, instalación, actualización y distribución específicas de Huawei.

Wear Engine requiere registro, autorización del servicio y consentimiento del usuario. En Android, Huawei indica Huawei Health y, en móviles no Huawei, HMS Core y permiso de ejecución en segundo plano. Su documentación incluye iOS, pero exige una app de móvil separada y comprobar dispositivos/capacidades admitidos. No asumir que el companion Android funciona en iPhone. [Guía oficial](https://developer.huawei.com/consumer/es/doc/connectivity-Guides/dev-process-0000001051058039).

Estimación técnica orientativa para un desarrollador dedicado: varias semanas, aproximadamente 3–6 para una primera versión comparable una vez demostrada la comunicación. No incluye aprobaciones externas ni garantiza compatibilidad con un firmware concreto. La primera prueba debe validar instalación, comunicación y consulta de estado; no necesita accionar el coche.

## Gráficos y datos

El mismo aspecto negro/rojo se puede mantener. El gráfico ya añadido al proyecto Wear OS muestra consumo medio entre viajes reales y la barra de distancia eléctrica, sin fingir consumo instantáneo. Los agregados diarios/mensuales, avisos y mantenimiento pendientes están descritos en [consumption.md](consumption.md).
