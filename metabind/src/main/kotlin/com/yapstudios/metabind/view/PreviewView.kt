package com.yapstudios.metabind.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yapstudios.bindjs.JsRuntime
import com.yapstudios.bindjs.composables.BindJSView
import com.yapstudios.bindjs.composables.UiEvent
import com.yapstudios.bindjs.model.Component
import com.yapstudios.metabind.view.PreviewViewModel.UiState.Error
import com.yapstudios.metabind.view.PreviewViewModel.UiState.Loading
import com.yapstudios.metabind.view.PreviewViewModel.UiState.Success

@Composable
fun PreviewView(
    contentId: String,
    viewModel: PreviewViewModel = viewModel(),
) {
    val jsRuntime = remember { viewModel.jsRuntime }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(contentId) {
        viewModel.loadContent(contentId)
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
                jsRuntime = jsRuntime,
                component = state.component,
                version = state.componentVersion,
                onUiEvent = viewModel::onUiEvent,
            )

            is Error -> ErrorState()
        }
    }
}

@Composable
private fun ComponentState(
    jsRuntime: JsRuntime,
    component: Component,
    version: Int,
    onUiEvent: (UiEvent) -> Unit,
) {
    BindJSView(
        jsRuntime = jsRuntime,
        component = component,
        version = version,
        onUiEvent = onUiEvent
    )
}

@Composable
private fun ErrorState() {
    Text(
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
