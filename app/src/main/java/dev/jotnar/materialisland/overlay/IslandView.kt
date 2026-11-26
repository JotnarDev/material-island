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
)

interface IslandCallbacks {
    fun onTap(item: IslandItem?, mode: Mode) {}
    fun onLongPress(item: IslandItem?, mode: Mode) {}
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
}

fun resolveMode(ui: IslandUi): Mode {
    val p = ui.state.primary
    return when {
        ui.hidden -> Mode.HIDDEN
        p == null -> Mode.REST
        p.kind == Kind.NOTIFICATION -> Mode.PEEK
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
private data class Dims(
    val pillH: Dp, val restD: Dp, val cameraW: Dp, val wing: Dp, val wideW: Dp, val cardW: Dp,
    val icon: Dp, val edgePad: Dp,
)

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

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
    MaterialTheme(colorScheme = dynamicDarkColorScheme(ctx)) {
        IslandBody(ui, geo, s, cb, modifier)
    }
}

@Composable
private fun IslandBody(ui: IslandUi, geo: IslandGeometry, s: IslandSettings, cb: IslandCallbacks, modifier: Modifier) {
    val density = LocalDensity.current
    val primary = ui.state.primary
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
    val hasUnread = ui.unread.isNotEmpty()
    suspend fun stowBubbles() {
        if (!hasUnread) { bubblesIn.snapTo(1f); return }
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

    val secondary = ui.state.secondary
    val showSat = s.showSecondary && mode == Mode.COMPACT && secondary != null
    var lastSecondary by remember { mutableStateOf(secondary) }
    if (secondary != null) lastSecondary = secondary

    // Zona táctil: solo la isla (y la burbuja). Fuera de ella los toques pasan a la app de debajo.
    val idleTouchable = s.idleTap != IdleAction.NONE || s.idleLongPress != IdleAction.NONE || s.calibration
    LaunchedEffect(mainRect, satRect, bubblesRect, mode, showSat, bubblesTouchable, idleTouchable) {
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
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        pressed = true; cb.onPressChanged(true)
                                        tryAwaitRelease()
                                        pressed = false; cb.onPressChanged(false)
                                    },
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
                        targetState = Frame(mode, primary),
                        contentKey = { it.mode to it.item?.id },
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
                            Mode.PEEK, Mode.EXPANDED -> if (item != null) Card(item, p, dims, s, ui, cb, sizeSpec)
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
    ) { (mainM, satM, bubM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val main = mainM.firstOrNull()?.measure(loose)
        val sat = satM.firstOrNull()?.measure(loose)
        val bub = bubM.firstOrNull()?.measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val topPx = top.roundToPx()
            if (main != null) {
                val x = (cx - main.width / 2).coerceIn(0, (constraints.maxWidth - main.width).coerceAtLeast(0))
                main.place(x, topPx)
                sat?.place(x + main.width + gapPx, topPx + (pillH.roundToPx() - sat.height) / 2)
                // Burbujas a la izquierda, centradas con la cámara.
                val centerY = if (drop) topPx + main.height / 2 else (geo.holeCenterYDp + s.offsetYDp).dp.roundToPx()
                if (bub != null) {
                    // Sitio normal: a la izquierda de la isla. Guardadas: su borde derecho en el centro de la cámara.
                    val k = if (drop) bubblesIn.value else 0f
                    val camY = (geo.holeCenterYDp + s.offsetYDp).dp.roundToPx()
                    val bx = x - gapPx - bub.width
                    val by = centerY - bub.height / 2
                    bub.place(
                        (bx + (cx - bub.width - bx) * k).roundToInt(),
                        (by + (camY - bub.height / 2 - by) * k).roundToInt(),
                    )
                }
            }
        }
    }
}

private data class Frame(val mode: Mode, val item: IslandItem?)

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
        Box(Modifier.weight(1f).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
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

// ── Tarjeta expandida / aviso ─────────────────────────────────────────

@Composable
private fun Card(
    item: IslandItem, p: IslandPalette, d: Dims, s: IslandSettings, ui: IslandUi, cb: IslandCallbacks,
    sizeSpec: androidx.compose.animation.core.FiniteAnimationSpec<IntSize>,
) {
    Column(Modifier.width(d.cardW).animateContentSize(sizeSpec)) {
        CardHeader(item, p, d)
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 4.dp)) {
            when (item.kind) {
                Kind.MEDIA -> MediaBody(item, p, s, cb)
                Kind.CALL -> CallBody(item, p, cb)
                Kind.TIMER, Kind.RECORDING -> TimerBody(item, p, cb)
                Kind.NAVIGATION -> NavBody(item, p, cb)
                Kind.NOTIFICATION -> NotificationBody(item, p, ui, cb)
                else -> ProgressBody(item, p, cb)
            }
        }
    }
}

@Composable
private fun CardHeader(item: IslandItem, p: IslandPalette, d: Dims) {
    Row(
        Modifier.fillMaxWidth().height(d.pillH).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AppOrKindIcon(item, p, 20.dp)
            Spacer(Modifier.width(8.dp))
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
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.take(3).forEach { a ->
            FilledTonalButton(
                onClick = { if (a.onReply != null) cb.onReplyStart(item) else cb.onAction(item, a) },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = p.accentContainer, contentColor = p.onAccentContainer,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (a.onReply != null) Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp).padding(end = 4.dp))
                Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MediaBody(item: IslandItem, p: IslandPalette, s: IslandSettings, cb: IslandCallbacks) {
    val m = item.media ?: return
    val now by androidx.compose.runtime.produceState(android.os.SystemClock.elapsedRealtime(), m.playing) {
        while (m.playing) {
            value = android.os.SystemClock.elapsedRealtime(); delay(250)
        }
    }
    val pos = m.currentPosition(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        MorphingArt(m.art, m.playing, 76.dp, s.morphingArt, p.accentContainer)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.content, maxLines = 1, modifier = Modifier.basicMarquee(),
            )
            Text(m.artist, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(10.dp))
    if (m.durationMs > 0) {
        WavyProgress(
            progress = pos.toFloat() / m.durationMs, animate = m.playing, color = p.accent, track = p.track,
            onSeek = { f -> cb.onSeek(item, f) },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(pos), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = p.variant)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(m.durationMs), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = p.variant)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { cb.onMedia(item, MediaCmd.PREV) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.content, modifier = Modifier.size(32.dp))
        }
        // Botón play/pausa que cambia de forma: píldora en pausa, cuadrado redondeado sonando (como el Pixel).
        val radius by animateDpAsState(if (m.playing) 20.dp else 32.dp, spring(0.5f, 400f), label = "pp")
        val width by animateDpAsState(if (m.playing) 76.dp else 92.dp, spring(0.55f, 400f), label = "ppw")
        Box(
            Modifier
                .width(width).height(60.dp)
                .clip(RoundedCornerShape(radius))
                .background(p.accent)
                .pointerInput(Unit) { detectTapGestures { cb.onMedia(item, MediaCmd.PLAY_PAUSE) } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause",
                tint = Color.Black.copy(alpha = 0.85f), modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { cb.onMedia(item, MediaCmd.NEXT) }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = p.content, modifier = Modifier.size(32.dp))
        }
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

@Composable
private fun CallBody(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(item, p, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.text ?: "", style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(18.dp))
    val round = item.actions.filter { it.style != ActionStyle.NORMAL }
    val other = item.actions.filter { it.style == ActionStyle.NORMAL }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        other.take(2).forEach { a ->
            FilledTonalButton(
                onClick = { cb.onAction(item, a) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = p.accentContainer, contentColor = p.onAccentContainer),
            ) { Text(a.title, maxLines = 1) }
        }
        round.forEach { a -> CallButton(a, item.incoming) { cb.onAction(item, a) } }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick, modifier = Modifier.size(width = if (positive) 72.dp else 64.dp, height = 64.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (positive) CallGreen else CallRed, contentColor = Color.White,
            ),
        ) {
            Icon(
                if (positive) Icons.Rounded.Call else Icons.Rounded.CallEnd, a.title,
                modifier = Modifier.size(28.dp).rotate(wiggle),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(a.title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
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
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PulsingShapeIcon(
            if (red) Icons.Rounded.FiberManualRecord else Icons.Rounded.Timer,
            if (red) CallRed else p.accentContainer, if (red) Color.White else p.onAccentContainer, 56.dp, true,
        )
    }
    ActionRow(item, p, cb)
}

@Composable
private fun NavBody(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(p.accentContainer), contentAlignment = Alignment.Center) {
            val img = item.image
            if (img != null) Image(img.asImageBitmap(), null, Modifier.size(44.dp))
            else Icon(Icons.Rounded.TurnRight, null, tint = p.onAccentContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, color = p.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
    ActionRow(item, p, cb)
}

@Composable
private fun ProgressBody(item: IslandItem, p: IslandPalette, cb: IslandCallbacks) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val img = item.image
        if (img != null) Image(img.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)))
        else AppOrKindIcon(item, p, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (item.indeterminate) LoadingIndicator(Modifier.size(44.dp), color = p.accent)
    }
    if (!item.indeterminate && item.progress >= 0f) {
        Spacer(Modifier.height(10.dp))
        WavyProgress(item.progress, animate = true, color = p.accent, track = p.track)
    }
    ActionRow(item, p, cb)
}

@Composable
private fun NotificationBody(item: IslandItem, p: IslandPalette, ui: IslandUi, cb: IslandCallbacks) {
    Row(verticalAlignment = Alignment.Top) {
        Avatar(item, p, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            item.text?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = p.variant,
                    maxLines = if (ui.expanded) 10 else 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val reply = item.actions.firstOrNull { it.onReply != null }
    if (ui.replyingId == item.id && reply != null) {
        Spacer(Modifier.height(14.dp))
        ReplyField(p, onSend = { cb.onReplySend(item, reply, it) }, onCancel = { cb.onReplyCancel() })
    } else {
        ActionRow(item, p, cb)
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
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.weight(1f).focusRequester(fr).padding(vertical = 14.dp),
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
