<p align="center"><img src="brand/lockup-horizontal-color.svg" height="72" alt="Material Island"></p>

> 🇬🇧 [English version](README.md)

# Material Island

Isla dinámica para Android con el estilo del Pixel: Material You, formas y muelles de
Material 3 Expressive. Hecha para el Pixel 10 Pro (Android 17), funciona en cualquier Android 12+.

Nada de lo que muestra la isla sale del teléfono. Solo **At a glance** usa internet, y solo si lo activas.
Es un icono a la izquierda de la cámara que abre una tarjeta deslizable con el clima (Open-Meteo), los marcadores en vivo
(ESPN), la bolsa (Yahoo Finance) y las alertas. Sin cuenta y sin rastreo. Sin conexión, solo esa tarjeta se pausa. La app también puede mirar en GitHub si hay versión nueva al abrirla
y actualizarse sola (se puede apagar en About). En Pixel,
un botón te lleva al ajuste del launcher para encender o apagar el At a glance de Google.

## En movimiento

<table>
  <tr>
    <td width="50%" valign="top"><b>Música en tres pasos</b><br>Píldora, tarjeta y carátula grande con los controles sobre un desenfoque progresivo.<br><br><img src="docs/gifs/music.gif" alt="Music: compact pill, card and full album art with blurred controls"></td>
    <td width="50%" valign="top"><b>Gota líquida</b> (cámara lenta)<br>Estilo de apertura opcional: una gota se estira desde la cámara, se suelta y se abre.<br><br><img src="docs/gifs/liquid-drop.gif" alt="Liquid drop open animation in slow motion"></td>
  </tr>
  <tr>
    <td valign="top"><b>Carga</b><br>Anillo ondulado, porcentaje que sube y un barrido de luz. Al tocarla: tiempo hasta el 100 %, velocidad, cable o inalámbrica, vatios y temperatura.<br><br><img src="docs/gifs/charging.gif" alt="Charging pill and detail card"></td>
    <td valign="top"><b>Accesos desde la cámara</b><br>Toca la cámara en reposo y sale una tarjeta con los accesos que elijas.<br><br><img src="docs/gifs/shortcuts.gif" alt="Shortcuts card opened from the camera"></td>
  </tr>
  <tr>
    <td valign="top"><b>Llamadas</b><br>Contesta desde la isla y el tiempo de llamada queda en una píldora compacta.<br><br><img src="docs/gifs/call.gif" alt="Incoming call answered from the island"></td>
    <td valign="top"><b>Mensajes</b><br>Un banner con responder y marcar como leído que vuelve a la cámara.<br><br><img src="docs/gifs/message.gif" alt="Message banner"></td>
  </tr>
</table>

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

Además: burbujas de mensajes sin leer a la izquierda de la isla (tocar = leer/responder en la isla, mantener = abrir la app), segunda actividad como burbuja al lado (tócala para cambiar), gestos configurables,
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

## Descargar

Descarga `material-island-<versión>.apk` desde la [última release](https://github.com/JotnarDev/material-island/releases/latest)
e instálala. En Android 13+, si no deja activar la accesibilidad: **Ajustes → Apps → Material Island → ⋮ →
Permitir ajustes restringidos**, o usa los comandos ADB de abajo.

### Xiaomi, Redmi y POCO (HyperOS / MIUI)

1. **Ajustes restringidos.** Si el interruptor de accesibilidad sale gris, intenta activarlo una vez. Después ve a
   **Ajustes → Apps → Administrar apps → Material Island → ⋮ → Permitir ajustes restringidos** y vuelve a activarlo.
2. **Accesibilidad:** está en **Ajustes → Ajustes adicionales → Accesibilidad → Apps descargadas**.
3. **Inicio automático:** actívalo en la info de la app. Si no, HyperOS cierra la isla al rato.
4. **Ahorro de batería:** ponlo en **Sin restricciones**, en la misma pantalla.
5. **ADB en Xiaomi:** para usar los comandos de abajo, activa **Opciones de desarrollador → Depuración USB (ajustes de
   seguridad)**. Pide iniciar sesión con la cuenta Mi y tener una SIM puesta.

La app muestra un aviso con accesos directos cuando detecta estos bloqueos.

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
adb shell pm grant $P android.permission.WRITE_SECURE_SETTINGS     # "La isla es la principal"
```

Ocultar los iconos de notificaciones de Android en la barra (las burbujas los reemplazan; se reinicia al reiniciar el teléfono):

```bash
adb shell cmd statusbar send-disable-flag notification-icons   # "none" para volver a mostrarlos
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

## Identidad

Logo "Split": la isla y el punto de la cámara separándose, el mismo gesto de la burbuja secundaria.
Archivos y guía de uso en [`brand/`](brand/).

## Créditos (ideas, código propio)

- [Expressive Cutout](https://github.com/EvanKoe/expressive-cutout): ventana de accesibilidad, zona táctil, estilo Expressive
- [Dynamic Island for Android](https://github.com/ajitpal/dynamic-island-android): lectura de notificaciones
- [LiveBridge](https://github.com/appsfolder/livebridge): detección de códigos de verificación
- [OmniLand](https://github.com/andrasulthan-alt/OmniLand): burbuja secundaria, eventos del sistema, gestos en reposo
- [SmartIsland](https://github.com/agupta07505/SmartIsland): respuesta rápida, tarjetas expandidas

## Licencia

[MIT](LICENSE) © JotnarDev
