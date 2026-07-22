package ai.metabind.ui.navigation

import androidx.navigation.NavBackStackEntry

fun NavBackStackEntry.getStringArg(key: String, defaultValue: String): String =
    arguments?.getString(key, defaultValue) ?: defaultValue

fun NavBackStackEntry.requireStringArg(key: String): String {
    val arguments = checkNotNull(arguments) {
        "Encountered null bundle when attempting to get \"$key\" String argument!"
    }

    if (!arguments.containsKey(key)) {
        throw NoSuchElementException("Bundle does not contain any \"$key\" argument!")
    }

    return arguments.getString(key)
        ?: throw NullPointerException("\"$key\" String argument in bundle is null!")
}