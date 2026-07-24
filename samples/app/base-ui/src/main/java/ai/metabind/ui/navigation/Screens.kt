package ai.metabind.ui.navigation

/**
 * The collection of all Compose screens used in the app, defining a unique route to each one.
 *
 * Add an object if the navigation will always be the same, or a class if you need different
 * behavior in different situations.
 *
 * When adding a screen here, you will need to also add it to ComposeNavScreen.
 *
 * Note that each route must be a unique string.
 */
sealed class Screens(
    override val route: String,
    override val authScreen: Boolean = false,
    override val popTo: Screens? = null,
) : NavigationCommand {

    override val destination: String
        get() = route

    // Navigation graphs -- these work as popTo values

    class MainGraph(override val popTo: Screens? = null) : Screens(
        route = "mainGraph",
        popTo = popTo
    )

    // Destination screens -- these do not work as popTo values

    object Back : Screens(
        route = "back"
    )

    /**
     * This is a special direction used to indicate that no particular navigation is needed. Used
     * as the initial state of navigation and when clearing the nav state.
     */
    object Default : Screens(
        route = "",
    )

    object ScanLink : Screens(
        route = "scanLink",
    )

    object Recents : Screens(
        route = "recents",
    )

    object RecentsPop : Screens(
        route = "recents",
        popTo = Recents
    )

    data class Detail(val itemId: Long) : Screens(
        route = "detail/$itemId",
    )

}
