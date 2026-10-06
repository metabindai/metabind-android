package ai.metabind.data.home.preview

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** AES-GCM ciphertext excluded from backup; its key never leaves Android Keystore. */
@Singleton
class PreviewCredentials @Inject constructor(@ApplicationContext private val context: Context) {
    private val alias = "ai.metabind.app.mcp-preview"

    @Synchronized
    fun save(project: MCPPreviewLink, credential: String) = secure {
        require(credential.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(project.serverUrl.toByteArray())
        val encrypted = cipher.doFinal(credential.toByteArray())
        val file = file(project)
        val output = file.startWrite()
        try {
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            output.write(encrypted)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    @Synchronized
    fun load(project: MCPPreviewLink): String? = secure {
        val file = file(project)
        if (!file.baseFile.exists()) return@secure null
        val bytes = file.readFully()
        val size = bytes.first().toInt()
        require(size == 12 && bytes.size > size + 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, size + 1)))
        cipher.updateAAD(project.serverUrl.toByteArray())
        String(cipher.doFinal(bytes.copyOfRange(size + 1, bytes.size)))
    }

    @Synchronized
    fun remove(project: MCPPreviewLink) { file(project).delete() }

    private fun file(project: MCPPreviewLink): AtomicFile {
        // Never interpret a legacy QR API key as an OAuth session.
        val directory = File(context.noBackupFilesDir, "mcp-oauth").apply { mkdirs() }
        val id = MessageDigest.getInstance("SHA-256").digest(project.serverUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(context.noBackupFilesDir, "mcp-preview/$id").delete()
        return AtomicFile(File(directory, id))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private inline fun <T> secure(block: () -> T): T = try { block() } catch (_: Exception) {
        throw IllegalStateException("Preview access could not be stored securely. Try importing the link again.")
    }
}
