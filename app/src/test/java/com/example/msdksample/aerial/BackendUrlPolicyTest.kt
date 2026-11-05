package com.example.msdksample.aerial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendUrlPolicyTest {

    @Test
    fun debugBuildAllowsHttpForLoopbackOnly() {
        assertTrue(BackendUrlPolicy.isAllowed("http://127.0.0.1:8080", isDebug = true))
        assertTrue(BackendUrlPolicy.isAllowed("http://localhost:8080", isDebug = true))
        assertFalse(BackendUrlPolicy.isAllowed("http://192.168.1.20:8080", isDebug = true))
    }

    @Test
    fun releaseBuildRequiresHttps() {
        assertTrue(BackendUrlPolicy.isAllowed("https://api.example.test", isDebug = false))
        assertFalse(BackendUrlPolicy.isAllowed("http://127.0.0.1:8080", isDebug = false))
    }

    @Test
    fun malformedAndCredentialBearingUrlsAreRejected() {
        assertFalse(BackendUrlPolicy.isAllowed("not-a-url", isDebug = true))
        assertFalse(BackendUrlPolicy.isAllowed("http://user@127.0.0.1:8080", isDebug = true))
    }
}
