package com.nusa.tunnel.core

import java.io.BufferedReader
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class Socks5HttpProbeTest {
    @Test
    fun sendsDomainAndHttpRequestThroughControlledSocks5Endpoint() {
        FakeSocks5Server().use { proxy ->
            val result = Socks5HttpProbe.check(
                url = "http://check.example/health?probe=1",
                socksPort = proxy.port,
                timeoutMillis = 2_000,
            )

            assertEquals(204, result.statusCode)
            assertTrue(result.succeeded)
            assertEquals("check.example", proxy.destinationHost)
            assertEquals(80, proxy.destinationPort)
            assertEquals("GET /health?probe=1 HTTP/1.1", proxy.requestLine)
            assertTrue(proxy.headers.orEmpty().contains("Host: check.example"))
        }
    }

    @Test
    fun performsHttpsThroughSocksWithCertificateAndHostnameVerification() {
        val keyStore = testKeyStore()
        val serverContext = serverSslContext(keyStore)
        val clientFactory = trustedClientSocketFactory(keyStore)
        FakeSocks5Server(tlsServerContext = serverContext).use { proxy ->
            val result = Socks5HttpProbe.check(
                url = "https://check.example/secure-health",
                socksPort = proxy.port,
                timeoutMillis = 2_000,
                sslSocketFactory = clientFactory,
            )
            assertEquals(204, result.statusCode)
            assertTrue(result.succeeded)
            assertEquals("check.example", proxy.destinationHost)
            assertEquals("GET /secure-health HTTP/1.1", proxy.requestLine)
        }
    }

    @Test
    fun rejectsHttpsCertificateWhenItsHostnameDoesNotMatch() {
        val keyStore = testKeyStore()
        FakeSocks5Server(tlsServerContext = serverSslContext(keyStore)).use { proxy ->
            assertThrows(SSLException::class.java) {
                Socks5HttpProbe.check(
                    url = "https://wrong.example/",
                    socksPort = proxy.port,
                    timeoutMillis = 2_000,
                    sslSocketFactory = trustedClientSocketFactory(keyStore),
                )
            }
        }
    }

    @Test
    fun returnsAnUnhealthyHttpStatusWithoutTreatingReachabilityAsSuccess() {
        FakeSocks5Server(statusCode = 503).use { proxy ->
            val result = Socks5HttpProbe.check(
                url = "http://check.example/status",
                socksPort = proxy.port,
                timeoutMillis = 2_000,
            )
            assertEquals(503, result.statusCode)
            assertFalse(result.succeeded)
        }
    }

    @Test
    fun rejectsUnsupportedUrlBeforeOpeningASocket() {
        assertThrows(IllegalArgumentException::class.java) {
            Socks5HttpProbe.check("ftp://check.example/file", socksPort = 10808, timeoutMillis = 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Socks5HttpProbe.check("http://user:secret@check.example/", socksPort = 10808, timeoutMillis = 1_000)
        }
    }

    @Test
    fun failsClosedWhenSocks5DoesNotOfferNoAuthentication() {
        FakeSocks5Server(rejectNoAuth = true).use { proxy ->
            assertThrows(IOException::class.java) {
                Socks5HttpProbe.check(
                    url = "http://check.example/",
                    socksPort = proxy.port,
                    timeoutMillis = 2_000,
                )
            }
        }
    }

    @Test
    fun timesOutWhenTheProxyDoesNotAnswerItsGreeting() {
        FakeSocks5Server(stallBeforeGreetingMillis = 500).use { proxy ->
            assertThrows(IOException::class.java) {
                Socks5HttpProbe.check(
                    url = "http://check.example/",
                    socksPort = proxy.port,
                    timeoutMillis = 100,
                )
            }
        }
    }

    private fun testKeyStore(): KeyStore {
        val store = KeyStore.getInstance("PKCS12")
        val input = javaClass.getResourceAsStream("/connection-check-test.p12")
            ?: error("missing controlled TLS test certificate")
        input.use { store.load(it, TEST_KEYSTORE_PASSWORD) }
        return store
    }

    private fun serverSslContext(keyStore: KeyStore): SSLContext {
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, TEST_KEYSTORE_PASSWORD)
        }
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, null, null)
        }
    }

    private fun trustedClientSocketFactory(keyStore: KeyStore): SSLSocketFactory {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        val aliases = keyStore.aliases()
        check(aliases.hasMoreElements()) { "test keystore must contain its certificate" }
        trustStore.setCertificateEntry("connection-check-test", keyStore.getCertificate(aliases.nextElement()))
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers.trustManagers, null)
        }.socketFactory
    }

    private class FakeSocks5Server(
        private val statusCode: Int = 204,
        private val rejectNoAuth: Boolean = false,
        private val stallBeforeGreetingMillis: Long = 0,
        private val tlsServerContext: SSLContext? = null,
    ) : Closeable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort

        @Volatile var destinationHost: String? = null
            private set
        @Volatile var destinationPort: Int? = null
            private set
        @Volatile var requestLine: String? = null
            private set
        @Volatile var headers: List<String>? = null
            private set
        @Volatile var serverFailure: Throwable? = null
            private set

        private val worker = thread(name = "fake-socks5", isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    if (stallBeforeGreetingMillis > 0) {
                        Thread.sleep(stallBeforeGreetingMillis)
                        return@use
                    }
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    val version = input.readUnsignedByte()
                    val methodCount = input.readUnsignedByte()
                    val methods = ByteArray(methodCount)
                    input.readFully(methods)
                    check(version == 0x05 && methods.any { (it.toInt() and 0xff) == 0x00 })
                    if (rejectNoAuth) {
                        output.write(byteArrayOf(0x05, 0xff.toByte()))
                        output.flush()
                        return@use
                    }
                    output.write(byteArrayOf(0x05, 0x00))
                    output.flush()

                    check(input.readUnsignedByte() == 0x05)
                    check(input.readUnsignedByte() == 0x01)
                    input.readUnsignedByte() // reserved
                    val addressType = input.readUnsignedByte()
                    destinationHost = when (addressType) {
                        0x01 -> ByteArray(4).also { input.readFully(it) }
                            .joinToString(".") { (it.toInt() and 0xff).toString() }
                        0x03 -> String(
                            ByteArray(input.readUnsignedByte()).also { input.readFully(it) },
                            StandardCharsets.US_ASCII,
                        )
                        0x04 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
                        else -> error("unexpected SOCKS address type $addressType")
                    }
                    destinationPort = input.readUnsignedShort()
                    output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 0))
                    output.flush()

                    val requestSocket = tlsServerContext?.let { context ->
                        (context.socketFactory.createSocket(socket, "check.example", socket.port, true) as SSLSocket)
                            .apply {
                                useClientMode = false
                                soTimeout = 2_000
                                startHandshake()
                            }
                    } ?: socket
                    try {
                        exchange(requestSocket)
                    } finally {
                        if (requestSocket !== socket) runCatching { requestSocket.close() }
                    }
                }
            } catch (error: Throwable) {
                serverFailure = error
            }
        }

        private fun exchange(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            requestLine = reader.readLine()
            val receivedHeaders = mutableListOf<String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                receivedHeaders += line
            }
            headers = receivedHeaders
            val reason = if (statusCode in 200..299) "No Content" else "Service Unavailable"
            socket.getOutputStream().write(
                "HTTP/1.1 $statusCode $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            socket.getOutputStream().flush()
        }

        override fun close() {
            runCatching { server.close() }
            worker.join(2_000)
            check(!worker.isAlive) { "fake SOCKS server thread did not terminate" }
        }
    }

    private companion object {
        val TEST_KEYSTORE_PASSWORD = "nusa-test".toCharArray()
    }
}
