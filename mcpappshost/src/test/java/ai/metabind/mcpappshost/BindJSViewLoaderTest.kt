package ai.metabind.mcpappshost

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

class BindJSViewLoaderTest {
    private val server = MockWebServer()
    private val cdn = MockWebServer()
    private val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val cdnHttp = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        .let { OkHttpClient.Builder().sslSocketFactory(it.sslSocketFactory(), it.trustManager).build() }
    private val initializeParams = CopyOnWriteArrayList<JsonObject>()
    private val methods = CopyOnWriteArrayList<String>()
    private val cdnHits = CopyOnWriteArrayList<String>()
    private val packages = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var cdnBody: ((String) -> String?)? = null
    @Volatile private var readBody: ((String) -> String?)? = null
    @Volatile private var cacheControl: String? = null
    private val conditionalHits = CopyOnWriteArrayList<String>()
    @get:Rule val folder = TemporaryFolder()
    private lateinit var client: MCPAppsClient
    private lateinit var loader: BindJSViewLoader

    private fun pkg(name: String, components: Map<String, String>, spec: String = "1.0", dependencies: String = "") =
        """{"name":"$name","version":"1.0.0","spec":"$spec","components":${JsonObject(components.mapValues { JsonPrimitive(it.value) })}$dependencies}"""

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun cdnUrl(uri: String) = cdn.url("/cdn/" + uri.removePrefix("ui://")).toString()

    private fun view(component: String, packageUri: String, spec: String = "1.0", sha256: String? = sha256(packages.getValue(packageUri))) =
        ResourceContent(
            uri = "ui://view/$component",
            mimeType = "application/bindjs+json",
            text = """{"spec":"$spec","component":"$component"}""",
            meta = buildJsonObject {
                putJsonObject("ui") {
                    putJsonObject("bindjs") {
                        put("spec", spec)
                        put("component", component)
                        put("package", packageUri)
                        sha256?.let { put("sha256", it) }
                        put("size", packages.getValue(packageUri).toByteArray().size)
                    }
                }
            }
        )

    @Before fun setup() {
        packages["ui://pkg/shop"] = pkg("com.shop.ui", mapOf("Card" to "card()", "Layout" to "layout()"))
        cdn.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false)
        cdn.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                cdnHits.add(path)
                val uri = "ui://" + path.removePrefix("/cdn/")
                val body = cdnBody?.invoke(uri) ?: packages[uri] ?: return MockResponse().setResponseCode(404)
                val etag = "\"${sha256(body)}\""
                request.getHeader("If-None-Match")?.let { conditionalHits.add(path) }
                val response = if (request.getHeader("If-None-Match") == etag) MockResponse().setResponseCode(304)
                    else MockResponse().setBody(body)
                return response.setHeader("ETag", etag).apply { cacheControl?.let { setHeader("Cache-Control", it) } }
            }
        }
        cdn.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val method = body["method"]!!.jsonPrimitive.content
                methods.add(method)
                if (method == "initialize") initializeParams.add(body["params"]!!.jsonObject)
                val result = when (method) {
                    "initialize" -> """{"protocolVersion":"2025-03-26"}"""
                    "notifications/initialized" -> return MockResponse().setResponseCode(202)
                    "resources/list" -> buildJsonObject {
                        putJsonArray("resources") {
                            packages.forEach { (uri, text) ->
                                addJsonObject {
                                    put("uri", uri)
                                    put("mimeType", "application/bindjs-package+json")
                                    putJsonObject("_meta") { putJsonObject("ui") { putJsonObject("bindjs") {
                                        put("sha256", sha256(text)); put("contentUrl", cdnUrl(uri))
                                    } } }
                                }
                            }
                        }
                    }.toString()
                    "resources/read" -> {
                        val uri = body["params"]!!.jsonObject["uri"]!!.jsonPrimitive.content
                        val text = readBody?.invoke(uri) ?: packages.getValue(uri)
                        """{"contents":[{"uri":"$uri","mimeType":"application/bindjs-package+json","text":${JsonPrimitive(text)}}]}"""
                    }
                    else -> error("Unexpected $method")
                }
                return MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"jsonrpc":"2.0","id":${body["id"]},"result":$result}""")
            }
        }
        server.start()
        client = MCPAppsClient(server.url("/mcp").toString(), maxRetries = 0)
        loader = BindJSViewLoader(client, BindJSPackageCache(), cdnHttp)
    }

    @After fun teardown() {
        server.shutdown()
        cdn.shutdown()
    }

    @Test fun loadsThePackageFromItsContentUrl() = runBlocking {
        val view = loader.load(view("Layout", "ui://pkg/shop"))
        assertEquals("Layout", view.component)
        assertEquals(mapOf("Card" to "card()", "Layout" to "layout()"), view.components)
        assertEquals(1, cdnHits.size)
        assertFalse("resources/read" in methods)
    }

    /** A package client caching on disk like [BindJSPackageHttp.client], trusting the test CDN. */
    private fun cachingHttp() = BindJSPackageHttp.newClient(folder.root.resolve("http"), cdnHttp.newBuilder())

    @Test fun aFreshPackageIsNeitherDownloadedNorDecodedAgain() = runBlocking {
        cacheControl = "public, max-age=31536000, immutable"
        val cache = BindJSPackageCache()
        val loader = BindJSViewLoader(client, cache, cachingHttp())
        loader.load(view("Layout", "ui://pkg/shop"))
        val decoded = cache[sha256(packages.getValue("ui://pkg/shop"))]
        assertNotNull(decoded)

        // A second View on the same package, and a new session sharing the cache.
        val card = loader.load(view("Card", "ui://pkg/shop"))
        BindJSViewLoader(MCPAppsClient(server.url("/mcp").toString()), cache, cachingHttp()).load(view("Layout", "ui://pkg/shop"))

        assertEquals("Card", card.component)
        assertEquals(1, cdnHits.size)
        assertSame(decoded, cache[sha256(packages.getValue("ui://pkg/shop"))])
    }

    @Test fun aFreshPackageOutlivesTheProcess() = runBlocking {
        cacheControl = "public, max-age=31536000, immutable"
        BindJSViewLoader(client, BindJSPackageCache(), cachingHttp()).load(view("Layout", "ui://pkg/shop"))
        // A restart: nothing decoded, a new client over the same disk cache.
        val view = BindJSViewLoader(MCPAppsClient(server.url("/mcp").toString()), BindJSPackageCache(), cachingHttp())
            .load(view("Layout", "ui://pkg/shop"))
        assertEquals("layout()", view.components["Layout"])
        assertEquals(1, cdnHits.size)
        assertFalse("resources/read" in methods)
    }

    @Test fun aStalePackageIsRevalidatedAndNotDecodedAgain() = runBlocking {
        cacheControl = "no-cache"
        val cache = BindJSPackageCache()
        val loader = BindJSViewLoader(client, cache, cachingHttp())
        loader.load(view("Layout", "ui://pkg/shop"))
        val decoded = cache[sha256(packages.getValue("ui://pkg/shop"))]
        loader.load(view("Card", "ui://pkg/shop"))
        assertEquals(2, cdnHits.size)
        assertEquals(1, conditionalHits.size)
        assertSame(decoded, cache[sha256(packages.getValue("ui://pkg/shop"))])
    }

    @Test fun aNoStorePackageIsNeitherKeptOnDiskNorDecodedCopyKept() = runBlocking {
        cacheControl = "no-store"
        val cache = BindJSPackageCache()
        val loader = BindJSViewLoader(client, cache, cachingHttp())
        loader.load(view("Layout", "ui://pkg/shop"))
        loader.load(view("Card", "ui://pkg/shop"))
        assertEquals(2, cdnHits.size)
        assertTrue(conditionalHits.isEmpty())
        assertNull(cache[sha256(packages.getValue("ui://pkg/shop"))])
    }

    @Test fun aHeldPackageFailingANewDigestIsReadOverMcp() = runBlocking {
        cacheControl = "public, max-age=31536000, immutable"
        val loader = BindJSViewLoader(client, BindJSPackageCache(), cachingHttp())
        loader.load(view("Layout", "ui://pkg/shop"))
        // The server moved on; the disk still holds the old package, which no longer verifies.
        packages["ui://pkg/shop"] = pkg("com.shop.ui", mapOf("Layout" to "layout2()"))
        cdnBody = { """{"name":"com.shop.ui","version":"1.0.0","spec":"1.0","components":{"Card":"card()","Layout":"layout()"}}""" }
        assertEquals("layout2()", loader.load(view("Layout", "ui://pkg/shop")).components["Layout"])
        assertTrue("resources/read" in methods)
    }

    @Test fun aRedirectOffHttpsIsNotFollowed() = runBlocking {
        val plain = MockWebServer().apply { start() }
        try {
            cdn.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    cdnHits.add(request.path!!)
                    return MockResponse().setResponseCode(302).setHeader("Location", plain.url("/package").toString())
                }
            }
            plain.enqueue(MockResponse().setBody(packages.getValue("ui://pkg/shop")))
            plain.enqueue(MockResponse().setBody(packages.getValue("ui://pkg/shop")))
            // The package client refuses the redirect; a client that follows it is caught by the loader.
            for (http in listOf(cachingHttp(), cdnHttp)) {
                methods.clear()
                assertEquals("layout()", BindJSViewLoader(client, BindJSPackageCache(), http).load(view("Layout", "ui://pkg/shop")).components["Layout"])
                assertTrue("resources/read" in methods)
            }
            assertEquals(1, plain.requestCount)
        } finally {
            plain.shutdown()
        }
    }

    @Test fun viewsOpeningTogetherFetchTheirPackageOnce() = runBlocking {
        cacheControl = "public, max-age=60"
        val loader = BindJSViewLoader(client, BindJSPackageCache(), cachingHttp())
        val first = async { loader.load(view("Layout", "ui://pkg/shop")) }
        val second = async { loader.load(view("Card", "ui://pkg/shop")) }
        first.await(); second.await()
        assertEquals(1, cdnHits.size)
    }

    @Test fun aNewDigestIsANewPackage() = runBlocking {
        loader.load(view("Layout", "ui://pkg/shop"))
        packages["ui://pkg/shop"] = pkg("com.shop.ui", mapOf("Layout" to "layout2()"))
        assertEquals("layout2()", loader.load(view("Layout", "ui://pkg/shop")).components["Layout"])
        assertEquals(2, cdnHits.size)
    }

    @Test fun tamperedCdnBytesFallBackToResourcesRead() = runBlocking {
        cdnBody = { """{"name":"evil","version":"1.0.0","components":{"Layout":"steal()"}}""" }
        assertEquals("layout()", loader.load(view("Layout", "ui://pkg/shop")).components["Layout"])
        assertTrue("resources/read" in methods)
    }

    @Test fun anUnreachableCdnFallsBackToResourcesRead() = runBlocking {
        cdn.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503)
        }
        assertEquals("layout()", loader.load(view("Layout", "ui://pkg/shop")).components["Layout"])
        assertTrue("resources/read" in methods)
    }

    @Test fun aPackageMatchingNowhereIsNotExecutedOrCached() = runBlocking {
        val cache = BindJSPackageCache()
        val loader = BindJSViewLoader(client, cache, cdnHttp)
        val wrong = "0".repeat(64)
        try {
            loader.load(view("Layout", "ui://pkg/shop", sha256 = wrong))
            fail("expected an integrity failure")
        } catch (e: BindJSViewException) {
            assertTrue(e.message!!.contains("sha256"))
        }
        assertNull(cache[wrong])
    }

    @Test fun anUnsupportedMajorIsRefusedBeforeFetching() = runBlocking {
        try {
            loader.load(view("Layout", "ui://pkg/shop", spec = "2.0"))
            fail("expected a version refusal")
        } catch (e: BindJSViewException) {
            assertTrue(e.message!!.contains("2.0"))
        }
        assertEquals(0, cdnHits.size)
        assertFalse("resources/read" in methods)
    }

    @Test fun aComponentMissingFromThePackageIsRefused() = runBlocking {
        try {
            loader.load(view("Missing", "ui://pkg/shop"))
            fail("expected a missing component")
        } catch (e: BindJSViewException) {
            assertTrue(e.message!!.contains("Missing"))
        }
    }

    @Test fun dependenciesRegisterFirst() = runBlocking {
        packages["ui://pkg/base"] = pkg("com.shop.base", mapOf("Badge" to "badge()"))
        packages["ui://pkg/app"] = pkg("com.shop.app", mapOf("Layout" to "layout()"),
            dependencies = ""","dependencies":[{"package":"ui://pkg/base","sha256":"${sha256(packages.getValue("ui://pkg/base"))}"}]""")
        val view = loader.load(view("Layout", "ui://pkg/app"))
        assertEquals(listOf("Badge", "Layout"), view.components.keys.toList())
    }

    @Test fun anInlinePackageNeedsNoFetch() = runBlocking {
        val inline = ResourceContent(
            uri = "ui://view/inline",
            mimeType = "application/bindjs+json",
            text = """{"spec":"1.0","component":"Layout","package":${packages.getValue("ui://pkg/shop")}}"""
        )
        assertEquals("Layout", loader.load(inline).component)
        assertEquals(0, server.requestCount + cdn.requestCount)
    }

    @Test fun anHttpContentUrlIsNotUsed() = runBlocking {
        val http = MCPAppsClient(server.url("/mcp").toString())
        server.dispatcher.let { inner ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val response = inner.dispatch(request)
                    val body = response.getBody()?.readUtf8() ?: return response
                    return response.setBody(body.replace("https://", "http://"))
                }
            }
        }
        BindJSViewLoader(http, BindJSPackageCache(), cdnHttp).load(view("Layout", "ui://pkg/shop"))
        assertEquals(0, cdn.requestCount)
        assertTrue("resources/read" in methods)
    }

    @Test fun initializeDeclaresTheConfiguredTypes() = runBlocking {
        val declaring = MCPAppsClient(server.url("/mcp").toString(),
            mimeTypes = listOf(MCPAppsClient.BINDJS_VIEW_DECLARATION) + MCPAppsClient.DEFAULT_MIME_TYPES)
        declaring.listResources()
        val declared = initializeParams.single()["capabilities"]!!.jsonObject["extensions"]!!
            .jsonObject["io.modelcontextprotocol/ui"]!!.jsonObject["mimeTypes"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("application/bindjs+json;version=1.0", "application/vnd.bindjs+json", "text/html;profile=mcp-app"), declared)
        assertTrue(BindJSViewLoader.isView("application/bindjs+json"))
        assertFalse(BindJSViewLoader.isView("application/bindjs-package+json"))
        assertFalse(BindJSViewLoader.isView("application/vnd.bindjs+json"))
    }
}
