package ai.metabind.ui.app

import androidx.lifecycle.ViewModel
import ai.metabind.ui.navigation.NavigationConductor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class AppViewModel @Inject constructor(
    private val navigationConductor: NavigationConductor,
) : ViewModel() {
}
