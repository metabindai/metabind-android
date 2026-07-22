package ai.metabind.feature.ext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@Composable
fun String?.mapAsTextStyle(): TextStyle = when (this) {
    "h2" -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W600)
    "titleHeader" -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W600)
    "body" -> MaterialTheme.typography.bodyLarge
    else -> MaterialTheme.typography.labelMedium
}

@Composable
fun String?.mapAsTextAlign(): TextAlign = when (this) {
    "right" -> TextAlign.End
    "center" -> TextAlign.Center
    else -> TextAlign.Start
}
