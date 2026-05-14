/*
 * KeyEntryScreen.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.assistant.demo.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun KeyEntryScreen(
    onNavigateToHome: () -> Unit = {},
    viewModel: KeyEntryViewModel = hiltViewModel()
) {
    var apiKey by remember { mutableStateOf("") }

    val submit = {
        if (apiKey.isNotBlank()) {
            viewModel.setApiKey(apiKey)
            onNavigateToHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimensionResource(R.dimen.space4x)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.key_entry_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space1_5x)))

        Text(
            text = stringResource(R.string.key_entry_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space2x)))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { newValue ->
                if (newValue.endsWith("\n")) {
                    submit()
                } else {
                    apiKey = newValue
                }
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.key_entry_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space2x)))

        Button(
            onClick = { submit() },
            enabled = apiKey.isNotBlank()
        ) {
            Text(stringResource(R.string.key_entry_start))
        }
    }
}
