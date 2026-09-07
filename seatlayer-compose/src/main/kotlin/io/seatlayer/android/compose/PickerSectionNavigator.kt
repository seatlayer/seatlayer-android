package io.seatlayer.android.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.seatlayer.android.SeatLayerPickerSectionSummary
import kotlinx.coroutines.launch

@Composable
public fun SeatLayerPickerSectionNavigator(modifier: Modifier = Modifier) {
    val scope = LocalSeatLayerPickerScope.current
    val sections = scope.state.snapshot?.sections.orEmpty()
    if (sections.isEmpty()) return
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            scope.strings.sections,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        sections.forEach { section ->
            SectionRow(section) {
                coroutineScope.launch {
                    scope.performAction { scope.controller.focusSection(section.id) }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: SeatLayerPickerSectionSummary,
    onClick: () -> Unit,
) {
    val scope = LocalSeatLayerPickerScope.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        color = scope.theme.surface,
        contentColor = scope.theme.text,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(section.displayLabel ?: section.label, maxLines = 1)
                section.zoneLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = scope.theme.mutedText,
                    )
                }
            }
            section.seatsLeft?.let {
                Text(
                    scope.strings.seatsLeft(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = scope.theme.mutedText,
                )
            }
        }
    }
}
