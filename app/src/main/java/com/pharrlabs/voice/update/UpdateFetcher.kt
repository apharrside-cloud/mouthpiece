package com.pharrlabs.voice.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Reads the small version file the download site publishes. Sends nothing about the user or the phone. */
object UpdateFetcher {
    const val SITE = "https://mouthpiece.pharrlabs.com"

    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL("$SITE/version.json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (c.responseCode != 200) null else UpdateCheck.parse(c.inputStream.bufferedReader().use { it.readText() })
            } finally { c.disconnect() }
        }.getOrNull()
    }
}
