package dev.jotnar.materialisland.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Pantalla con barra superior grande que se pliega al hacer scroll. */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = behavior,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            content = content,
        )
    }
}

/** Título de un grupo. */
@Composable
fun GroupTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp),
    )
}

/**
 * Grupo de filas al estilo de Ajustes de Android 16: tarjetas separadas por 2 dp, con esquinas grandes
 * solo arriba del todo y abajo del todo.
 */
@Composable
fun Group(vararg rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, row ->
            val big = 22.dp
            val small = 5.dp
            val shape = RoundedCornerShape(
                topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
                bottomStart = if (i == rows.lastIndex) big else small, bottomEnd = if (i == rows.lastIndex) big else small,
            )
            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.fillMaxWidth()) {
                row()
            }
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector?, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    if (icon == null) return
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
    Spacer(Modifier.width(16.dp))
}

@Composable
fun NavRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
            } else null,
        )
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    steps: Int = 0,
    subtitle: String? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                format(value), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Fila que abre un diálogo para elegir una opción. */
@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, icon: ImageVector? = null, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(label(selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .selectable(o == selected, role = Role.RadioButton) { onSelect(o); open = false }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label(o), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

/** Botones segmentados para 2-3 opciones. */
@Composable
fun <T> SegmentedRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = o == selected, onClick = { onSelect(o) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label(o), maxLines = 1) }
            }
        }
    }
}

val SwatchColors = listOf(
    0xFF1C1B1FL, 0xFF0B1D3A, 0xFF1F2A1E, 0xFF2D1B2E, 0xFF3A1F14,
    0xFFA8C7FAL, 0xFF7FCFFFL, 0xFF6DD58CL, 0xFFFFB4ABL, 0xFFFFD267L, 0xFFE8B4FFL, 0xFFFF8A65L, 0xFFFFFFFFL,
).map { it.toLong() }

@Composable
fun SwatchRow(title: String, colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { c ->
                val sel = c == selected
                Box(
                    Modifier.size(40.dp).clip(if (sel) RoundedCornerShape(12.dp) else CircleShape)
                        .background(Color(c.toInt()))
                        .border(2.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, if (sel) RoundedCornerShape(12.dp) else CircleShape)
                        .clickable { onSelect(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) Icon(Icons.Rounded.Check, null, tint = if (c == 0xFFFFFFFFL) Color.Black else Color.White)
                }
            }
        }
    }
}
