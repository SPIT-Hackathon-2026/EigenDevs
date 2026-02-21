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
            val fis = FileInputStream(bundleFile)
            newChunkedResponse(Response.Status.OK, "application/octet-stream", fis)
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
         * Works on WiFi or hotspot; returns null if no network interface found.
         */
        fun getLocalIp(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()
                    ?.asSequence()
                    ?.flatMap { it.inetAddresses.asSequence() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            } catch (e: Exception) {
                null
            }
        }
    }
}
