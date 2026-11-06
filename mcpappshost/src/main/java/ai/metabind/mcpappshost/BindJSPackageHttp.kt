/*
 * BindJSPackageHttp.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.mcpappshost

import android.content.Context
import androidx.startup.Initializer
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

/**
 * The HTTP transport for BindJS packages fetched from their `contentUrl`.
 *
 * Packages go through an OkHttp disk cache, so the server's `Cache-Control`, expiry and
 * validators decide when a package is downloaded again; an immutable package survives
 * app restarts without touching the network. It is an evictable cache, not offline
 * storage. The client is separate from the MCP client, so no MCP authorization, session
 * header or cookie ever reaches the CDN, and it does not follow a redirect from HTTPS
 * to HTTP.
 */
object BindJSPackageHttp {
    const val DISK_CACHE_BYTES = 128L * 1024 * 1024
    const val CACHE_DIRECTORY = "bindjs-packages"

    @Volatile private var directory: File? = null

    /**
     * Where the disk cache lives. [BindJSPackageCacheInitializer] calls this at startup
     * with `cacheDir/bindjs-packages`; it only takes effect before [client] is first used.
     */
    fun install(directory: File) {
        this.directory = directory
    }

    /** Shared by every [BindJSViewLoader] that isn't given its own client. */
    val client: OkHttpClient by lazy { newClient(directory) }

    /** A package client like [client], caching in [cacheDirectory], or not at all when null. */
    fun newClient(cacheDirectory: File?, builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
        builder
            .followSslRedirects(false)
            .apply { cacheDirectory?.let { cache(Cache(it, DISK_CACHE_BYTES)) } }
            .build()
}

/** Points [BindJSPackageHttp] at the app's cache directory, through AndroidX App Startup. */
class BindJSPackageCacheInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        BindJSPackageHttp.install(File(context.cacheDir, BindJSPackageHttp.CACHE_DIRECTORY))
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
