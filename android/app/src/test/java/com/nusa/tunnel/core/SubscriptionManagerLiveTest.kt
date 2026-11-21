package com.nusa.tunnel.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Network smoke test enabled in GitHub Actions with NUSATUNNEL_SUBSCRIPTION_URL.
 * It deliberately asserts protocol coverage and prints counts only; never emits
 * the subscription body, server addresses, or credentials to CI logs.
 */
class SubscriptionManagerLiveTest {
    @Test
    fun `live subscription fetch returns supported profiles`() = runTest {
        val url = System.getenv("NUSATUNNEL_SUBSCRIPTION_URL")
        assumeTrue("Live subscription URL is not configured", !url.isNullOrBlank())

        val result = SubscriptionManager.fetch(url!!)
        assertNull("Subscription fetch/parse failed: ${result.error}", result.error)
        assertTrue("Too few supported profiles parsed: ${result.servers.size}", result.servers.size > 1_000)

        val counts = result.servers.groupingBy { it.proto }.eachCount()
        for (proto in listOf(Proto.VMess, Proto.VLESS, Proto.Trojan, Proto.Shadowsocks)) {
            assertTrue("No $proto profiles were parsed", (counts[proto] ?: 0) > 0)
        }
        println("Live subscription smoke test: ${result.servers.size} profiles parsed; protocol counts=$counts")
        assertEquals(4, counts.keys.size)
    }
}
