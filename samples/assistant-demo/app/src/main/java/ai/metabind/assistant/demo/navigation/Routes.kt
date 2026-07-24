/*
 * Routes.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object KeyEntryRoute : NavKey
