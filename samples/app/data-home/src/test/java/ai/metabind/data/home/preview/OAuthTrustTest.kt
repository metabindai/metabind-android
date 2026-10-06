package ai.metabind.data.home.preview

import org.junit.Assert.*
import org.junit.Test

class OAuthTrustTest {
    @Test fun discoveryCannotSendCodesOrTokensToAnotherEnvironmentOrHost() {
        OAuthTrust.validate(false, "https://www.metabind.ai/oauth/authorize", "https://api.metabind.ai/oauth/token", "https://mcp.metabind.ai/register")
        OAuthTrust.validate(true, "https://dev.metabind.ai/oauth/authorize", "https://api-dev.metabind.ai/oauth/token", "https://api-dev.metabind.ai/register")
        for (token in listOf("http://api.metabind.ai/token", "https://evil.example/token", "https://api-dev.metabind.ai/token", "https://api.metabind.ai:443/token", "https://user@api.metabind.ai/token", "https://api.metabind.ai/token?redirect=evil", "https://api.metabind.ai/token#fragment")) {
            assertTrue(runCatching { OAuthTrust.validate(false, "https://www.metabind.ai/authorize", token) }.isFailure)
        }
    }

    @Test fun requiresExactProjectPreviewScopes() {
        assertTrue(OAuthTrust.validScope(OAuthTrust.scopes.split(' ').reversed().joinToString(" ")))
        assertFalse(OAuthTrust.validScope("execute:mcp"))
        assertFalse(OAuthTrust.validScope(OAuthTrust.scopes + " write:projects"))
    }
}
