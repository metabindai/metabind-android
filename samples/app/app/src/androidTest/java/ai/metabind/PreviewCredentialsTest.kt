package ai.metabind

import ai.metabind.data.home.preview.MCPPreviewLink
import ai.metabind.data.home.preview.PreviewCredentials
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PreviewCredentialsTest {
    @Test fun encryptedAccessSurvivesStoreRecreationAndIsScopedToEnvironment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val link = MCPPreviewLink.parse("https://mcp.metabind.ai/00000000000000000000/projects/11111111111111111111")!!
        val dev = MCPPreviewLink.parse("https://mcp-dev.metabind.ai/00000000000000000000/projects/11111111111111111111")!!
        val store = PreviewCredentials(context)
        val key = """{"accessToken":"test-only-token","refreshToken":"test-only-refresh"}"""
        try {
            store.save(link, key)
            assertEquals(key, PreviewCredentials(context).load(link))
            assertNull(store.load(dev))
            val id = MessageDigest.getInstance("SHA-256").digest(link.serverUrl.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(context.noBackupFilesDir, "mcp-oauth/$id")
            assertTrue(file.exists())
            assertFalse(String(file.readBytes()).contains(key))
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertTrue(runCatching { store.load(link) }.isFailure)
        } finally {
            store.remove(link)
            assertNull(store.load(link))
        }
    }
}
