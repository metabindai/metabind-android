/*
 * BindJSViewLoader.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.mcpappshost

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

/**
 * A package document (bindjs/spec/11-packages.md), decoded once and held by digest.
 * [dependencies] are references still to be resolved, each with its own digest.
 */
data class BindJSPackage(
    val name: String,
    val version: String,
    val components: Map<String, String>,
    val entry: String? = null,
    val dependencies: List<Dependency> = emptyList(),
) {
    data class Dependency(val uri: String, val sha256: String?)
}

/**
 * A View read on the BindJS View channel: its entry [component] and every component
 * it can run, dependencies' first, in the order they must be registered.
 */
data class BindJSView(
    val component: String,
    val components: Map<String, String>,
)

/** A View that must not be executed: unsupported version, missing component, failed integrity check. */
class BindJSViewException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decoded packages keyed by SHA-256, so verified bytes are decoded once. Entries were
 * verified before they went in. A package with a `contentUrl` is still fetched through
 * [BindJSPackageHttp] on every load, so HTTP freshness decides whether it is current;
 * this cache only saves decoding it. One read over MCP has no such rules, so for it the
 * digest the server states on a View is enough to skip the read. Shared by every loader
 * in the process unless one is given its own.
 */
class BindJSPackageCache(private val capacity: Int = 32) {
    private val entries = object : LinkedHashMap<String, BindJSPackage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BindJSPackage>) = size > capacity
    }
    private val locks = mutableMapOf<String, Mutex>()

    operator fun get(sha256: String): BindJSPackage? = synchronized(entries) { entries[sha256.lowercase()] }

    internal operator fun set(sha256: String, pkg: BindJSPackage) {
        synchronized(entries) { entries[sha256.lowercase()] = pkg }
    }

    internal fun remove(sha256: String) {
        synchronized(entries) { entries.remove(sha256.lowercase()) }
    }

    /** Drops every decoded package. The HTTP disk cache is left as it is. */
    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    /** One load per digest at a time: two Views opening on the same package fetch it once. */
    internal suspend fun <T> withLock(sha256: String, block: suspend () -> T): T {
        val lock = synchronized(locks) { locks.getOrPut(sha256.lowercase()) { Mutex() } }
        return lock.withLock { block() }
    }

    companion object {
        val shared = BindJSPackageCache()
    }
}

/**
 * Resolves `application/bindjs+json` View documents (bindjs/bindings/mcp-apps.md) into
 * the components to run. The package comes from its `contentUrl` (found through
 * `resources/list`) through the HTTP cache, else from `resources/read`. Bytes are
 * checked against `sha256` and `size` before they are decoded or a decoded copy is
 * used; nothing that fails the check is executed or cached.
 */
class BindJSViewLoader(
    private val client: MCPAppsClient,
    private val cache: BindJSPackageCache = BindJSPackageCache.shared,
    private val http: OkHttpClient = BindJSPackageHttp.client,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listMutex = Mutex()
    private var contentUrls: Map<String, String>? = null

    companion object {
        private const val TAG = "BindJSViewLoader"
        private const val SUPPORTED_MAJOR = 1

        fun isView(mimeType: String) =
            mimeType.substringBefore(';').trim().equals(MCPAppsClient.BINDJS_VIEW_MIME_TYPE, ignoreCase = true)

        /** `_meta.ui.bindjs` of a View or package resource. */
        fun bindjsMeta(meta: JsonObject?): JsonObject? =
            (meta?.get("ui") as? JsonObject)?.get("bindjs") as? JsonObject
    }

    suspend fun load(view: ResourceContent): BindJSView {
        val text = view.text ?: throw BindJSViewException("Empty BindJS View ${view.uri}")
        val document = json.parseToJsonElement(text).jsonObject
        val meta = bindjsMeta(view.meta)

        requireSupported(meta.string("spec") ?: document.string("spec"), "View ${view.uri}")
        val component = document.string("component") ?: meta.string("component")
            ?: throw BindJSViewException("View ${view.uri} names no component")

        val inline = document["package"] as? JsonObject
        val reference = meta.string("package")
        val root = when {
            inline != null -> {
                if (reference != null) Log.w(TAG, "View ${view.uri} carries its package inline and references $reference; using the inline one")
                decode(inline)
            }
            reference != null -> fetch(reference, meta.string("sha256"), meta?.get("size")?.jsonPrimitive?.longOrNull)
            else -> throw BindJSViewException("View ${view.uri} has no package")
        }

        val components = linkedMapOf<String, String>()
        collect(root, components, mutableSetOf())
        if (component !in components) {
            throw BindJSViewException("Component '$component' is not in package ${root.name}@${root.version}")
        }
        return BindJSView(component, components)
    }

    private suspend fun collect(pkg: BindJSPackage, into: MutableMap<String, String>, resolving: MutableSet<String>) {
        val key = "${pkg.name}@${pkg.version}"
        if (!resolving.add(key)) throw BindJSViewException("Circular package dependency at $key")
        for (dependency in pkg.dependencies) {
            collect(fetch(dependency.uri, dependency.sha256, null), into, resolving)
        }
        into.putAll(pkg.components)
        resolving.remove(key)
    }

    private suspend fun fetch(uri: String, sha256: String?, size: Long?): BindJSPackage {
        // Without a digest there is nothing to verify against and nothing to cache by.
        if (sha256 == null) return decode(read(uri))
        return cache.withLock(sha256) {
            val url = contentUrl(uri)
            val fromCdn = url?.let { download(it) }?.takeIf { response ->
                verifies(response.bytes, sha256, size).also { if (!it) Log.w(TAG, "Package $uri from $url failed its integrity check; reading it over MCP") }
            }
            when {
                // The server asked that this package not be kept, decoded or otherwise.
                fromCdn?.noStore == true -> decode(fromCdn.bytes).also { cache.remove(sha256) }
                fromCdn != null -> cache[sha256] ?: decode(fromCdn.bytes).also { cache[sha256] = it }
                else -> {
                    // With no contentUrl there are no HTTP rules, and the stated digest is
                    // enough to use a held copy. A contentUrl that failed is read over MCP.
                    val bytes = if (url != null) verifiedRead(uri, sha256, size) else null
                    cache[sha256] ?: decode(bytes ?: verifiedRead(uri, sha256, size)).also { cache[sha256] = it }
                }
            }
        }
    }

    private suspend fun verifiedRead(uri: String, sha256: String, size: Long?): ByteArray =
        read(uri).also { bytes ->
            if (!verifies(bytes, sha256, size)) {
                Log.e(TAG, "Package $uri failed its integrity check; not executing it")
                throw BindJSViewException("Package $uri does not match its sha256/size")
            }
        }

    private suspend fun read(uri: String): ByteArray {
        val resource = client.readResource(uri)
        // The digest covers the `text` field's bytes as served (binding section 3).
        return resource.text?.toByteArray(Charsets.UTF_8)
            ?: resource.blob?.let { java.util.Base64.getDecoder().decode(it) }
            ?: throw BindJSViewException("Empty package resource $uri")
    }

    /** The package's `contentUrl` from `resources/list`, listed again once when [uri] is new since the last listing. */
    private suspend fun contentUrl(uri: String): String? = listMutex.withLock {
        contentUrls?.get(uri)?.let { return@withLock it }
        try {
            contentUrls = client.listResources()
                .mapNotNull { resource -> bindjsMeta(resource.meta).string("contentUrl")?.let { resource.uri to it } }
                .filter { (_, url) -> url.startsWith("https://", ignoreCase = true) }
                .toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "resources/list failed; reading packages over MCP", e)
        }
        contentUrls?.get(uri)
    }

    private class Download(val bytes: ByteArray, val noStore: Boolean)

    /** Through [http], so its cache answers when the package is fresh and revalidates it when not. */
    private suspend fun download(url: String): Download? = try {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("Accept", MCPAppsClient.BINDJS_PACKAGE_MIME_TYPE).build()
            http.newCall(request).execute().use { response ->
                // A redirect must not leave HTTPS, whatever client the loader was given.
                if (response.code != 200 || !response.request.url.isHttps) return@use null
                Download(response.body?.bytes() ?: return@use null, response.cacheControl.noStore)
            }
        }.also { if (it == null) Log.w(TAG, "Fetching $url failed; reading the package over MCP") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Fetching $url failed; reading the package over MCP", e)
        null
    }

    private fun verifies(bytes: ByteArray, sha256: String, size: Long?): Boolean {
        if (size != null && bytes.size.toLong() != size) return false
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return digest.equals(sha256, ignoreCase = true)
    }

    private fun decode(bytes: ByteArray): BindJSPackage =
        decode(json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject)

    private fun decode(document: JsonObject): BindJSPackage {
        val name = document.string("name") ?: throw BindJSViewException("Package has no name")
        val version = document.string("version") ?: throw BindJSViewException("Package $name has no version")
        requireSupported(document.string("spec"), "Package $name@$version")
        val components = (document["components"] as? JsonObject)
            ?.mapValues { (_, source) -> source.jsonPrimitive.content }
            ?: throw BindJSViewException("Package $name@$version has no components")
        val dependencies = document["dependencies"]?.jsonArray.orEmpty().map { element ->
            val dependency = element.jsonObject
            BindJSPackage.Dependency(
                uri = dependency.string("package") ?: throw BindJSViewException("Package $name@$version has a dependency with no package"),
                sha256 = dependency.string("sha256"),
            )
        }
        return BindJSPackage(name, version, components, document.string("entry"), dependencies)
    }

    /** A host must not execute a View or package whose major it does not render. A missing `spec` means 1.0. */
    private fun requireSupported(spec: String?, what: String) {
        if (spec == null) return
        val major = spec.substringBefore('.').toIntOrNull()
        if (major != SUPPORTED_MAJOR) throw BindJSViewException("$what requires BindJS $spec; this host renders $SUPPORTED_MAJOR.x")
    }

    private fun JsonObject?.string(key: String): String? = this?.get(key)?.jsonPrimitive?.contentOrNull
}
