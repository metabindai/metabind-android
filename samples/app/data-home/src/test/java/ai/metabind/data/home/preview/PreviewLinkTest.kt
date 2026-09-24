package ai.metabind.data.home.preview

import org.junit.Assert.*
import org.junit.Test

class PreviewLinkTest {
    private val org = "a".repeat(20)
    private val project = "b".repeat(20)
    private val key = "a".repeat(64)
    private val endpoint = "https://mcp.metabind.ai/$org/projects/$project"
    private val wrapper = "https://www.metabind.ai/preview/mcp?url=$endpoint/draft"

    @Test fun discardsLegacyCredentialAndPreservesDraftAndTitle() {
        val parsed = MCPPreviewLink.parse("$wrapper#key=$key&name=Finance%20Preview")!!
        assertEquals("Finance Preview", parsed.name)
        assertEquals("$endpoint/draft", parsed.serverUrl)
        assertFalse(parsed.previewUrl.contains(key))
        assertFalse(parsed.toString().contains(key))
        val saved = MCPPreviewLink.parse(parsed.previewUrl)!!
        assertTrue(saved.isDraft)
        assertEquals(parsed.name, saved.name)
        assertEquals(parsed.serverUrl, saved.serverUrl)
    }

    @Test fun publishedAndDraftLinksRemainSeparate() {
        val published = MCPPreviewLink.parse("$endpoint#key=$key")!!
        assertEquals(endpoint, published.serverUrl)
        assertFalse(published.isDraft)
        assertFalse(MCPPreviewLink.parse(published.previewUrl)!!.isDraft)
        assertNotEquals(published.previewUrl, MCPPreviewLink.parse(wrapper)!!.previewUrl)
    }

    @Test fun developmentAndProductionAccessAreSeparate() {
        val dev = MCPPreviewLink.parse("${endpoint.replace("mcp.", "mcp-dev.")}#key=$key")!!
        assertTrue(dev.isDevelopment)
        assertTrue(dev.previewUrl.startsWith("https://dev.metabind.ai/"))
        assertNotEquals(MCPPreviewLink.parse(endpoint)!!.serverUrl, dev.serverUrl)
    }

    @Test fun uuidProjectIdsAreAccepted() {
        val id = "00000000-0000-0000-0000-000000000000"
        assertEquals(id, MCPPreviewLink.parse("https://mcp.metabind.ai/$id/projects/$id")!!.projectId)
    }

    @Test fun rejectsMalformedOrAmbiguousCredentialsWithoutEchoingInput() {
        val bad = listOf("$wrapper&key=$key", "$wrapper#key=$key&key=$key", "$wrapper#key=bad",
            "$wrapper&url=$endpoint", "$wrapper#key=$key&name=one&name=two", "$wrapper#key=%XX")
        bad.forEach { input ->
            val error = runCatching { MCPPreviewLink.parse(input) }.exceptionOrNull()
            assertNotNull(input.substringBefore('#'), error)
            assertFalse(error.toString().contains(key))
        }
    }

    @Test fun rejectsUntrustedOrNoncanonicalEndpoints() {
        val badEndpoints = listOf(endpoint.replace("https", "http"), endpoint.replace("mcp.metabind.ai", "mcp.metabind.ai.evil.com"),
            endpoint.replace("mcp.metabind.ai", "user@mcp.metabind.ai"), endpoint.replace("mcp.metabind.ai", "mcp.metabind.ai:443"),
            endpoint.replace(org, "named-org"), "$endpoint/extra", "$endpoint/", "$endpoint?token=$key", "$endpoint#key=$key")
        badEndpoints.forEach { bad ->
            val encoded = java.net.URLEncoder.encode(bad, "UTF-8")
            assertTrue(runCatching { MCPPreviewLink.parse("https://www.metabind.ai/preview/mcp?url=$encoded") }.isFailure)
        }
    }

    @Test fun contentPreviewRemainsSupportedAndQueriesAreRemoved() {
        val content = "https://www.metabind.ai/preview/$key"
        assertNull(MCPPreviewLink.parse(content))
        assertEquals(content, MCPPreviewLink.contentUrl("$content?utm_source=test"))
        assertTrue(runCatching { MCPPreviewLink.contentUrl(content.replace("www.metabind.ai", "evilmetabind.ai")) }.isFailure)
        assertTrue(runCatching { MCPPreviewLink.contentUrl("$content#key=$key") }.isFailure)
    }
}
