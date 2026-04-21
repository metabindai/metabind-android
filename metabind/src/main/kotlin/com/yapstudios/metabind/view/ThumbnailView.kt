/*
 * ThumbnailView.kt.
 *
 * © 2026 Yap Studios LLC
 */
package com.yapstudios.metabind.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yapstudios.metabind.view.ThumbnailViewModel.UiState.Error
import com.yapstudios.metabind.view.ThumbnailViewModel.UiState.Loading
import com.yapstudios.metabind.view.ThumbnailViewModel.UiState.Success

@Composable
fun ThumbnailView(
    contentId: String,
    viewModel: ThumbnailViewModel = viewModel(
        key = contentId,
        factory = ThumbnailViewModel.factory(LocalContext.current)
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(contentId) {
        viewModel.loadContent(contentId, context)
    }

    Box(
        modifier = Modifier
            .size(150.dp)
            .border(
                BorderStroke(1.dp, Color.LightGray),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
    ) {
        when (val state = uiState) {
            is Loading -> LoadingState()
            is Success -> ComponentState(
                bitmap = state.bitmap,
                isContent = state.isContent,
            )

            is Error -> ErrorState()
        }
    }
}

@Composable
private fun ComponentState(
    bitmap: ImageBitmap,
    isContent: Boolean,
) {
    Image(
        bitmap = bitmap,
        contentDescription = "",
        contentScale = ContentScale.Crop,
        alignment = if (isContent) Alignment.TopCenter else Alignment.Center
    )
}

@Composable
private fun BoxScope.ErrorState() {
    Text(
        modifier = Modifier
            .align(Alignment.Center),
        textAlign = TextAlign.Center,
        text = "Error loading component.",
        style = MaterialTheme.typography.labelSmall.copy(color = Color.Red)
    )
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(
        modifier = Modifier
            .width(32.dp)
            .align(Alignment.Center),
    )
}
