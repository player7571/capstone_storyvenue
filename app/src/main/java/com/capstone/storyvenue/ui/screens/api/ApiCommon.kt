package com.capstone.storyvenue.ui.screens

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal val API_BASE_URL: String = ApiHttp.BASE_URL
internal val apiClient = ApiHttp.client
internal val apiVoiceClient = ApiHttp.voiceClient
internal val apiJsonType = ApiHttp.JSON_TYPE

internal fun authGet(url: String, token: String): Request =
    Request.Builder().url(url)
        .addHeader("Authorization", "Bearer $token")
        .get().build()

internal fun authPost(url: String, token: String, body: String = "{}"): Request =
    Request.Builder().url(url)
        .addHeader("Authorization", "Bearer $token")
        .post(body.toRequestBody(apiJsonType)).build()

internal fun authPut(url: String, token: String, body: String = "{}"): Request =
    Request.Builder().url(url)
        .addHeader("Authorization", "Bearer $token")
        .put(body.toRequestBody(apiJsonType)).build()

internal fun authDelete(url: String, token: String): Request =
    Request.Builder().url(url)
        .addHeader("Authorization", "Bearer $token")
        .delete().build()

internal fun parseErrorMessage(body: String, fallback: String): String {
    if (body.isBlank()) return fallback
    return try {
        val json = JSONObject(body)
        val detail = json.optString("detail", "").trim()
        val message = json.optString("message", "").trim()
        when {
            detail.isNotEmpty() -> detail
            message.isNotEmpty() -> message
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

internal fun JSONObject.optCleanString(name: String): String {
    if (!has(name) || isNull(name)) return ""
    return optString(name, "").takeUnless { it == "null" } ?: ""
}

internal fun toAbsoluteUrl(pathOrUrl: String): String {
    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
    return if (pathOrUrl.startsWith("/")) "$API_BASE_URL$pathOrUrl" else "$API_BASE_URL/$pathOrUrl"
}

internal fun timeAgo(isoString: String?): String {
    if (isoString == null) return ""
    return try {
        val then = try {
            ZonedDateTime.parse(isoString).toInstant()
        } catch (_: Exception) {
            Instant.parse(isoString)
        }
        val now = Instant.now()
        val minutes = ChronoUnit.MINUTES.between(then, now)
        when {
            minutes < 1 -> "방금"
            minutes < 60 -> "${minutes}분 전"
            minutes < 1440 -> "${minutes / 60}시간 전"
            else -> "${minutes / 1440}일 전"
        }
    } catch (_: Exception) {
        ""
    }
}

internal fun parseProfile(json: JSONObject) = ProfileData(
    id = json.getString("id"),
    name = json.optCleanString("name"),
    email = json.optCleanString("email"),
    avatarUrl = json.optCleanString("avatar_url").ifBlank { null },
)

internal fun parseInterviewState(json: JSONObject?): InterviewPromptData? {
    if (json == null) return null
    return InterviewPromptData(
        currentQuestionNo = json.optInt("current_question_no", 1),
        totalQuestions = json.optInt("total_questions", 10),
        mainQuestion = json.optString("main_question", ""),
        questionHint = json.optCleanString("question_hint").ifBlank { null },
        followUpCount = json.optInt("follow_up_count", 0),
        questionStatus = json.optString("question_status", "main"),
        progressPercent = json.optInt("progress_percent", 0),
        isInterviewComplete = json.optBoolean("is_interview_complete", false),
    )
}

internal fun parseVoiceTurnData(json: JSONObject): VoiceTurnData {
    val audioUrl = json.optCleanString("audio_url")
    return VoiceTurnData(
        userText = json.optString("user_text", ""),
        assistantText = json.optString("assistant_text", ""),
        audioUrl = audioUrl.takeIf { it.isNotBlank() }?.let { toAbsoluteUrl(it) },
        decision = json.optCleanString("decision").ifBlank { null },
        interviewState = parseInterviewState(json.optJSONObject("interview_state")),
    )
}
