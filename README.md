# Material Island

Isla dinámica para Android con el estilo del Pixel: Material You, formas y muelles de
Material 3 Expressive. Hecha para el Pixel 10 Pro (Android 17), funciona en cualquier Android 12+.

Sin permiso de internet: nada sale del teléfono.

## Qué hace

| Tipo | Compacta | Expandida |
|---|---|---|
| Música | Carátula con forma de galleta que gira + ecualizador | Carátula que cambia de forma al pausar, barra ondulada con arrastre, botón play que cambia de forma |
| Llamadas | Icono verde que late + "Entrante" o cronómetro | Contestar / rechazar / colgar al estilo del marcador del Pixel |
| Temporizadores | Cuenta atrás en vivo | Número grande + acciones del Reloj |
| Navegación | Flecha de giro + distancia | Instrucción, llegada, salir |
| Descargas / Live Updates | Anillo de progreso | Barra ondulada + acciones |
| Mensajes | — | Aviso con respuesta rápida desde la isla |
| Códigos (OTP) | Píldora ancha con el código; tocar = copiar | — |
| Sistema | Carga, batería baja, Bluetooth, modo de sonido, avión, auriculares | — |

Además: segunda actividad como burbuja al lado (tócala para cambiar), gestos configurables,
gestos sobre la cámara en reposo (linterna, captura…), se oculta en horizontal / pantalla completa,
respeta No molestar y vibra con las primitivas hápticas del Pixel.

## Mapa del código

```
app/src/main/java/dev/jotnar/materialisland/
├── data/IslandSettings.kt     ← TODA la configuración (valores por defecto aquí)
├── model/IslandItem.kt        ← Tipos de actividad y su prioridad (Kind)
├── core/
│   ├── IslandStore.kt         ← Fuente de verdad: actividades en curso + cola de avisos
│   ├── NotificationParser.kt  ← Notificación → actividad (añade reglas nuevas aquí)
│   ├── MediaMonitor.kt        ← Sesiones de música, color de la carátula
│   ├── SystemEventMonitor.kt  ← Carga, Bluetooth, timbre…
│   ├── Actions.kt             ← Abrir apps, linterna, hápticos
│   └── Demo.kt                ← Actividades de prueba
├── overlay/
│   ├── IslandView.kt          ← LA ISLA: modos, tamaños, colores, tarjetas por tipo
│   ├── Components.kt          ← Barra ondulada, carátula con forma, ecualizador, iconos
│   ├── IslandOverlay.kt       ← Ventana, zona táctil, gestos, expandir/contraer
│   └── Geometry.kt            ← Busca el agujero de la cámara
├── service/                   ← Servicio de accesibilidad (ventana) y de notificaciones
└── ui/                        ← App de ajustes (Material 3 Expressive)
```

### Recetas rápidas

- **Cambiar un valor por defecto**: `data/IslandSettings.kt`.
- **Nuevo tipo de actividad**: añade un valor a `Kind` (`model/IslandItem.kt`), detéctalo en
  `NotificationParser.parse`, y píntalo en `CompactLeft`/`CompactRight`/`Card` de `IslandView.kt`.
- **Cambiar la animación**: `IslandBody` en `IslandView.kt` (`sizeSpec`, `transitionSpec`).
- **Otra forma para la carátula**: `artMorph` en `Components.kt` (cualquier `MaterialShapes.*`).
- **Colores**: función `palette()` en `IslandView.kt`.

La configuración también se puede copiar como JSON desde *Copia de seguridad*, editarla y pegarla.

## Compilar e instalar

Requisitos: JDK 17, Android SDK con la plataforma `android-37.2`.

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Permisos por ADB (evita los pasos manuales y el aviso de "ajuste restringido"):

```bash
P=dev.jotnar.materialisland
adb shell cmd notification allow_listener $P/$P.service.IslandNotificationListener
adb shell settings put secure enabled_accessibility_services $P/$P.service.IslandAccessibilityService
adb shell dumpsys deviceidle whitelist +$P
adb shell pm grant $P android.permission.BLUETOOTH_CONNECT
adb shell appops set $P RECEIVE_SENSITIVE_NOTIFICATIONS allow   # códigos OTP en Android 15+
```

> Ojo: `settings put … enabled_accessibility_services` reemplaza la lista. Si tienes otros
> servicios de accesibilidad activos, añádelos separados por `:`.

## Notas técnicas

- La ventana es `TYPE_ACCESSIBILITY_OVERLAY`: se dibuja sobre la barra de estado sin el permiso
  de "mostrar sobre otras apps".
- Solo el rectángulo de la isla recibe toques (`OnComputeInternalInsetsListener`). Esa API oculta
  está limitada a targetSdk ≤ 30; se desbloquea con
  [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass).
- En reposo no hay animaciones vivas: 0 frames por segundo.

## Créditos (ideas, código propio)

- [Expressive Cutout](https://github.com/EvanKoe/expressive-cutout): ventana de accesibilidad, zona táctil, estilo Expressive
- [Dynamic Island for Android](https://github.com/ajitpal/dynamic-island-android): lectura de notificaciones
- [LiveBridge](https://github.com/appsfolder/livebridge): detección de códigos de verificación
- [OmniLand](https://github.com/andrasulthan-alt/OmniLand): burbuja secundaria, eventos del sistema, gestos en reposo
- [SmartIsland](https://github.com/agupta07505/SmartIsland): respuesta rápida, tarjetas expandidas
