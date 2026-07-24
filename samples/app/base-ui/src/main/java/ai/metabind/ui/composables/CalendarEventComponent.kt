package ai.metabind.ui.composables

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.metabind.ui.theme.R
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter

internal const val TEST_TAG_EVENT_COMPONENT_TITLE = "eventComponentTitleTestTag"
internal const val TEST_TAG_EVENT_COMPONENT_DATE = "eventComponentDateTestTag"
internal const val TEST_TAG_EVENT_COMPONENT_CLICK_LISTENER = "eventComponentClickListenerTestTag"

@Composable
fun CalendarEventComponent(
    title: String,
    start: LocalDateTime,
    modifier: Modifier = Modifier,
    dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy"),
    onAddToCalendarClick: (Context) -> Unit = {},
) {
    EventContainer(
        modifier = modifier,
    ) {
        EventDisplayText(
            title = title,
            start = start,
            dateTimeFormatter = dateTimeFormatter,
        )
        AddToCalendarCta(
            onAddToCalendarClick = onAddToCalendarClick,
        )
    }
}

@Composable
private fun EventDisplayText(
    title: String,
    start: LocalDateTime,
    dateTimeFormatter: DateTimeFormatter,
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        VerticalSpace()
        Icon(
            painter = painterResource(id = R.drawable.ic_event),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        HalfVerticalSpace()
        Text(
            modifier = Modifier.testTag(TEST_TAG_EVENT_COMPONENT_TITLE),
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
        )
        HalfVerticalSpace()
        Text(
            modifier = Modifier.testTag(TEST_TAG_EVENT_COMPONENT_DATE),
            text = start.format(dateTimeFormatter),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W400),
        )
        VerticalSpace()
    }
}

@Composable
private fun BoxScope.AddToCalendarCta(
    onAddToCalendarClick: (Context) -> Unit = {},
) {
    val context = LocalContext.current
    TextButton(
        modifier =
        Modifier
            .testTag(TEST_TAG_EVENT_COMPONENT_CLICK_LISTENER)
            .align(Alignment.CenterEnd),
        onClick = { onAddToCalendarClick(context) },
    ) {
        Text(
            text = stringResource(id = R.string.common_add_to_calendar),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EventContainer(
    modifier: Modifier = Modifier,
    containerBorderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable BoxScope.() -> Unit,
) {
    val roundedCornerShape = RoundedCornerShape(12.dp)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, containerBorderColor), roundedCornerShape)
            .clip(roundedCornerShape)
            .padding(horizontal = 24.dp),
    ) {
        content()
    }
}

@Preview
@Composable
fun CalendarEventComponentPreview(
    date: LocalDateTime = LocalDateTime.of(2024, Month.MARCH, 8, 14, 38),
) {
    CalendarEventComponent(
        title = "Title of Event",
        start = date,
        onAddToCalendarClick = {},
    )
}
