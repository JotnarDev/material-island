package dev.jotnar.materialisland.core

import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.model.IslandItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que la isla debe pintar en este momento. */
data class IslandState(
    /** Actividades en curso ordenadas: la primera ocupa la isla. */
    val live: List<IslandItem> = emptyList(),
    /** Aviso transitorio que se muestra encima de todo, o null. */
    val transient: IslandItem? = null,
) {
    val primary: IslandItem? get() = transient ?: live.firstOrNull()
    val secondary: IslandItem? get() = if (transient != null) null else live.getOrNull(1)
}

/**
 * Fuente única de verdad: notificaciones, música, eventos del sistema y la demo escriben aquí; la isla
 * (y la vista previa de la app) leen [state].
 */
object IslandStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val liveItems = MutableStateFlow<Map<String, IslandItem>>(emptyMap())
    private val queue = MutableStateFlow<List<IslandItem>>(emptyList())
    private val preferredId = MutableStateFlow<String?>(null)

    /** App en primer plano (la informa el servicio de accesibilidad). */
    val foregroundPkg = MutableStateFlow<String?>(null)

    private var transientJob: Job? = null
    private var holdTransient = false

    val state: StateFlow<IslandState> = combine(
        liveItems, queue, preferredId, foregroundPkg, SettingsStore.flow,
    ) { live, q, preferred, fg, settings ->
        val visible = live.values.filter { item ->
            !(settings.hideMediaWhenAppOpen && item.kind == dev.jotnar.materialisland.model.Kind.MEDIA &&
                item.pkg != null && item.pkg == fg)
        }
        val sorted = visible.sortedWith(
            compareByDescending<IslandItem> { it.id == preferred }
                .thenByDescending { it.kind.priority }
                .thenByDescending { it.postedAt },
        )
        IslandState(sorted, q.firstOrNull())
    }.stateIn(scope, SharingStarted.Eagerly, IslandState())

    // ── Actividades en curso ────────────────────────────────────────────

    fun upsert(item: IslandItem) {
        val old = liveItems.value[item.id]
        // Conserva la hora original para que actualizar una notificación no la reordene.
        val merged = if (old != null) item.copy(postedAt = old.postedAt) else item
        liveItems.value = liveItems.value + (item.id to merged)
        if (old == null && item.autoExpand) autoExpandRequests.tryEmit(item.id)
    }

    fun remove(id: String) {
        if (liveItems.value.containsKey(id)) liveItems.value = liveItems.value - id
        if (preferredId.value == id) preferredId.value = null
        // Un aviso transitorio de esa misma notificación también desaparece.
        if (queue.value.any { it.id == id }) dismissTransient(id)
    }

    fun get(id: String): IslandItem? = liveItems.value[id] ?: queue.value.firstOrNull { it.id == id }

    /** Pasa la segunda actividad a la isla (tocar la burbuja o deslizar de lado). */
    fun cycle() {
        val live = state.value.live
        if (live.size < 2) return
        preferredId.value = live[1].id
    }

    // ── Avisos transitorios ─────────────────────────────────────────────

    fun show(item: IslandItem) {
        val q = queue.value
        val idx = q.indexOfFirst { it.id == item.id }
        queue.value = when {
            idx == 0 -> listOf(item) + q.drop(1) // actualiza el que se ve
            idx > 0 -> q.toMutableList().also { it[idx] = item }
            else -> q + item
        }
        if (idx == 0 || q.isEmpty()) scheduleTransient()
    }

    /** Mantiene el aviso actual en pantalla (el usuario lo está tocando o respondiendo). */
    fun hold(hold: Boolean) {
        holdTransient = hold
        if (!hold) scheduleTransient()
        else transientJob?.cancel()
    }

    fun dismissTransient(id: String? = null) {
        val q = queue.value
        if (q.isEmpty()) return
        queue.value = if (id == null) q.drop(1) else q.filterNot { it.id == id }
        holdTransient = false
        scheduleTransient()
    }

    private fun scheduleTransient() {
        transientJob?.cancel()
        val current = queue.value.firstOrNull() ?: return
        if (holdTransient) return
        transientJob = scope.launch {
            delay(current.durationMs)
            if (queue.value.firstOrNull()?.id == current.id && !holdTransient) {
                queue.value = queue.value.drop(1)
                scheduleTransient()
            }
        }
    }

    /** Ids de actividades que piden abrirse expandidas (llamada entrante). */
    val autoExpandRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun clearAll() {
        liveItems.value = emptyMap()
        queue.value = emptyList()
        preferredId.value = null
    }

    fun clearWhere(predicate: (IslandItem) -> Boolean) {
        liveItems.value = liveItems.value.filterValues { !predicate(it) }
        queue.value = queue.value.filterNot(predicate)
    }
}
