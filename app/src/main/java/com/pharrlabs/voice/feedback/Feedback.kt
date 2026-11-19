package com.pharrlabs.voice.feedback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class FeedbackKind(val wire: String, val title: String, val hint: String) {
    PROBLEM("problem", "Report a problem", "What went wrong? Which app were you in, and what did you say?"),
    IDEA("idea", "Suggest a feature", "What would you like Mouthpiece to do?"),
}

enum class SendResult { SENT, EMPTY, BUSY, FAILED }

/** Pure logic for the in-app feedback form; the network call lives in [FeedbackSender]. */
object Feedback {
    const val MAX_MESSAGE = 4000
    const val MAX_CONTACT = 200

    /** The JSON body for the download site, or null when there is nothing to send. Sends only what the user typed plus version and phone model. */
    fun payload(kind: FeedbackKind, message: String, contact: String, version: String, android: String, device: String): String? {
        val text = message.trim().take(MAX_MESSAGE)
        if (text.isEmpty()) return null
        return JSONObject()
            .put("type", kind.wire)
            .put("message", text)
            .put("contact", contact.trim().take(MAX_CONTACT))
            .put("version", version)
            .put("android", android)
            .put("device", device)
            .toString()
    }

    fun resultFor(httpStatus: Int?): SendResult = when (httpStatus) {
        200 -> SendResult.SENT
        429 -> SendResult.BUSY
        else -> SendResult.FAILED
    }

    fun message(r: SendResult): String = when (r) {
        SendResult.SENT -> "Thanks, your feedback was sent."
        SendResult.EMPTY -> "Write a few words first."
        SendResult.BUSY -> "You've sent a few already. Please try again later."
        SendResult.FAILED -> "Couldn't send. Check your connection and try again."
    }
}

object FeedbackSender {
    const val URL = "https://mouthpiece.pharrlabs.com/feedback"

    suspend fun send(json: String): SendResult = withContext(Dispatchers.IO) {
        val status = runCatching {
            val c = (URL(URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                c.responseCode
            } finally { c.disconnect() }
        }.getOrNull()
        Feedback.resultFor(status)
    }
}
