package io.github.mookeview.octop

import org.junit.Assert.*
import org.junit.Test

class ServerAddressTest {
    @Test fun preservesCustomPortAndProxySubpath() {
        assertEquals(
            "https://octop.mookeview.cn:8880/octop/",
            ServerAddress.parse(" HTTPS://OCTOP.MOOKEVIEW.CN:8880/octop/ ")?.url
        )
    }

    @Test fun usesHttpsWhenSchemeIsOmitted() {
        assertEquals("https://example.com/", ServerAddress.parse("example.com")?.url)
    }

    @Test fun supportsEmulatorAndIpv6Servers() {
        assertEquals("http://10.0.2.2:18088/", ServerAddress.parse("http://10.0.2.2:18088")?.url)
        assertEquals("http://[::1]:18088/", ServerAddress.parse("http://[::1]:18088")?.url)
    }

    @Test fun rejectsCredentialsAndNonServerAddresses() {
        listOf(
            "", "file:///etc/passwd", "javascript:alert(1)", "https://user:pass@example.com",
            "https://example.com?token=secret", "https://example.com/#/chat", "https://",
            "https://example.com:70000", "https://example.com:0", "https://example.com/../admin"
        ).forEach { assertNull(it, ServerAddress.parse(it)) }
    }

    @Test fun allowsOnlyExactServerAndExplicitAuthenticationOrigins() {
        val server = ServerAddress.parse("https://octop.mookeview.cn:8880")!!
        val auth = listOf("https://nas.mookeview.cn:8880", "https://auth.mookeview.cn:8880")
        assertTrue(server.allowsNavigation("https://octop.mookeview.cn:8880/chat/main", auth))
        assertTrue(server.allowsNavigation("https://nas.mookeview.cn:8880/?state=x", auth))
        assertFalse(server.allowsNavigation("https://nas.mookeview.cn.evil:8880/", auth))
        assertFalse(server.allowsNavigation("http://nas.mookeview.cn:8880/", auth))
        assertFalse(server.allowsNavigation("https://octop.mookeview.cn:8881/", auth))
        assertFalse(server.allowsNavigation("https://octop.mookeview.cn:8880@evil.example/", auth))
    }

    @Test fun treatsExplicitDefaultPortsAsSameOrigin() {
        val server = ServerAddress.parse("https://example.com:443/")!!
        assertEquals("https://example.com/", server.url)
        assertTrue(server.allowsNavigation("https://EXAMPLE.COM:443/login", emptyList()))
        assertFalse(server.allowsNavigation("http://example.com/login", emptyList()))
    }
}
