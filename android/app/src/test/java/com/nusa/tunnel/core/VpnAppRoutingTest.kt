package com.nusa.tunnel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnAppRoutingTest {
    private val owner = "com.example.nusatunnel"
    private val appA = "com.example.alpha"
    private val appB = "org.example.beta"

    @Test
    fun `all apps mode routes normally but always excludes the VPN owner`() {
        val plan = VpnAppRoutingPlanner.create(
            VpnAppRoutingSettings(), owner, setOf(owner, appA, appB),
        )

        assertNull(plan.allowedPackages)
        assertEquals(setOf(owner), plan.disallowedPackages)
    }

    @Test
    fun `selected only mode produces an exact allowlist`() {
        val plan = VpnAppRoutingPlanner.create(
            VpnAppRoutingSettings(VpnAppRoutingMode.SelectedOnly, setOf(appA, appB)),
            owner,
            setOf(owner, appA, appB),
        )

        assertEquals(setOf(appA, appB), plan.allowedPackages)
        assertTrue(plan.disallowedPackages.isEmpty())
        assertTrue(owner !in plan.allowedPackages.orEmpty())
    }

    @Test
    fun `exclude selected mode combines the selected packages with the tunnel owner`() {
        val plan = VpnAppRoutingPlanner.create(
            VpnAppRoutingSettings(VpnAppRoutingMode.ExcludeSelected, setOf(appA)),
            owner,
            setOf(owner, appA, appB),
        )

        assertNull(plan.allowedPackages)
        assertEquals(setOf(owner, appA), plan.disallowedPackages)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `selected only mode fails closed when its allowlist is empty`() {
        VpnAppRoutingPlanner.create(
            VpnAppRoutingSettings(VpnAppRoutingMode.SelectedOnly, emptySet()),
            owner,
            setOf(owner, appA),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stale package selection is rejected rather than silently changing routes`() {
        VpnAppRoutingPlanner.create(
            VpnAppRoutingSettings(VpnAppRoutingMode.ExcludeSelected, setOf(appA)),
            owner,
            setOf(owner, appB),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `VPN app cannot select its own package`() {
        VpnAppRoutingPlanner.create(
            VpnAppRoutingSettings(VpnAppRoutingMode.SelectedOnly, setOf(owner)),
            owner,
            setOf(owner),
        )
    }

    @Test
    fun `package validation rejects malformed identifiers`() {
        assertTrue(VpnAppRoutingPlanner.isValidPackageName(appA))
        assertTrue(!VpnAppRoutingPlanner.isValidPackageName("not a package"))
        assertTrue(!VpnAppRoutingPlanner.isValidPackageName("single"))
        assertTrue(!VpnAppRoutingPlanner.isValidPackageName("com..invalid"))
    }
}
