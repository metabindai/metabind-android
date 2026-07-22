package ai.metabind.ui.navigation

/**
 * Describes a desired navigation operation.
 */
interface NavigationCommand {
    val route: String
    val destination: String
    val popTo: Screens?
    val authScreen: Boolean
}
