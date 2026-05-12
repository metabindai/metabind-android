package ai.metabind.ui.delegates

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber

interface AnalyticsDelegate {
    fun register(lifecycle: Lifecycle)
}

class AnalyticsDelegateImpl(
    private val navigationName: String
) : AnalyticsDelegate,
    DefaultLifecycleObserver {

    override fun register(lifecycle: Lifecycle) {
        lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Timber.i("Resuming $navigationName")
    }

}
