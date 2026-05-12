@file:OptIn(ExperimentalPermissionsApi::class)

package ai.metabind.feature.recents.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import ai.metabind.feature.home.R
import ai.metabind.metabind.view.ThumbnailView

@Composable
fun RecentsScreen(
    viewModel: RecentsViewModel,
) {
    val viewState = viewModel.viewState.collectAsState().value

    RecentsContent(
        viewState = viewState,
        onRemove = viewModel::onRemove,
        onItemClicked = viewModel::onItemClicked,
        onScanClicked = viewModel::onScanClicked,
    )

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
}

@Composable
fun RecentsContent(
    viewState: RecentsViewModel.ViewState,
    onRemove: (Long) -> Unit,
    onItemClicked: (Long) -> Unit,
    onScanClicked: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .systemBarsPadding()
                .padding(horizontal = 32.dp)
        ) {
            Text("Recents", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W600))
            viewState.recents?.let { recents ->
                if (recents.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .padding(all = 24.dp),
                    ) {
                        items(items = viewState.recents, key = { it.id }) { itemState ->
                            RecentItem(
                                itemState = itemState,
                                onRemove = { onRemove(itemState.id) },
                                onItemClicked = { onItemClicked(itemState.id) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 16.dp), color = Color.LightGray
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxWidth(),
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = "No recents found",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
            if (viewState.recents == null) {
                Box(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxWidth(),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(64.dp)
                            .align(Alignment.Center),
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onScanClicked,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
                .size(56.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.qr_code_24px),
                contentDescription = "Preview",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun RecentItem(
    itemState: RecentsViewModel.RecentItemViewState,
    onRemove: (Long) -> Unit,
    onItemClicked: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        confirmValueChange = { dismissedValue ->
            if (dismissedValue != SwipeToDismissBoxValue.Settled) {
                onRemove(itemState.id)
                true
            } else {
                false
            }
        })
    SwipeToDismissBox(
        state = swipeToDismissBoxState, modifier = modifier.fillMaxWidth(), backgroundContent = {
            if (swipeToDismissBoxState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove item",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            lerp(Color.LightGray, Color.Red, swipeToDismissBoxState.progress)
                        )
                        .wrapContentSize(Alignment.CenterEnd)
                        .padding(12.dp),
                    tint = Color.White
                )
            }
        }) {
        RecentItemView(
            itemId = itemState.id,
            contentId = itemState.token,
            name = itemState.name ?: "Unknown",
            onItemClicked = onItemClicked,
        )
    }
}

@Composable
private fun RecentItemView(
    itemId: Long,
    contentId: String,
    name: String,
    onItemClicked: (Long) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 200.dp)
            .background(Color.White)
            .clickable(onClick = { onItemClicked(itemId) })
    ) {
        Row(modifier = Modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .border(
                        BorderStroke(1.dp, Color.LightGray),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                ThumbnailView(contentId)
            }
            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Component",
                    style = MaterialTheme.typography.labelLarge.copy(color = Color.LightGray)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600))
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
