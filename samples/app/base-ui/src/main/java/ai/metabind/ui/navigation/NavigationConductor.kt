package ai.metabind.ui.navigation

import ai.metabind.ui.app.Conductor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The class responsible for routing between screens in the app. In most cases this Conductor should
 * be triggered by a ViewModel. The Compose App will update its navigation state based on the
 * requested command.
 */
@Singleton
class NavigationConductor @Inject constructor() :
    Conductor<NavigationCommand>(Screens.Default)
