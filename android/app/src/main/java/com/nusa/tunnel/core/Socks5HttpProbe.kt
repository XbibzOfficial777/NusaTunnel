package com.nusa.tunnel.core

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Result of an HTTP(S) GET issued through NusaTunnel's active local SOCKS5 inbound. */
data class ConnectionCheckResult(
    val statusCode: Int,
    val elapsedMillis: Long,
) {
    val succeeded: Boolean get() = statusCode in 200..399
}

/**
 * Small, bounded HTTP/1.1 probe through SOCKS5. The destination hostname is sent to SOCKS5
 * rather than resolved by the app, so the check exercises the running Xray proxy path.
 */
object Socks5HttpProbe {
    private const val MAX_URL_LENGTH = 2_048
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val MAX_HEADER_LINES = 128
    private val STATUS_LINE = Regex("HTTP/1\\.[01] ([1-5][0-9]{2})(?: .*)?")
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "nusa-connection-check-timeout").apply { isDaemon = true }
    }

    fun check(url: String, socksPort: Int, timeoutMillis: Int): ConnectionCheckResult =
        check(
            url = url,
            socksPort = socksPort,
            timeoutMillis = timeoutMillis,
            sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
        )

    internal fun check(
        url: String,
        socksPort: Int,
        timeoutMillis: Int,
        sslSocketFactory: SSLSocketFactory,
    ): ConnectionCheckResult {
        require(socksPort in 1..65_535) { "SOCKS port must be 1..65535" }
        require(timeoutMillis in 1..60_000) { "Timeout must be 1..60000 milliseconds" }
        val uri = parseUrl(url)
        val scheme = uri.scheme.lowercase()
        val host = uri.host.removeSurrounding("[", "]")
        val targetPort = uri.port.takeIf { it != -1 } ?: if (scheme == "https") 443 else 80
        val target = requestTarget(uri)
        val hostHeader = hostHeader(host, uri.port, scheme)
        val startNanos = System.nanoTime()
        val timedOut = AtomicBoolean(false)
        val proxySocket = Socket()
        val timeoutTask = timeoutScheduler.schedule({
            timedOut.set(true)
            runCatching { proxySocket.close() }
        }, timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        try {
            proxySocket.tcpNoDelay = true
            proxySocket.soTimeout = timeoutMillis
            proxySocket.connect(
                InetSocketAddress(InetAddress.getByName("127.0.0.1"), socksPort),
                timeoutMillis,
            )
            connectSocks5(proxySocket, host, targetPort)

            val requestSocket = if (scheme == "https") {
                createVerifiedTlsSocket(proxySocket, host, targetPort, timeoutMillis, sslSocketFactory)
            } else {
                proxySocket
            }
            val statusCode = try {
                requestSocket.soTimeout = timeoutMillis
                writeRequest(requestSocket, target, hostHeader)
                readFinalStatusCode(requestSocket)
            } finally {
                if (requestSocket !== proxySocket) runCatching { requestSocket.close() }
            }
            val elapsedNanos = System.nanoTime() - startNanos
            if (timedOut.get() || elapsedNanos >= timeoutMillis.toLong() * 1_000_000L) {
                throw SocketTimeoutException("Connection check exceeded ${timeoutMillis}ms")
            }
            return ConnectionCheckResult(
                statusCode = statusCode,
                elapsedMillis = (elapsedNanos / 1_000_000L).coerceAtLeast(0L),
            )
        } catch (error: IOException) {
            if (timedOut.get() || System.nanoTime() - startNanos >= timeoutMillis.toLong() * 1_000_000L) {
                throw SocketTimeoutException("Connection check exceeded ${timeoutMillis}ms").apply {
                    initCause(error)
                }
            }
            throw error
        } finally {
            timeoutTask.cancel(false)
            runCatching { proxySocket.close() }
        }
    }

    private fun parseUrl(value: String): URI {
        require(value.length <= MAX_URL_LENGTH) { "Connection-check URL is too long" }
        val parsed = runCatching { URI(value.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid connection-check URL", it) }
        require(ConnectionCheckSettingsValidator.isValidUrl(value)) {
            "Connection-check URL must be an absolute HTTP or HTTPS URL without credentials or a fragment"
        }
        return URI(parsed.toASCIIString())
    }

    private fun connectSocks5(socket: Socket, host: String, port: Int) {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        output.write(byteArrayOf(0x05, 0x01, 0x00)) // SOCKS5, one method, no authentication
        output.flush()
        val version = input.readUnsignedByte()
        val method = input.readUnsignedByte()
        if (version != 0x05 || method != 0x00) {
            throw IOException("SOCKS5 proxy rejected the no-auth method")
        }

        output.writeByte(0x05)
        output.writeByte(0x01) // CONNECT
        output.writeByte(0x00)
        writeSocksAddress(output, host)
        output.writeShort(port)
        output.flush()

        val replyVersion = input.readUnsignedByte()
        val replyCode = input.readUnsignedByte()
        input.readUnsignedByte() // reserved
        val addressType = input.readUnsignedByte()
        if (replyVersion != 0x05) throw IOException("Invalid SOCKS5 CONNECT reply")
        when (addressType) {
            0x01 -> input.skipFully(4)
            0x03 -> input.skipFully(input.readUnsignedByte())
            0x04 -> input.skipFully(16)
            else -> throw IOException("Unsupported SOCKS5 reply address type")
        }
        input.readUnsignedShort() // bound port
        if (replyCode != 0x00) throw IOException("SOCKS5 CONNECT failed with code $replyCode")
    }

    private fun writeSocksAddress(output: DataOutputStream, host: String) {
        val ipv4 = parseIpv4(host)
        when {
            ipv4 != null -> {
                output.writeByte(0x01)
                output.write(ipv4)
            }
            host.contains(':') -> {
                val address = runCatching { InetAddress.getByName(host) }
                    .getOrElse { throw IOException("Invalid IPv6 connection-check host", it) }
                if (address !is Inet6Address) throw IOException("Invalid IPv6 connection-check host")
                output.writeByte(0x04)
                output.write(address.address)
            }
            else -> {
                val domain = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }
                    .getOrElse { throw IOException("Invalid connection-check host", it) }
                    .toByteArray(StandardCharsets.US_ASCII)
                if (domain.isEmpty() || domain.size > 255) throw IOException("Invalid SOCKS5 domain length")
                output.writeByte(0x03)
                output.writeByte(domain.size)
                output.write(domain)
            }
        }
    }

    private fun parseIpv4(host: String): ByteArray? {
        if (host.none { it == '.' } || host.any { !(it.isDigit() || it == '.') }) return null
        val parts = host.split('.')
        if (parts.size != 4) throw IOException("Invalid IPv4 connection-check host")
        return ByteArray(4) { index ->
            val octet = parts[index].toIntOrNull()?.takeIf { it in 0..255 }
                ?: throw IOException("Invalid IPv4 connection-check host")
            octet.toByte()
        }
    }

    private fun createVerifiedTlsSocket(
        socket: Socket,
        host: String,
        port: Int,
        timeoutMillis: Int,
        factory: SSLSocketFactory,
    ): SSLSocket {
        val tlsSocket = factory.createSocket(socket, host, port, true) as SSLSocket
        tlsSocket.soTimeout = timeoutMillis
        tlsSocket.sslParameters = tlsSocket.sslParameters.apply {
            endpointIdentificationAlgorithm = "HTTPS"
        }
        tlsSocket.startHandshake()
        return tlsSocket
    }

    private fun requestTarget(uri: URI): String {
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val target = path + query
        if ('\r' in target || '\n' in target) throw IOException("Invalid HTTP request target")
        return target
    }

    private fun hostHeader(host: String, explicitPort: Int, scheme: String): String {
        val hostPart = if (host.contains(':')) "[$host]" else host
        val defaultPort = if (scheme == "https") 443 else 80
        return if (explicitPort != -1 && explicitPort != defaultPort) "$hostPart:$explicitPort" else hostPart
    }

    private fun writeRequest(socket: Socket, target: String, hostHeader: String) {
        val request = buildString {
            append("GET ").append(target).append(" HTTP/1.1\r\n")
            append("Host: ").append(hostHeader).append("\r\n")
            append("User-Agent: NusaTunnel-ConnectionCheck/1\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        socket.getOutputStream().apply {
            write(request)
            flush()
        }
    }

    private fun readFinalStatusCode(socket: Socket): Int {
        val input = socket.getInputStream()
        repeat(4) {
            var headerBytes = 0
            val statusLine = readAsciiLine(input).also {
                headerBytes += (it?.length ?: 0) + 2
            } ?: throw IOException("HTTP response ended before the status line")
            val match = STATUS_LINE.matchEntire(statusLine)
                ?: throw IOException("Malformed HTTP status line")
            val code = match.groupValues[1].toInt()
            var lines = 0
            while (true) {
                val line = readAsciiLine(input) ?: throw IOException("HTTP response ended inside headers")
                headerBytes += line.length + 2
                lines++
                if (headerBytes > MAX_HEADER_BYTES || lines > MAX_HEADER_LINES) {
                    throw IOException("HTTP response headers exceed safety limits")
                }
                if (line.isEmpty()) break
            }
            if (code !in 100..199 || code == 101) return code
        }
        throw IOException("Too many interim HTTP responses")
    }

    private fun readAsciiLine(input: java.io.InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) return if (line.size() == 0) null else line.toString(StandardCharsets.US_ASCII.name())
            if (value == '\n'.code) return line.toString(StandardCharsets.US_ASCII.name())
            if (value != '\r'.code) {
                if (line.size() >= 8_192) throw IOException("HTTP response line is too long")
                line.write(value)
            }
        }
    }

    private fun DataInputStream.skipFully(length: Int) {
        var remaining = length
        val buffer = ByteArray(minOf(remaining, 256))
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(remaining, buffer.size))
            if (read < 0) throw IOException("Truncated SOCKS5 reply")
            remaining -= read
        }
    }

}
