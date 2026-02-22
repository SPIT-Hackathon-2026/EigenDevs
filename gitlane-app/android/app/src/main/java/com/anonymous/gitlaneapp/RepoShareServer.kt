package com.anonymous.gitlaneapp

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Tiny HTTP server (NanoHTTPD) that serves a single git bundle file.
 * Started when the user taps "Share" on a repo.
 * Stopped when the QR share screen is closed.
 *
 * The QR code encodes the URL to this server, e.g.:
 *   gitlane://192.168.1.5:8088/myrepo
 *
 * The receiving device scans the QR, hits:
 *   http://192.168.1.5:8088/myrepo.bundle
 * …and downloads the entire git bundle.
 */
class RepoShareServer(
    private val bundleFile: File,
    private val repoName: String,
    port: Int = 8088
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return if (session.uri == "/$repoName.bundle") {
            // Use fixed-length response (with Content-Length header) instead of chunked
            // transfer encoding. Chunked encoding causes "unexpected end of stream" errors
            // on the receiving device's OkHttp/Android HTTP stack.
            val fis = FileInputStream(bundleFile)
            newFixedLengthResponse(
                Response.Status.OK,
                "application/octet-stream",
                fis,
                bundleFile.length()
            )
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "GitLane: bundle not found"
            )
        }
    }

    companion object {
        const val PORT = 8088

        /**
         * Returns the device's current local IPv4 address (for QR encoding).
         * Prioritizes WiFi (wlan) and Hotspot (ap) interfaces.
         */
        fun getLocalIp(): String? {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces().toList()
                
                // 1. Priority: Look for explicitly named hotspot interfaces (ap0, softap)
                // In Android, hotspot IP is almost always 192.168.43.1
                val hotspotAddr = interfaces.asSequence()
                    .filter { it.name.contains("ap") || it.name.contains("softap") }
                    .flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                    ?.hostAddress ?: "192.168.43.1" // Fallback to standard Android hotspot IP

                // Check if we are actually in hotspot mode (interface contains 43.1)
                val activeHotspot = interfaces.flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { it.hostAddress == "192.168.43.1" }
                if (activeHotspot != null) return "192.168.43.1"

                // 2. WiFi Interface (usually wlan0) starting with 192.168
                val wifiAddr = interfaces.asSequence()
                    .filter { it.name.contains("wlan") }
                    .flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress.startsWith("192.168.") }
                
                if (wifiAddr != null) return wifiAddr.hostAddress

                // 3. Generic fallback
                interfaces.asSequence()
                    .flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            } catch (e: Exception) {
                null
            }
        }
    }
}
