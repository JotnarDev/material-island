package dev.jotnar.materialisland.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.jotnar.materialisland.model.ChargeInfo
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.launch
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.Animatable
import dev.jotnar.materialisland.data.OpenStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import dev.jotnar.materialisland.core.IslandState
import dev.jotnar.materialisland.data.AccentMode
import dev.jotnar.materialisland.data.BackgroundMode
import dev.jotnar.materialisland.data.IdleAction
import dev.jotnar.materialisland.data.IslandSettings
import dev.jotnar.materialisland.model.ActionStyle
import dev.jotnar.materialisland.model.IslandAction
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Tipos públicos ─────────────────────────────────────────────────────

/** Posición del agujero de la cámara, en dp relativos a la ventana del overlay. */
data class IslandGeometry(
    val widthDp: Float,
    val holeCenterXDp: Float,
    val holeCenterYDp: Float,
    val holeDiameterDp: Float,
)

/** Forma que toma la isla. */
enum class Mode { HIDDEN, REST, COMPACT, WIDE, PEEK, EXPANDED }

enum class Swipe { UP, DOWN, LEFT, RIGHT }

enum class MediaCmd { PLAY_PAUSE, NEXT, PREV }

data class IslandUi(
    val state: IslandState,
    val expanded: Boolean,
    val hidden: Boolean,
    val replyingId: String? = null,
    val copiedId: String? = null,
    /** Mensajes sin leer, el más reciente primero. */
    val unread: List<IslandItem> = emptyList(),
    /** Música en su tercer nivel (carátula grande). */
    val full: Boolean = false,
    /** Tarjeta de accesos abierta desde la cámara en reposo. */
    val shortcuts: Boolean = false,
    val shortcutTick: Int = 0,
    /** Icono de At a glance junto a la cámara (si está activado). */
    val glanceOn: Boolean = false,
    /** Tarjeta de At a glance abierta. */
    val glance: Boolean = false,
    val glanceData: dev.jotnar.materialisland.core.Glance.Data = dev.jotnar.materialisland.core.Glance.Data(),
    /** Vista elegida de At a glance (la que muestra el icono y la que se abre debajo). */
    val glanceView: dev.jotnar.materialisland.data.GlanceView? = null,
    /** Hacia dónde se cambió la última vez (1 = siguiente, -1 = anterior), para la animación. */
    val glanceDir: Int = 1,
    val glanceCount: Int = 0,
)

interface IslandCallbacks {
    fun onTap(item: IslandItem?, mode: Mode) {}
    fun onLongPress(item: IslandItem?, mode: Mode) {}
    fun onDoubleTap(item: IslandItem?, mode: Mode) {}
    fun onShortcut(action: IdleAction) {}
    fun onSwipe(item: IslandItem?, mode: Mode, dir: Swipe) {}
    fun onAction(item: IslandItem, action: IslandAction) {}
    fun onReplyStart(item: IslandItem) {}
    fun onReplySend(item: IslandItem, action: IslandAction, text: String) {}
    fun onReplyCancel() {}
    fun onMedia(item: IslandItem, cmd: MediaCmd) {}
    fun onSeek(item: IslandItem, fraction: Float) {}
    fun onSecondaryTap() {}
    fun onBounds(rects: List<android.graphics.Rect>) {}
    fun onPressChanged(pressed: Boolean) {}
    fun onBubbleTap(item: IslandItem) {}
    fun onBubbleLongPress(item: IslandItem) {}
    fun onBubbleOverflow() {}
    fun onGlanceTap() {}
    /** Pasar a la vista siguiente (1) o anterior (-1) de At a glance. */
    fun onGlanceSwipe(step: Int) {}
}

fun resolveMode(ui: IslandUi): Mode {
    val p = ui.state.primary
    return when {
        ui.hidden -> Mode.HIDDEN
        ui.glance -> Mode.EXPANDED
        p == null -> if (ui.shortcuts) Mode.EXPANDED else Mode.REST
        p.kind == Kind.NOTIFICATION -> Mode.PEEK
        ui.expanded && p.kind == Kind.CHARGING -> Mode.EXPANDED
        ui.expanded && p.kind == Kind.BLUETOOTH && p.buds != null -> Mode.EXPANDED
        !p.kind.persistent -> Mode.WIDE
        ui.expanded -> Mode.EXPANDED
        else -> Mode.COMPACT
    }
}

// ── Colores ────────────────────────────────────────────────────────────

private val CallGreen = Color(0xFF1E8E3E)
private val CallRed = Color(0xFFD93025)
private val SoftGreen = Color(0xFF6DD58C)
private val SoftRed = Color(0xFFFFB4AB)

@Immutable
data class IslandPalette(
    val bg: Color,
    val content: Color,
    val variant: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val track: Color,
)

private fun tone(argb: Int, lightness: Float, maxSat: Float = 0.9f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = hsl[1].coerceAtMost(maxSat)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
private fun palette(s: IslandSettings, item: IslandItem?, mode: Mode): IslandPalette {
    val scheme = MaterialTheme.colorScheme
    val base = when (s.background) {
        BackgroundMode.BLACK -> Color.Black
        BackgroundMode.MATERIAL -> scheme.surfaceContainer
        BackgroundMode.CUSTOM -> Color(s.customBackground.toInt())
    }
    val itemColor = item?.media?.artColor?.takeIf { s.albumTint } ?: item?.accent
    val accentArgb: Int? = when (s.accent) {
        AccentMode.MATERIAL -> if (item?.kind == Kind.MEDIA && s.albumTint) item.media?.artColor else null
        AccentMode.APP -> itemColor
        AccentMode.CUSTOM -> s.customAccent.toInt()
    }
    val accent = accentArgb?.let { tone(it, 0.72f) } ?: scheme.primary
    val accentContainer = accentArgb?.let { tone(it, 0.26f, 0.6f) } ?: scheme.primaryContainer
    val onAccentContainer = accentArgb?.let { tone(it, 0.9f) } ?: scheme.onPrimaryContainer
    val tinted = mode == Mode.EXPANDED && item?.kind == Kind.MEDIA && s.albumTint && item.media?.artColor != null
    val bg = if (tinted) lerp(base, tone(item!!.media!!.artColor!!, 0.14f, 0.55f), 0.9f) else base
    val content = Color(0xFFF2F1F6)
    return IslandPalette(
        bg = bg, content = content, variant = content.copy(alpha = 0.68f),
        accent = accent, accentContainer = accentContainer, onAccentContainer = onAccentContainer,
        track = accent.copy(alpha = 0.25f),
    )
}

/** Medidas derivadas de la cámara y la configuración. */
internal data class Dims(
    val pillH: Dp, val restD: Dp, val cameraW: Dp, val wing: Dp, val wideW: Dp, val cardW: Dp,
    val icon: Dp, val edgePad: Dp,
)

internal val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * Espaciado de la isla, sacado de la guía del banner de llamada entrante:
 * 10 de margen con la pantalla · banner de 76 · 12 de padding junto a un elemento redondo y 16 en los
 * demás bordes · avatar de 46 · 8 entre ícono y texto · 2 entre líneas · 12 entre grupos y botones ·
 * botones de 40 · títulos de 16.
 */
internal object Sp {
    val banner = 76.dp          // alto de una fila tipo banner
    val avatar = 46.dp          // avatar / elemento principal a la izquierda
    val action = 40.dp          // botones redondos y alto de los botones de acción
    val leadRound = 12.dp       // borde junto a un elemento redondo (avatar, ícono)
    val edge = 16.dp            // resto de bordes
    val iconText = 8.dp         // ícono → texto
    val lines = 2.dp            // entre dos líneas de texto
    val group = 12.dp           // entre grupos y entre botones
    val bannerV = (banner - avatar) / 2   // 15: centra el avatar en el banner
}

// ── Composable principal ──────────────────────────────────────────────

/**
 * La isla completa. La usa el overlay real y también la vista previa de la app (con [interactive]
 * en false y callbacks vacíos).
 */
@Composable
fun IslandView(
    ui: IslandUi,
    geo: IslandGeometry,
    s: IslandSettings,
    cb: IslandCallbacks,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    MaterialTheme(
        colorScheme = dynamicDarkColorScheme(ctx),
        typography = remember { dev.jotnar.materialisland.ui.theme.PixelFonts.typography() },
    ) {
        IslandBody(ui, geo, s, cb, modifier)
    }
}

@Composable
private fun IslandBody(ui: IslandUi, geo: IslandGeometry, s: IslandSettings, cb: IslandCallbacks, modifier: Modifier) {
    val density = LocalDensity.current
    val primary = if (ui.glance) null else ui.state.primary
    val targetMode = resolveMode(ui)
    val drop = s.openStyle == OpenStyle.DROP

    // Estilo "gota": la isla no cambia de forma sobre la cámara. Una gota (tamaño de reposo) se
    // estira desde la cámara con un cuello líquido, se suelta, cae rebotando y al aterrizar se abre.
    // Al cerrar: se encoge a gota, sube, el cuello vuelve a formarse y se reabsorbe en la cámara.
    // `mode` es lo que se dibuja (va por detrás de `targetMode` durante la caída/subida).
    var mode by remember { mutableStateOf(targetMode) }
    var lowered by remember { mutableStateOf(drop && !targetMode.isResting()) }
    val pillH = (geo.holeDiameterDp + 2 * s.pillPaddingDp).dp
    val dims = Dims(
        pillH = pillH,
        restD = (geo.holeDiameterDp + s.restSizeAdjustDp).dp,
        cameraW = (geo.holeDiameterDp + 12f).dp,
        wing = s.wingDp.dp,
        wideW = s.wideWidthDp.dp,
        cardW = (geo.widthDp - 2 * s.expandedMarginDp).dp,
        icon = pillH - 12.dp,
        edgePad = 6.dp,
    )

    val stiff = 380f * s.speed
    val sizeSpec = spring<IntSize>(
        // En el estilo gota, el encogimiento a gota no rebota (si no, pasa por una barra delgada).
        dampingRatio = if (drop && mode.isResting()) 0.9f else s.damping,
        stiffness = stiff,
    )
    val dpSpec = spring<Dp>(dampingRatio = s.damping, stiffness = stiff)

    val resting = mode.isResting()
    val holeTop = geo.holeCenterYDp.dp
    val restTop = holeTop - dims.restD / 2 + s.offsetYDp.dp
    val dropTop = holeTop + dims.restD / 2 + s.dropGapDp.dp + s.offsetYDp.dp
    // Punto donde el cuello se corta: un poco antes de llegar, para que la gota "se suelte" y rebote.
    val breakTop = dropTop - s.dropGapDp.dp * 0.35f
    val morphTop by animateDpAsState(
        (if (resting) holeTop - dims.restD / 2 else holeTop - pillH / 2) + s.offsetYDp.dp, dpSpec, label = "top",
    )
    val dropAnim = remember { Animatable(if (lowered) dropTop else restTop, Dp.VectorConverter) }
    // Burbujas de mensajes en el estilo gota: 0 = en su sitio, 1 = guardadas dentro de la cámara.
    // Se guardan antes de que la gota se mueva y vuelven a salir cuando la animación termina.
    val bubblesIn = remember { Animatable(0f) }
    // Lo que va a los lados de la cámara: burbujas (izquierda) y At a glance (derecha).
    val hasUnread = ui.unread.isNotEmpty() || ui.glanceOn
    suspend fun stowBubbles() {
        if (!hasUnread) { bubblesIn.snapTo(1f); return }
        // Entre reposo y píldora siguen visibles junto a la cámara: no hace falta guardarlos.
        val sideMode = { m: Mode -> m == Mode.REST || m == Mode.COMPACT }
        if (sideMode(mode) && sideMode(targetMode)) return
        val visible = mode == Mode.REST || mode == Mode.COMPACT
        if (visible) bubblesIn.animateTo(1f, tween((200 / s.speed).toInt(), easing = FastOutSlowInEasing))
        else bubblesIn.snapTo(1f)
    }
    suspend fun releaseBubbles() = bubblesIn.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f * s.speed))
    LaunchedEffect(targetMode, drop, restTop, dropTop) {
        if (!drop) { mode = targetMode; lowered = false; bubblesIn.snapTo(0f); return@LaunchedEffect }
        val wantDown = !targetMode.isResting()
        when {
            wantDown && !lowered -> {
                // La gota empieza a estirarse cuando las burbujas ya casi están dentro.
                val stow = launch { stowBubbles() }
                delay((130 / s.speed).toLong())
                lowered = true; mode = Mode.REST
                // 1) se estira despacio con el cuello  2) se suelta y cae con rebote  3) se abre
                dropAnim.animateTo(breakTop, tween((240 / s.speed).toInt(), easing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.55f)))
                launch { dropAnim.animateTo(dropTop, spring(dampingRatio = 0.42f, stiffness = stiff * 1.1f)) }
                delay((90 / s.speed).toLong())
                mode = targetMode
                stow.join()
                delay((320 / s.speed).toLong())                                    // que termine de abrirse
                releaseBubbles()
            }
            !wantDown && lowered -> {
                stowBubbles()
                mode = Mode.REST
                delay((220 / s.speed).toLong())                                    // se encoge a gota
                // Sube; las burbujas salen en cuanto la gota se reabsorbe, sin esperar a que el resorte se asiente.
                launch { dropAnim.animateTo(restTop, spring(dampingRatio = 0.62f, stiffness = stiff * 0.8f)) }
                delay((300 / s.speed).toLong())
                lowered = false; mode = targetMode
                releaseBubbles()
            }
            else -> {
                mode = targetMode
                if (!dropAnim.isRunning) dropAnim.snapTo(if (lowered) dropTop else restTop)
                if (bubblesIn.value != 0f) releaseBubbles()
            }
        }
    }
    val top = if (drop) dropAnim.value else morphTop
    val corner by animateDpAsState(
        when (mode) {
            Mode.REST, Mode.HIDDEN -> dims.restD / 2
            Mode.COMPACT, Mode.WIDE -> pillH / 2
            else -> s.expandedCornerDp.dp
        },
        dpSpec, label = "corner",
    )
    val alpha by animateFloatAsState(if (mode == Mode.HIDDEN) 0f else 1f, tween(220), label = "alpha")
    val pal = palette(s, primary, mode)
    val bg by animateColorAsState(if (resting) Color.Black else pal.bg, tween(260), label = "bg")
    val outline = s.outline && !resting || s.calibration
    val outlineColor = if (s.calibration) Color(0xFFFF5449) else pal.accent.copy(alpha = 0.35f)

    // Gestos (con el estado más reciente aunque el pointerInput no se reinicie).
    val curItem by rememberUpdatedState(primary)
    val curMode by rememberUpdatedState(mode)
    var pressed by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        if (pressed) (if (curMode == Mode.EXPANDED || curMode == Mode.PEEK) 0.985f else 0.94f) else 1f,
        spring(dampingRatio = 0.5f, stiffness = 700f), label = "press",
    )
    val stretchY by animateFloatAsState(
        if (dragging) (drag.y * 0.18f).coerceIn(-24f, 40f) else 0f, spring(0.45f, 500f), label = "stretchY",
    )
    val stretchX by animateFloatAsState(
        if (dragging) (drag.x * 0.12f).coerceIn(-30f, 30f) else 0f, spring(0.45f, 500f), label = "stretchX",
    )

    var mainRect by remember { mutableStateOf(Rect.Zero) }
    var satRect by remember { mutableStateOf<Rect?>(null) }
    var bubblesRect by remember { mutableStateOf<Rect?>(null) }
    val showBubbles = ui.unread.isNotEmpty() && (mode == Mode.REST || mode == Mode.COMPACT)
    val bubblesTouchable = showBubbles && bubblesIn.value == 0f
    var glanceRect by remember { mutableStateOf<Rect?>(null) }
    val showGlance = ui.glanceOn && (mode == Mode.REST || mode == Mode.COMPACT)
    val glanceTouchable = showGlance && bubblesIn.value == 0f

    val secondary = ui.state.secondary
    val showSat = s.showSecondary && mode == Mode.COMPACT && secondary != null
    var lastSecondary by remember { mutableStateOf(secondary) }
    if (secondary != null) lastSecondary = secondary

    // Zona táctil: solo la isla (y la burbuja). Fuera de ella los toques pasan a la app de debajo.
    val idleTouchable = s.idleTap != IdleAction.NONE || s.idleLongPress != IdleAction.NONE ||
        s.idleDoubleTap != IdleAction.NONE || s.calibration
    // El doble toque retrasa el toque simple, así que solo se escucha en reposo y si está configurado.
    val doubleTapOn = mode == Mode.REST && s.idleDoubleTap != IdleAction.NONE
    LaunchedEffect(mainRect, satRect, bubblesRect, glanceRect, mode, showSat, bubblesTouchable, glanceTouchable, idleTouchable) {
        val m = with(density) { 6.dp.toPx() }
        val rects = buildList {
            val touchMain = when (mode) {
                Mode.HIDDEN -> false
                Mode.REST -> idleTouchable
                else -> true
            }
            if (touchMain && mainRect != Rect.Zero) add(mainRect.inflate(m).toAndroid())
            if (showSat) satRect?.let { add(it.inflate(m).toAndroid()) }
            if (bubblesTouchable) bubblesRect?.let { add(it.inflate(m).toAndroid()) }
            if (glanceTouchable) glanceRect?.let { add(it.inflate(m).toAndroid()) }
        }
        cb.onBounds(rects)
    }

    val cx = with(density) { (geo.holeCenterXDp + s.offsetXDp).dp.roundToPx() }
    val gapPx = with(density) { 6.dp.roundToPx() }

    Layout(
        contents = listOf(
            {
                // El contenedor lleva fondo, forma y gestos; AnimatedContent solo anima tamaño y contenido.
                Box(
                    Modifier
                            .graphicsLayer {
                                this.alpha = alpha
                                scaleX = pressScale * (1f + abs(stretchX) / 600f)
                                scaleY = pressScale * (1f + stretchY.coerceAtLeast(0f) / 400f)
                                translationX = stretchX
                                translationY = stretchY
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            }
                            .onGloballyPositioned { mainRect = it.boundsInWindow() }
                            .clip(RoundedCornerShape(corner))
                            .background(bg)
                            .then(if (outline) Modifier.border(1.dp, outlineColor, RoundedCornerShape(corner)) else Modifier)
                            .pointerInput(doubleTapOn) {
                                detectTapGestures(
                                    onPress = {
                                        pressed = true; cb.onPressChanged(true)
                                        tryAwaitRelease()
                                        pressed = false; cb.onPressChanged(false)
                                    },
                                    onDoubleTap = if (doubleTapOn) { _ -> cb.onDoubleTap(curItem, curMode) } else null,
                                    onTap = { cb.onTap(curItem, curMode) },
                                    onLongPress = { cb.onLongPress(curItem, curMode) },
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { drag = Offset.Zero; dragging = true },
                                    onDragCancel = { dragging = false },
                                    onDragEnd = {
                                        dragging = false
                                        val d = drag
                                        if (maxOf(abs(d.x), abs(d.y)) > 16.dp.toPx()) {
                                            val dir = if (abs(d.x) > abs(d.y)) {
                                                if (d.x > 0) Swipe.RIGHT else Swipe.LEFT
                                            } else if (d.y > 0) Swipe.DOWN else Swipe.UP
                                            cb.onSwipe(curItem, curMode, dir)
                                        }
                                    },
                                    onDrag = { c, delta -> c.consume(); drag += delta },
                                )
                            },
                ) {
                    AnimatedContent(
                        targetState = Frame(mode, primary, ui.glance),
                        contentKey = { Triple(it.mode, it.item?.id, it.glance) },
                        transitionSpec = {
                            (fadeIn(tween(200, delayMillis = 70)) + scaleIn(spring(s.damping, stiff), initialScale = 0.92f))
                                .togetherWith(fadeOut(tween(80)) + scaleOut(tween(120), targetScale = 0.96f))
                                .using(SizeTransform(clip = true) { _, _ -> sizeSpec })
                        },
                        contentAlignment = Alignment.TopCenter,
                        label = "island",
                ) { frame ->
                        val item = frame.item
                        val p = palette(s, item, frame.mode)
                        when (frame.mode) {
                            Mode.HIDDEN, Mode.REST -> Box(Modifier.size(dims.restD))
                            Mode.COMPACT -> if (item != null) Compact(item, p, dims, s)
                            Mode.WIDE -> if (item != null) Wide(item, p, dims, ui.copiedId == item.id)
                            Mode.PEEK, Mode.EXPANDED -> if (frame.glance) GlanceCard(s, p, dims, ui, cb)
                                else if (item != null) Card(item, p, dims, s, ui, cb, sizeSpec)
                                else if (ui.shortcuts) ShortcutsCard(s, p, dims, ui.shortcutTick, cb)
                        }
                    }
                }
            },
            {
                AnimatedVisibility(
                    visible = showSat,
                    enter = scaleIn(spring(0.5f, 500f)) + fadeIn(),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150)),
                ) {
                    val sec = lastSecondary
                    Box(
                        Modifier
                            .size(pillH)
                            .onGloballyPositioned { satRect = it.boundsInWindow() }
                            .clip(CircleShape)
                            .background(pal.bg)
                            .pointerInput(Unit) { detectTapGestures { cb.onSecondaryTap() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sec != null) CompactLeft(sec, palette(s, sec, Mode.COMPACT), dims.icon, s)
                    }
                }
                if (!showSat) LaunchedEffect(Unit) { satRect = null }
            },
            {
                AnimatedVisibility(
                    visible = showBubbles,
                    enter = scaleIn(spring(0.55f, 450f), transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)) + fadeIn(),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150)),
                ) {
                    UnreadBubbles(
                        ui.unread, s.maxBubbles.coerceAtLeast(1), pillH - 12.dp, cb,
                        Modifier
                            .graphicsLayer {
                                // Se encogen hacia su borde derecho (el lado de la cámara) al guardarse.
                                val k = bubblesIn.value
                                scaleX = 1f - 0.8f * k; scaleY = 1f - 0.8f * k
                                this.alpha = (1f - k * k).coerceIn(0f, 1f)
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                            }
                            .onGloballyPositioned { bubblesRect = it.boundsInWindow() },
                    )
                }
            },
            {
                AnimatedVisibility(
                    visible = showGlance,
                    enter = scaleIn(spring(0.55f, 450f), transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)) + fadeIn(),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150)),
                ) {
                    GlanceChip(
                        ui, pillH - 12.dp, cb,
                        Modifier
                            .graphicsLayer {
                                val k = bubblesIn.value
                                scaleX = 1f - 0.8f * k; scaleY = 1f - 0.8f * k
                                this.alpha = (1f - k * k).coerceIn(0f, 1f)
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .onGloballyPositioned { glanceRect = it.boundsInWindow() },
                    )
                }
                if (!showGlance) LaunchedEffect(Unit) { glanceRect = null }
            },
        ),
        modifier = modifier.fillMaxSize().then(
            if (drop && mode != Mode.HIDDEN) Modifier.drawBehind {
                drawLiquidNeck(
                    cx = cx.toFloat(),
                    camCenterY = (holeTop + s.offsetYDp.dp).toPx(),
                    r = (dims.restD / 2).toPx(),
                    dropTop = top.toPx(),
                    breakGap = (breakTop - restTop - dims.restD).toPx(),
                    color = bg.copy(alpha = alpha),
                )
            } else Modifier,
        ),
    ) { (mainM, satM, bubM, glM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val main = mainM.firstOrNull()?.measure(loose)
        val sat = satM.firstOrNull()?.measure(loose)
        val bub = bubM.firstOrNull()?.measure(loose)
        val gl = glM.firstOrNull()?.measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val topPx = top.roundToPx()
            if (main != null) {
                val x = (cx - main.width / 2).coerceIn(0, (constraints.maxWidth - main.width).coerceAtLeast(0))
                main.place(x, topPx)
                sat?.place(x + main.width + gapPx, topPx + (pillH.roundToPx() - sat.height) / 2)
                // Burbujas a la izquierda y At a glance a la derecha, centrados con la cámara.
                // Morph: junto a la isla. Gota: siempre junto a la cámara (la píldora cae debajo, ellos no se mueven).
                // k: 0 = en su sitio, 1 = guardados dentro de la cámara (solo en el estilo gota).
                val k = if (drop) bubblesIn.value else 0f
                val camY = (geo.holeCenterYDp + s.offsetYDp).dp.roundToPx()
                val camR = (dims.restD / 2).roundToPx()
                fun placeSide(pl: androidx.compose.ui.layout.Placeable, px: Int, stowX: Int) {
                    val py = camY - pl.height / 2
                    pl.place((px + (stowX - px) * k).roundToInt(), py)
                }
                if (bub != null && bub.width > 0) {
                    val bx = (if (drop) cx - camR else x) - gapPx - bub.width
                    placeSide(bub, bx, cx - bub.width)
                }
                if (gl != null && gl.width > 0) {
                    val satW = if (!drop && sat != null && sat.width > 0) sat.width + gapPx else 0
                    val gx = (if (drop) cx + camR else x + main.width + satW) + gapPx
                    placeSide(gl, gx, cx)
                }
            }
        }
    }
}

private data class Frame(val mode: Mode, val item: IslandItem?, val glance: Boolean = false)

private fun Mode.isResting() = this == Mode.REST || this == Mode.HIDDEN

/**
 * Cuello líquido entre la cámara y la gota, tratadas como dos círculos de radio [r]. Mientras se
 * solapan, rellena la "cintura" para que parezcan una sola forma que se estira; al separarse, el
 * cuello se adelgaza y se corta cuando el hueco llega a [breakGap].
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidNeck(
    cx: Float, camCenterY: Float, r: Float, dropTop: Float, breakGap: Float, color: Color,
) {
    val dropCenterY = dropTop + r
    val d = dropCenterY - camCenterY                   // distancia entre centros
    val gap = d - 2 * r                                // hueco entre bordes (negativo = se solapan)
    if (d < r * 0.25f || breakGap <= 0f || gap >= breakGap) return
    // Ancho de la cintura: casi todo el radio cuando se solapan, se cierra hasta 0 en breakGap.
    val waist = if (gap <= 0f) r * (0.92f - 0.35f * (d / (2 * r)))
    else r * 0.57f * (1f - gap / breakGap).let { it * it }
    // Puntos de anclaje en cada círculo (ángulo de ~55° desde el eje vertical).
    val ax = r * 0.82f; val ay = r * 0.57f
    val yA = camCenterY + ay; val yB = dropCenterY - ay
    val mid = (yA + yB) / 2f; val k = (yB - yA) * 0.45f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - ax, yA)
        cubicTo(cx - ax + r * 0.12f, yA + k * 0.6f, cx - waist, mid - k * 0.5f, cx - waist, mid)
        cubicTo(cx - waist, mid + k * 0.5f, cx - ax + r * 0.12f, yB - k * 0.6f, cx - ax, yB)
        lineTo(cx + ax, yB)
        cubicTo(cx + ax - r * 0.12f, yB - k * 0.6f, cx + waist, mid + k * 0.5f, cx + waist, mid)
        cubicTo(cx + waist, mid - k * 0.5f, cx + ax - r * 0.12f, yA + k * 0.6f, cx + ax, yA)
        close()
    }
    drawPath(path, color)
    // La parte de arriba del cuello nace dentro de la cámara: un círculo detrás la "tapa" igual que el agujero real.
    drawCircle(color, r, androidx.compose.ui.geometry.Offset(cx, camCenterY))
}

private fun Rect.toAndroid() = android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

// ── Burbujas de mensajes sin leer ─────────────────────────────────────

/**
 * Avatares apilados a la izquierda de la isla (el más reciente pegado a la isla y por encima).
 * Si hay más de [max], el primero de la izquierda es un "+N" que abre el panel.
 */
@Composable
private fun UnreadBubbles(items: List<IslandItem>, max: Int, size: Dp, cb: IslandCallbacks, modifier: Modifier) {
    val shown = if (items.size > max) items.take(max - 1) else items.take(max)
    val extra = items.size - shown.size
    val step = size * 0.68f
    // Orden de dibujo de izquierda a derecha: "+N", los más viejos, ..., el más reciente.
    val slots: List<IslandItem?> = (if (extra > 0) listOf<IslandItem?>(null) else emptyList()) + shown.reversed()
    Box(modifier.width(size + step * (slots.size - 1)).height(size)) {
        slots.forEachIndexed { i, item ->
            androidx.compose.runtime.key(item?.id ?: "more") {
                val scale = remember { androidx.compose.animation.core.Animatable(0.3f) }
                LaunchedEffect(Unit) { scale.animateTo(1f, spring(0.5f, 420f)) }
                Box(
                    Modifier
                        .padding(start = step * i)
                        .size(size)
                        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                        .pointerInput(item?.id) {
                            detectTapGestures(
                                onTap = { if (item != null) cb.onBubbleTap(item) else cb.onBubbleOverflow() },
                                onLongPress = { if (item != null) cb.onBubbleLongPress(item) else cb.onBubbleOverflow() },
                            )
                        },
                ) {
                    if (item == null) {
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape).background(Color.Black).padding(2.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+$extra", color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    } else {
                        val face = item.image ?: item.appIcon
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black).padding(2.dp).clip(CircleShape)) {
                            if (face != null) Image(face.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item.title.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        // Insignia con el ícono de la app (solo si la cara es un avatar).
                        val badge = item.appIcon
                        if (item.image != null && badge != null) {
                            Image(
                                badge.asImageBitmap(), null,
                                Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(size * 0.42f)
                                    .clip(CircleShape).background(Color.Black).padding(1.5.dp).clip(CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Píldora compacta ─────────────────────────────────────────────────

@Composable
private fun Compact(item: IslandItem, p: IslandPalette, d: Dims, s: IslandSettings) {
    Row(
        Modifier.width(d.wing * 2 + d.cameraW).height(d.pillH).padding(horizontal = d.edgePad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { CompactLeft(item, p, d.icon, s) }
        Spacer(Modifier.width(d.cameraW))
        Box(Modifier.weight(1f).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) { CompactRight(item, p, d.icon) }
    }
}

@Composable
fun AppOrKindIcon(item: IslandItem, p: IslandPalette, size: Dp) {
    val bmp = item.appIcon
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
            Icon(kindIcon(item), null, tint = p.onAccentContainer, modifier = Modifier.size(size * 0.6f))
        }
    }
}

@Composable
private fun CompactLeft(item: IslandItem, p: IslandPalette, size: Dp, s: IslandSettings) {
    when (item.kind) {
        Kind.MEDIA -> MorphingArt(item.media?.art, item.media?.playing == true, size, s.morphingArt, p.accentContainer)
        Kind.CALL -> PulsingShapeIcon(Icons.Rounded.Call, CallGreen, Color.White, size, item.incoming)
        Kind.RECORDING -> PulsingShapeIcon(Icons.Rounded.FiberManualRecord, CallRed, Color.White, size, true)
        Kind.TIMER -> Box(Modifier.size(size).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Timer, null, tint = p.onAccentContainer, modifier = Modifier.size(size * 0.62f))
        }
        Kind.NAVIGATION -> Box(Modifier.size(size).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
            val img = item.image
            if (img != null) Image(img.asImageBitmap(), null, Modifier.size(size * 0.72f))
            else Icon(Icons.Rounded.Navigation, null, tint = p.onAccentContainer, modifier = Modifier.size(size * 0.62f))
        }
        else -> AppOrKindIcon(item, p, size)
    }
}

@Composable
private fun CompactRight(item: IslandItem, p: IslandPalette, size: Dp) {
    val style = MaterialTheme.typography.labelLarge.merge(Tabular).copy(fontWeight = FontWeight.SemiBold)
    @Composable
    fun T(text: String?, color: Color = p.accent) =
        Text(text ?: "", style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

    when (item.kind) {
        Kind.MEDIA -> Visualizer(item.media?.playing == true, p.accent)
        Kind.CALL -> if (item.incoming) T("Incoming", SoftGreen) else T(chronoText(item, rememberNow()) ?: "On call", SoftGreen)
        Kind.RECORDING -> T(chronoText(item, rememberNow()) ?: "REC", SoftRed)
        Kind.TIMER -> T(chronoText(item, rememberNow()) ?: item.compactText ?: item.title)
        Kind.NAVIGATION -> T(item.compactText ?: item.title)
        Kind.PROGRESS, Kind.LIVE -> when {
            item.indeterminate -> LoadingIndicator(Modifier.size(size), color = p.accent)
            item.progress >= 0f -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { item.progress }, modifier = Modifier.size(size * 0.82f),
                    color = p.accent, trackColor = p.track, strokeWidth = 3.dp,
                )
            }
            else -> T(item.compactText ?: item.title)
        }
        else -> T(item.compactText ?: item.title)
    }
}

// ── Píldora ancha (eventos del sistema, códigos, pagos) ───────────────

@Composable
private fun Wide(item: IslandItem, p: IslandPalette, d: Dims, copied: Boolean) {
    if (item.kind == Kind.CHARGING && item.charge != null) { ChargingPill(item, item.charge, p, d); return }
    if (item.kind == Kind.BLUETOOTH && item.buds != null) { BudsPill(item, item.buds, p, d); return }
    val color = when (item.kind) {
        Kind.CHARGING -> if (item.compactText == "off") p.accent else SoftGreen
        Kind.BATTERY -> SoftRed
        Kind.PAYMENT -> SoftGreen
        else -> p.accent
    }
    Row(
        Modifier.width(d.wideW).height(d.pillH).padding(horizontal = d.edgePad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(d.icon).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(kindIcon(item), null, tint = color, modifier = Modifier.size(d.icon * 0.6f))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (copied) "Copied" else item.title, style = MaterialTheme.typography.labelLarge,
                color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(d.cameraW))
        Box(Modifier.weight(1f).padding(end = Sp.iconText), contentAlignment = Alignment.CenterEnd) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (copied) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(18.dp).padding(end = 2.dp))
                Text(
                    item.value ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = color,
                    style = MaterialTheme.typography.titleSmall.merge(Tabular).copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (item.kind == Kind.OTP) 2.sp else 0.sp,
                    ),
                )
            }
        }
    }
}

// ── Carga ────────────────────────────────────────────────────────────

private val ChargeGreen = Color(0xFF6DD58C)

/** postedAt del aviso de carga que ya hizo su animación de entrada. */
private var chargeIntroShownFor = 0L

/** Estado de carga en vivo: la demo usa sus datos fijos, lo real se relee cada 2 s mientras se ve. */
@Composable
private fun rememberCharge(initial: ChargeInfo): ChargeInfo {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val live by androidx.compose.runtime.produceState(initial, initial.demo) {
        if (initial.demo) return@produceState
        while (true) {
            value = dev.jotnar.materialisland.core.Battery.read(ctx); delay(2000)
        }
    }
    return live
}

/**
 * Anillo ondulado de Material 3 Expressive que se llena hasta el nivel, con el rayo latiendo dentro
 * y una onda que sale del centro al conectar.
 */
@Composable
private fun ChargeRing(level: Int, charging: Boolean, color: Color, track: Color, size: Dp, iconSize: Dp, ripple: Boolean, intro: Boolean = true) {
    val fill = remember { Animatable(if (intro) 0f else level / 100f) }
    LaunchedEffect(level) { fill.animateTo(level / 100f, spring(dampingRatio = 0.8f, stiffness = 60f)) }
    val pulse = if (charging) {
        val t = rememberInfiniteTransition(label = "bolt")
        t.animateFloat(0.86f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s").value
    } else 1f
    val wave = remember { Animatable(0f) }
    LaunchedEffect(ripple) {
        if (!ripple) return@LaunchedEffect
        repeat(2) { wave.snapTo(0f); wave.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (ripple) Box(
            Modifier.size(size).drawBehind {
                val k = wave.value
                if (k in 0.001f..0.999f) drawCircle(color.copy(alpha = 0.45f * (1f - k)), radius = this.size.minDimension / 2 * (0.6f + 1.1f * k))
            },
        )
        CircularWavyProgressIndicator(
            progress = { fill.value }, modifier = Modifier.size(size),
            color = color, trackColor = track,
            stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { (size / 12).toPx() }, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            trackStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { (size / 12).toPx() }, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            amplitude = { if (charging) 1f else 0f },
        )
        Icon(
            Icons.Rounded.Bolt, null, tint = color,
            modifier = Modifier.size(iconSize).graphicsLayer { scaleX = pulse; scaleY = pulse },
        )
    }
}

/** Porcentaje que cuenta desde 0 hasta el nivel. */
@Composable
private fun countUp(level: Int, intro: Boolean = true): Int {
    val a = remember { Animatable(if (intro) 0f else level.toFloat()) }
    LaunchedEffect(level) { a.animateTo(level.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }
    return a.value.roundToInt()
}

/** Primer estado: píldora con anillo, título y porcentaje, y un barrido de luz al conectar. */
@Composable
private fun ChargingPill(item: IslandItem, c: ChargeInfo, p: IslandPalette, d: Dims) {
    val on = item.compactText == "on"
    val color = if (on) ChargeGreen else p.variant
    // La entrada animada solo una vez por conexión: al volver de la tarjeta no se repite.
    val intro = remember { (on && item.postedAt != chargeIntroShownFor).also { chargeIntroShownFor = item.postedAt } }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(on) { if (on && intro) sweep.animateTo(1f, tween(1300, delayMillis = 150, easing = FastOutSlowInEasing)) }
    Row(
        Modifier.width(d.wideW).height(d.pillH)
            .drawBehind {
                val k = sweep.value
                if (on && k in 0.001f..0.999f) {
                    val band = this.size.width * 0.45f
                    val x = -band + (this.size.width + band) * k
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent, 0.5f to ChargeGreen.copy(alpha = 0.28f), 1f to Color.Transparent,
                            startX = x, endX = x + band,
                        ),
                    )
                }
            }
            .padding(horizontal = d.edgePad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            ChargeRing(c.level, on, color, color.copy(alpha = 0.22f), d.icon, d.icon * 0.55f, ripple = on && intro, intro = intro)
            Spacer(Modifier.width(Sp.iconText))
            Text(
                if (on) "Charging" else item.title, style = MaterialTheme.typography.labelLarge, color = p.content,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(d.cameraW))
        Row(Modifier.weight(1f).padding(end = Sp.iconText), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (on && c.speed == ChargeInfo.Speed.FAST) {
                Text(
                    "FAST", color = ChargeGreen, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(ChargeGreen.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "${if (on) countUp(c.level, intro) else c.level}%", color = color, maxLines = 1,
                style = MaterialTheme.typography.titleSmall.merge(Tabular).copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun formatFullIn(c: ChargeInfo): String = when {
    c.level >= 100 -> "Fully charged"
    !c.charging -> "Not charging"
    c.fullInMs <= 0 -> "Estimating time to full…"
    else -> {
        val min = ((c.fullInMs + 59_999) / 60_000).toInt()
        val t = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
        "Full in $t"
    }
}

/** Dato pequeño de la tarjeta de carga: ícono + texto en una pastilla de 40. */
@Composable
private fun ChargeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, p: IslandPalette, modifier: Modifier) {
    Row(
        modifier.height(Sp.action).clip(RoundedCornerShape(Sp.action / 2)).background(p.accentContainer.copy(alpha = 0.45f))
            .padding(horizontal = Sp.group),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = p.content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Sp.iconText / 2 + 2.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Segundo estado: nivel grande, tiempo hasta el 100 %, barra y datos del cargador. */
@Composable
private fun ChargingCard(item: IslandItem, p: IslandPalette) {
    val c = rememberCharge(item.charge ?: return)
    val color = if (c.charging) ChargeGreen else p.variant
    Column(Modifier.fillMaxWidth().padding(Sp.edge)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChargeRing(c.level, c.charging, color, color.copy(alpha = 0.2f), Sp.banner, 34.dp, ripple = false)
            Spacer(Modifier.width(Sp.edge))
            Column(Modifier.weight(1f)) {
                Caption(if (c.charging) c.speed.label else "Not charging", color)
                Spacer(Modifier.height(Sp.lines))
                Text(
                    "${c.level}%", color = p.content,
                    style = MaterialTheme.typography.displaySmall.merge(Tabular).copy(fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(Sp.lines))
                Text(formatFullIn(c), style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(Sp.edge))
        WavyProgress(c.level / 100f, animate = c.charging, color = color, track = color.copy(alpha = 0.2f))
        Spacer(Modifier.height(Sp.edge))
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.iconText)) {
            val plugIcon = when (c.plug) {
                ChargeInfo.Plug.WIRELESS -> Icons.Rounded.Waves
                ChargeInfo.Plug.DOCK -> Icons.Rounded.Dock
                ChargeInfo.Plug.NONE -> Icons.Rounded.PowerOff
                else -> Icons.Rounded.Cable
            }
            ChargeChip(plugIcon, c.plug.label, p, Modifier.weight(1f))
            ChargeChip(Icons.Rounded.Bolt, c.watts?.let { "%.1f W".format(it) } ?: c.speed.label.substringBefore(" "), p, Modifier.weight(1f))
            c.tempC?.let { ChargeChip(Icons.Rounded.Thermostat, "%.0f °C".format(it), p, Modifier.weight(1f)) }
        }
    }
}

// ── Tarjeta de accesos (cámara en reposo) ─────────────────────────────

private fun shortcutIcon(a: IdleAction, torch: Boolean, ringer: Int): androidx.compose.ui.graphics.vector.ImageVector = when (a) {
    IdleAction.FLASHLIGHT -> if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff
    IdleAction.SOUND_MODE -> when (ringer) {
        android.media.AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration
        android.media.AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff
        else -> Icons.Rounded.NotificationsActive
    }
    IdleAction.VOLUME -> Icons.Rounded.VolumeUp
    IdleAction.ALARMS -> Icons.Rounded.Alarm
    IdleAction.TIMER -> Icons.Rounded.HourglassTop
    IdleAction.CAMERA -> Icons.Rounded.PhotoCamera
    IdleAction.INTERNET -> Icons.Rounded.Wifi
    IdleAction.SCREENSHOT -> Icons.Rounded.Screenshot
    IdleAction.NOTIFICATIONS -> Icons.Rounded.Notifications
    IdleAction.QUICK_SETTINGS -> Icons.Rounded.Tune
    IdleAction.LOCK_SCREEN -> Icons.Rounded.Lock
    IdleAction.OPEN_ISLAND_APP -> Icons.Rounded.Settings
    else -> Icons.Rounded.Apps
}

private fun shortcutLabel(a: IdleAction, ringer: Int): String = when (a) {
    IdleAction.SOUND_MODE -> when (ringer) {
        android.media.AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
        android.media.AudioManager.RINGER_MODE_SILENT -> "Silent"
        else -> "Sound on"
    }
    IdleAction.OPEN_ISLAND_APP -> "Island"
    IdleAction.QUICK_SETTINGS -> "Quick settings"
    else -> a.label
}

/**
 * Accesos rápidos en cuadrícula de 3 columnas. La linterna y el modo de sonido muestran su estado
 * (encendidos = color de acento).
 */
@Composable
private fun ShortcutsCard(s: IslandSettings, p: IslandPalette, d: Dims, tick: Int, cb: IslandCallbacks) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val torch = remember(tick) { dev.jotnar.materialisland.core.Actions.torchOn }
    val ringer = remember(tick) { dev.jotnar.materialisland.core.Actions.ringerMode(ctx) }
    val items = s.shortcuts.filter { it != IdleAction.NONE && it != IdleAction.SHORTCUTS }
    Column(Modifier.width(d.cardW).padding(Sp.edge)) {
        Caption("Shortcuts", p.variant)
        Spacer(Modifier.height(Sp.group))
        if (items.isEmpty()) {
            Text("Choose shortcuts in Gestures.", style = MaterialTheme.typography.bodyMedium, color = p.variant)
        }
        items.chunked(3).forEachIndexed { r, row ->
            if (r > 0) Spacer(Modifier.height(Sp.group))
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.group)) {
                row.forEach { a ->
                    val active = (a == IdleAction.FLASHLIGHT && torch) ||
                        (a == IdleAction.SOUND_MODE && ringer != android.media.AudioManager.RINGER_MODE_NORMAL)
                    val bg by androidx.compose.animation.animateColorAsState(
                        if (active) p.accent else p.accentContainer.copy(alpha = 0.55f), label = "tile",
                    )
                    val fg = if (active) Color.Black.copy(alpha = 0.85f) else p.content
                    Column(
                        Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(22.dp)).background(bg)
                            .pointerInput(a) { detectTapGestures { cb.onShortcut(a) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(shortcutIcon(a, torch, ringer), null, tint = fg, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(Sp.iconText - 2.dp))
                        Text(
                            shortcutLabel(a, ringer), style = MaterialTheme.typography.labelMedium, color = fg,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Tarjeta expandida / aviso ─────────────────────────────────────────

@Composable
private fun Card(
    item: IslandItem, p: IslandPalette, d: Dims, s: IslandSettings, ui: IslandUi, cb: IslandCallbacks,
    sizeSpec: androidx.compose.animation.core.FiniteAnimationSpec<IntSize>,
) {
    Column(Modifier.width(d.cardW).animateContentSize(sizeSpec)) {
        when (item.kind) {
            // Llamadas y mensajes son banners: la etiqueta de la app va encima del nombre, sin encabezado.
            Kind.CALL -> CallBody(item, p, cb)
            Kind.NOTIFICATION -> NotificationBody(item, p, ui, cb)
            Kind.CHARGING -> ChargingCard(item, p)
            Kind.BLUETOOTH -> BudsCard(item, p)
            Kind.MEDIA -> androidx.compose.animation.AnimatedContent(
                targetState = ui.full,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                        .using(androidx.compose.animation.SizeTransform(clip = true) { _, _ -> sizeSpec })
                },
                label = "mediaLevel",
            ) { full ->
                if (full) MediaFull(item, p, d, s, cb) else MediaCard(item, p, s, cb)
            }
            else -> {
                CardHeader(item, p, d)
                Column(Modifier.padding(start = Sp.edge, end = Sp.edge, bottom = Sp.edge)) {
                    when (item.kind) {
                        Kind.TIMER, Kind.RECORDING -> TimerBody(item, p, cb)
                        Kind.NAVIGATION -> NavBody(item, p, cb)
                        else -> ProgressBody(item, p, cb)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardHeader(item: IslandItem, p: IslandPalette, d: Dims) {
    Row(
        Modifier.fillMaxWidth().height(d.pillH).padding(horizontal = Sp.edge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AppOrKindIcon(item, p, 20.dp)
            Spacer(Modifier.width(Sp.iconText))
            Text(
                item.appLabel ?: kindLabel(item.kind), style = MaterialTheme.typography.labelLarge,
                color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(d.cameraW))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            val style = MaterialTheme.typography.labelLarge.merge(Tabular)
            when (item.kind) {
                Kind.MEDIA -> Visualizer(item.media?.playing == true, p.accent, height = 14.dp)
                Kind.CALL -> Text(
                    if (item.incoming) "Incoming" else chronoText(item, rememberNow()) ?: "", style = style, color = SoftGreen,
                )
                Kind.PROGRESS, Kind.LIVE -> if (item.progress >= 0f) Text("${(item.progress * 100).roundToInt()}%", style = style, color = p.accent)
                Kind.NOTIFICATION -> Text("now", style = style, color = p.variant)
                else -> Unit
            }
        }
    }
}

@Composable
private fun ActionRow(item: IslandItem, p: IslandPalette, cb: IslandCallbacks, actions: List<IslandAction> = item.actions) {
    if (actions.isEmpty()) return
    Spacer(Modifier.height(Sp.group))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sp.group)) {
        actions.take(3).forEach { a ->
            FilledTonalButton(
                onClick = { if (a.onReply != null) cb.onReplyStart(item) else cb.onAction(item, a) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = p.accentContainer, contentColor = p.onAccentContainer,
                ),
                contentPadding = PaddingValues(horizontal = Sp.group, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(Sp.action),
            ) {
                if (a.onReply != null) {
                    Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Sp.iconText / 2))
                }
                Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Posición actual que avanza sola mientras suena. */
@Composable
private fun rememberMediaPos(m: dev.jotnar.materialisland.model.MediaInfo): Long {
    val now by androidx.compose.runtime.produceState(android.os.SystemClock.elapsedRealtime(), m.playing) {
        while (m.playing) {
            value = android.os.SystemClock.elapsedRealtime(); delay(250)
        }
    }
    return m.currentPosition(now)
}

/** Play/pausa que cambia de forma: píldora en pausa, cuadrado redondeado sonando (como el Pixel). */
@Composable
private fun PlayPauseButton(playing: Boolean, container: Color, content: Color, height: Dp, onClick: () -> Unit) {
    // Siempre el onClick más reciente: si no, se queda con el estado del primer toque y solo hace play o solo pausa.
    val click by androidx.compose.runtime.rememberUpdatedState(onClick)
    val radius by animateDpAsState(if (playing) height / 3 else height / 2, spring(0.5f, 400f), label = "pp")
    val width by animateDpAsState(if (playing) height * 1.25f else height * 1.5f, spring(0.55f, 400f), label = "ppw")
    Box(
        Modifier
            .width(width).height(height)
            .clip(RoundedCornerShape(radius))
            .background(container)
            .pointerInput(Unit) { detectTapGestures { click() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause",
            tint = content, modifier = Modifier.size(height * 0.55f),
        )
    }
}

@Composable
private fun MediaControls(
    item: IslandItem, playing: Boolean, cb: IslandCallbacks,
    iconTint: Color, playContainer: Color, playContent: Color, sideContainer: Color?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sp.group * 2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun Side(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, cmd: MediaCmd) {
            val current by androidx.compose.runtime.rememberUpdatedState(item)
            Box(
                Modifier.size(Sp.avatar).clip(CircleShape)
                    .then(if (sideContainer != null) Modifier.background(sideContainer) else Modifier)
                    .pointerInput(Unit) { detectTapGestures { cb.onMedia(current, cmd) } },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, desc, tint = iconTint, modifier = Modifier.size(26.dp)) }
        }
        Side(Icons.Rounded.SkipPrevious, "Previous", MediaCmd.PREV)
        PlayPauseButton(playing, playContainer, playContent, 52.dp) { cb.onMedia(item, MediaCmd.PLAY_PAUSE) }
        Side(Icons.Rounded.SkipNext, "Next", MediaCmd.NEXT)
    }
}

/** Barra de progreso con los tiempos a los lados: 0:57 ~~~~~─── 3:35 */
@Composable
private fun MediaProgress(item: IslandItem, pos: Long, color: Color, track: Color, label: Color, cb: IslandCallbacks) {
    val m = item.media ?: return
    if (m.durationMs <= 0) return
    val style = MaterialTheme.typography.labelMedium.merge(Tabular)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(pos), style = style, color = label)
        Spacer(Modifier.width(Sp.group))
        Box(Modifier.weight(1f)) {
            WavyProgress(
                progress = pos.toFloat() / m.durationMs, animate = m.playing, color = color, track = track,
                onSeek = { f -> cb.onSeek(item, f) },
            )
        }
        Spacer(Modifier.width(Sp.group))
        Text(formatDuration(m.durationMs), style = style, color = label)
    }
}

/**
 * Nivel 2: carátula 64 · título y artista · visualizador a la derecha; debajo la barra con los tiempos
 * a los lados y los controles centrados.
 */
@Composable
private fun MediaCard(item: IslandItem, p: IslandPalette, s: IslandSettings, cb: IslandCallbacks) {
    val m = item.media ?: return
    val pos = rememberMediaPos(m)
    Column(Modifier.fillMaxWidth().padding(Sp.edge + 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingArt(m.art, m.playing, 64.dp, s.morphingArt, p.accentContainer)
            Spacer(Modifier.width(Sp.edge))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = p.content, maxLines = 1, modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(Sp.lines))
                Text(m.artist, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Sp.group))
            Visualizer(m.playing, p.accent, height = 22.dp, bars = 5)
        }
        Spacer(Modifier.height(Sp.edge + 4.dp))
        MediaProgress(item, pos, p.accent, p.track, p.variant, cb)
        Spacer(Modifier.height(Sp.edge))
        MediaControls(
            item, m.playing, cb, iconTint = p.content,
            playContainer = p.accent, playContent = Color.Black.copy(alpha = 0.85f), sideContainer = null,
        )
    }
}

/**
 * Nivel 3: la carátula ocupa toda la tarjeta y los controles flotan encima sobre un panel que
 * desenfoca la misma carátula (efecto de cristal), para separar los controles de la imagen.
 */
@Composable
private fun MediaFull(item: IslandItem, p: IslandPalette, d: Dims, s: IslandSettings, cb: IslandCallbacks) {
    val m = item.media ?: return
    val pos = rememberMediaPos(m)
    val art = m.art
    var panelH by remember { mutableStateOf(0) }
    val fallback = Brush.linearGradient(listOf(p.accentContainer, p.bg))
    Box(Modifier.width(d.cardW).height(d.cardW * 1.08f)) {
        @Composable
        fun ArtLayer(modifier: Modifier) {
            if (art != null) Image(art.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
            else Box(modifier.background(fallback))
        }
        ArtLayer(Modifier.matchParentSize())
        // Desenfoque progresivo: varias copias de la carátula, cada una más desenfocada y visible
        // más abajo, que se funden con un degradado. Arriba nítida → abajo desenfoque completo.
        val fadeLen = 72.dp
        listOf(6.dp to 0f, 14.dp to 0.33f, 30.dp to 0.66f).forEach { (radius, stage) ->
            ArtLayer(
                Modifier.matchParentSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val top = size.height - panelH - fadeLen.toPx()
                        val start = top + (size.height - top) * stage * 0.6f
                        val end = start + fadeLen.toPx()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = start, endY = end),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                    .blur(radius, BlurredEdgeTreatment.Rectangle),
            )
        }
        // Velo negro en degradado: transparente arriba, negro abajo, para que el texto se lea.
        Box(
            Modifier.matchParentSize().drawBehind {
                val top = size.height - panelH - fadeLen.toPx()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.85f),
                        startY = top, endY = size.height,
                    ),
                )
            },
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { panelH = it.height }
                .padding(start = Sp.edge + 4.dp, end = Sp.edge + 4.dp, top = Sp.edge, bottom = Sp.edge + 4.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White, maxLines = 1, modifier = Modifier.basicMarquee(),
                        )
                        Spacer(Modifier.height(Sp.lines))
                        Text(
                            m.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(Sp.group))
                    Visualizer(m.playing, Color.White, height = 22.dp, bars = 5)
                }
                Spacer(Modifier.height(Sp.group))
                MediaProgress(item, pos, Color.White, Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.75f), cb)
                Spacer(Modifier.height(Sp.group))
                MediaControls(
                    item, m.playing, cb, iconTint = Color.White,
                    playContainer = Color.White, playContent = Color.Black.copy(alpha = 0.85f),
                    sideContainer = Color.White.copy(alpha = 0.16f),
                )
            }
        }
        // Ícono de la app arriba a la izquierda, sobre una pastilla translúcida.
        Box(
            Modifier.align(Alignment.TopStart).padding(Sp.edge).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)).padding(4.dp),
        ) { AppOrKindIcon(item, p, 24.dp) }
    }
}

@Composable
private fun Avatar(item: IslandItem, p: IslandPalette, size: Dp) {
    val img = item.image
    if (img != null) {
        Image(img.asImageBitmap(), null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
            Text(
                item.title.trim().take(1).uppercase(), color = p.onAccentContainer,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/** Una línea pequeña encima del título (app, estado, hora), como "iPhone" en la guía. */
@Composable
internal fun Caption(text: String, color: Color) = Text(
    text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
)

/**
 * Banner de llamada, medidas de la guía: 76 de alto · 12 | avatar 46 | 8 | etiqueta + nombre (16) | 12 |
 * botones de 40 separados 12 | 16.
 */
@Composable
private fun CallBody(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    val round = item.actions.filter { it.style != ActionStyle.NORMAL }
    val other = item.actions.filter { it.style == ActionStyle.NORMAL }
    Row(
        Modifier.fillMaxWidth().height(Sp.banner).padding(start = Sp.leadRound, end = Sp.edge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item, p, Sp.avatar)
        Spacer(Modifier.width(Sp.iconText))
        Column(Modifier.weight(1f)) {
            val caption = if (item.incoming) item.appLabel ?: "Incoming call"
            else chronoText(item, rememberNow()) ?: item.text ?: "On call"
            Caption(caption, if (item.incoming) p.variant else SoftGreen)
            Spacer(Modifier.height(Sp.lines))
            Text(
                item.title, style = MaterialTheme.typography.titleMedium, color = p.content,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Sp.group))
        round.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.width(Sp.group))
            CallButton(a, item.incoming) { cb.onAction(item, a) }
        }
    }
    if (other.isNotEmpty()) {
        Column(Modifier.padding(start = Sp.edge, end = Sp.edge, bottom = Sp.edge)) {
            ActionRow(item, p, cb, other)
        }
    }
}

@Composable
private fun CallButton(a: IslandAction, incoming: Boolean, onClick: () -> Unit) {
    val positive = a.style == ActionStyle.POSITIVE
    val wiggle = if (positive && incoming) {
        val t = rememberInfiniteTransition(label = "wiggle")
        val r by t.animateFloat(-14f, 14f, infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse), label = "r")
        val gate by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "g")
        if (gate < 0.35f) r else 0f
    } else 0f
    FilledIconButton(
        onClick = onClick, modifier = Modifier.size(Sp.action), shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (positive) CallGreen else CallRed, contentColor = Color.White,
        ),
    ) {
        Icon(
            if (positive) Icons.Rounded.Call else Icons.Rounded.CallEnd, a.title,
            modifier = Modifier.size(22.dp).rotate(wiggle),
        )
    }
}

@Composable
private fun TimerBody(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    val now = rememberNow()
    val red = item.kind == Kind.RECORDING
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                chronoText(item, now) ?: item.compactText ?: item.title,
                style = MaterialTheme.typography.displayMedium.merge(Tabular).copy(fontWeight = FontWeight.Medium),
                color = if (red) SoftRed else p.accent, maxLines = 1,
            )
            val sub = listOfNotNull(item.title.takeIf { chronoText(item, now) != null }, item.text).joinToString(" · ")
            if (sub.isNotBlank()) Spacer(Modifier.height(Sp.lines))
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PulsingShapeIcon(
            if (red) Icons.Rounded.FiberManualRecord else Icons.Rounded.Timer,
            if (red) CallRed else p.accentContainer, if (red) Color.White else p.onAccentContainer, Sp.avatar + Sp.iconText, true,
        )
    }
    ActionRow(item, p, cb)
}

@Composable
private fun NavBody(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Sp.avatar).clip(RoundedCornerShape(14.dp)).background(p.accentContainer), contentAlignment = Alignment.Center) {
            val img = item.image
            if (img != null) Image(img.asImageBitmap(), null, Modifier.size(Sp.avatar * 0.7f))
            else Icon(Icons.Rounded.TurnRight, null, tint = p.onAccentContainer, modifier = Modifier.size(Sp.avatar * 0.62f))
        }
        Spacer(Modifier.width(Sp.group))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.text?.let { Spacer(Modifier.height(Sp.lines)); Text(it, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
    ActionRow(item, p, cb)
}

@Composable
private fun ProgressBody(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val img = item.image
        if (img != null) Image(img.asImageBitmap(), null, Modifier.size(Sp.avatar).clip(RoundedCornerShape(14.dp)))
        else AppOrKindIcon(item, p, Sp.avatar)
        Spacer(Modifier.width(Sp.group))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.text?.let { Spacer(Modifier.height(Sp.lines)); Text(it, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (item.indeterminate) { Spacer(Modifier.width(Sp.group)); LoadingIndicator(Modifier.size(Sp.action), color = p.accent) }
    }
    if (!item.indeterminate && item.progress >= 0f) {
        Spacer(Modifier.height(Sp.group))
        WavyProgress(item.progress, animate = true, color = p.accent, track = p.track)
    }
    ActionRow(item, p, cb)
}

/**
 * Mensaje como banner: 12 | avatar 46 | 8 | "App · now" encima del remitente (16) y el texto | 16.
 * Las acciones (40 de alto, separadas 12) van debajo con 16 a los lados.
 */
@Composable
private fun NotificationBody(item: IslandItem, p: IslandPalette, ui: IslandUi, cb: IslandCallbacks) {
    Row(
        Modifier.fillMaxWidth().padding(start = Sp.leadRound, end = Sp.edge, top = Sp.bannerV, bottom = Sp.bannerV),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(item, p, Sp.avatar)
        Spacer(Modifier.width(Sp.iconText))
        Column(Modifier.weight(1f).heightIn(min = Sp.avatar), verticalArrangement = Arrangement.Center) {
            Caption(listOfNotNull(item.appLabel, "now").joinToString(" · "), p.variant)
            Spacer(Modifier.height(Sp.lines))
            Text(
                item.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            item.text?.let {
                Spacer(Modifier.height(Sp.lines))
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = p.variant,
                    maxLines = if (ui.expanded) 10 else 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val reply = item.actions.firstOrNull { it.onReply != null }
    Column(Modifier.padding(start = Sp.edge, end = Sp.edge, bottom = if (item.actions.isEmpty()) 0.dp else Sp.edge)) {
        if (ui.replyingId == item.id && reply != null) {
            ReplyField(p, onSend = { cb.onReplySend(item, reply, it) }, onCancel = { cb.onReplyCancel() })
        } else if (item.actions.isNotEmpty()) {
            // El banner ya deja 15 debajo del texto: la fila va sin espacio extra arriba.
            ActionRowTight(item, p, cb)
        }
    }
}

/** Fila de acciones sin el espacio superior (cuando el bloque de arriba ya trae su padding). */
@Composable
private fun ActionRowTight(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sp.group)) {
        item.actions.take(3).forEach { a ->
            FilledTonalButton(
                onClick = { if (a.onReply != null) cb.onReplyStart(item) else cb.onAction(item, a) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = p.accentContainer, contentColor = p.onAccentContainer),
                contentPadding = PaddingValues(horizontal = Sp.group, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(Sp.action),
            ) {
                if (a.onReply != null) {
                    Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Sp.iconText / 2))
                }
                Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ReplyField(p: IslandPalette, onSend: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(180)
        runCatching { fr.requestFocus() }
        kb?.show()
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.accentContainer.copy(alpha = 0.55f))
            .padding(start = Sp.edge, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.weight(1f).focusRequester(fr).padding(vertical = Sp.group),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.content),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text) }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Write a reply…", color = p.variant, style = MaterialTheme.typography.bodyLarge)
                    inner()
                }
            },
        )
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel", tint = p.variant) }
        FilledIconButton(
            onClick = { if (text.isNotBlank()) onSend(text) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = p.accent, contentColor = Color.Black),
        ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send") }
    }
}

/** Para usar el color en sitios que piden ARGB. */
fun Color.argb(): Int = toArgb()
