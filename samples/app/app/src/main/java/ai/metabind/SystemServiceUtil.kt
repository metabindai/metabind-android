package ai.metabind

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

object SystemServiceUtil {
    fun isTalkBackEnabled(context: Context): Boolean {
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        if (accessibilityManager.isEnabled) {
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_SPOKEN
            )

            for (service in enabledServices) {
                if (service.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback") {
                    return true
                }
            }
        }
        return false
    }
}
