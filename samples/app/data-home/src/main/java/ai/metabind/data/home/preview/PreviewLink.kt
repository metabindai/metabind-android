package ai.metabind.data.home.preview

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A project link identifies a version; it grants no access. */
class MCPPreviewLink private constructor(
    val organizationId: String,
    val projectId: String,
    val isDevelopment: Boolean,
    val name: String?,
    val isDraft: Boolean,
) {
    val mcpHost get() = if (isDevelopment) "https://mcp-dev.metabind.ai" else "https://mcp.metabind.ai"
    val serverUrl get() = "$mcpHost/$organizationId/projects/$projectId" + if (isDraft) "/draft" else ""
    val previewUrl get() = "https://${if (isDevelopment) "dev" else "www"}.metabind.ai/preview/mcp?url=${encode(serverUrl)}" + (name?.let { "&name=${encode(it)}" } ?: "")
    val title get() = name ?: "MCP Project"

    // Intentionally omit the credential from diagnostics.
    override fun toString() = "MCPPreviewLink($serverUrl)"

    companion object {
        private val webHosts = setOf("www.metabind.ai", "metabind.ai", "dev.metabind.ai")
        private val mcpHosts = setOf("mcp.metabind.ai", "mcp-dev.metabind.ai")
        private val id = Regex("(?:[0-9A-Za-z]{20}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})")
        val credentialPattern = Regex("[0-9a-fA-F]{64}")

        fun parse(input: String): MCPPreviewLink? = guarded {
            val uri = URI(input.trim())
            val wrapper = uri.host?.lowercase() in webHosts && uri.rawPath == "/preview/mcp"
            if (!wrapper && uri.host?.lowercase() !in mcpHosts) return@guarded null
            requireSecure(uri)
            val query = parameters(uri.rawQuery)
            require(query.keys.none { it in setOf("key", "apiKey", "token") })
            val fragment = parameters(uri.rawFragment)
            val endpoint = if (wrapper) URI(requireNotNull(query["url"])) else uri
            requireSecure(endpoint)
            require(endpoint.host.lowercase() in mcpHosts && endpoint.rawQuery == null)
            require(!wrapper || endpoint.rawFragment == null)
            val parts = endpoint.rawPath.split('/')
            require(parts.size in 4..5 && parts[0].isEmpty() && parts[2] == "projects")
            require(parts.size == 4 || parts[4] == "draft")
            require(id.matches(parts[1]) && id.matches(parts[3]))
            val key = fragment["key"]
            require(key == null || credentialPattern.matches(key))
            MCPPreviewLink(parts[1], parts[3], endpoint.host.equals("mcp-dev.metabind.ai", true),
                (fragment["name"] ?: query["name"])?.trim()?.take(120)?.ifEmpty { null }, parts.size == 5)
        }

        fun contentUrl(input: String): String = guarded {
            val uri = URI(input.trim())
            requireSecure(uri)
            require(uri.host.lowercase() in webHosts && uri.rawFragment == null)
            val parts = uri.rawPath.split('/')
            require(parts.size == 3 && parts[1] == "preview" && parts[2] != "mcp" && Regex("[A-Za-z0-9_-]+").matches(parts[2]))
            "https://${uri.host.lowercase()}${uri.rawPath}"
        }

        private fun requireSecure(uri: URI) {
            require(uri.scheme.equals("https", true) && uri.host != null && uri.rawUserInfo == null && uri.port == -1)
        }

        private fun parameters(value: String?): Map<String, String> {
            if (value.isNullOrEmpty()) return emptyMap()
            val pairs = value.split('&').map {
                val split = it.split('=', limit = 2)
                decode(split[0]) to decode(split.getOrElse(1) { "" })
            }
            require(pairs.map { it.first }.distinct().size == pairs.size)
            return pairs.toMap()
        }

        private fun decode(value: String) = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        private inline fun <T> guarded(block: () -> T): T = try { block() } catch (_: Exception) {
            // URI exceptions contain their input, which can include a key.
            throw IllegalArgumentException("Open a valid Metabind content or MCP project preview link.")
        }
    }
}
