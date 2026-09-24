package ai.metabind

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in, read-only Finance smoke test. No live project credentials are committed. */
@RunWith(AndroidJUnit4::class)
class LiveMCPPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun importChatAndReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val linkFile = File(context.cacheDir, "mcp-preview-test-link")
        assumeTrue("Supply a Finance preview link in the app cache to run this live test.", linkFile.exists())
        val link = linkFile.readText().trim()
        linkFile.delete()
        compose.onNodeWithContentDescription("Preview").performClick()
        compose.onNodeWithText("Open Preview Link").assertExists()
        compose.onNodeWithText("Preview URL").performTextInput(link)
        compose.onNodeWithText("Open Preview").performClick()
        ready()
        ask("Show me my subscriptions", "Subscriptions", "Netflix")
        ask("Show me my spending breakdown", "Spending", "Transportation")
        // Activity recreation must preserve the assistant and its current conversation.
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Show me my spending breakdown").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Close Preview").performClick()
        val title = InstrumentationRegistry.getArguments().getString("projectTitle") ?: "Banking Assistant"
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        ready()
    }

    @Test fun reopenSavedPreviewAfterProcessRestart() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reopenSavedPreview") == "true")
        val title = InstrumentationRegistry.getArguments().getString("projectTitle") ?: "Banking Assistant"
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        ready()
    }

    private fun ready() {
        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Saved drafts · Production").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Message").assertExists()
    }

    private fun ask(prompt: String, cardTitle: String, dataLabel: String) {
        compose.onNodeWithText("Message").performTextInput(prompt)
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(90_000) { compose.onAllNodesWithText(cardTitle).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(90_000) { compose.onAllNodesWithText(dataLabel).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(90_000) { compose.onAllNodesWithText("Thinking...").fetchSemanticsNodes().isEmpty() }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, "preview-${cardTitle.lowercase()}.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
