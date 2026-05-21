package com.capstone.storyvenue.ui.screens

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal val API_BASE_URL: String = ApiHttp.BASE_URL
internal val apiClient = ApiHttp.client
internal val apiChapterClient = ApiHttp.chapterClient
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

private val bookCreatedAtFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

internal fun formatBookCreatedAt(isoString: String?): String? {
    if (isoString.isNullOrBlank()) return null
    return try {
        val zoned = try {
            ZonedDateTime.parse(isoString)
                .withZoneSameInstant(ZoneId.systemDefault())
        } catch (_: Exception) {
            Instant.parse(isoString).atZone(ZoneId.systemDefault())
        }
        "작성일 ${zoned.format(bookCreatedAtFormatter)}"
    } catch (_: Exception) {
        null
    }
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

private val chatTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val chatListMonthDayFormatter = DateTimeFormatter.ofPattern("MM.dd")
private val chatListFullDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val chatDividerFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)

private fun parseChatZdt(isoString: String?): ZonedDateTime? {
    if (isoString.isNullOrBlank()) return null
    return try {
        ZonedDateTime.parse(isoString).withZoneSameInstant(ZoneId.systemDefault())
    } catch (_: Exception) {
        try {
            Instant.parse(isoString).atZone(ZoneId.systemDefault())
        } catch (_: Exception) {
            null
        }
    }
}

internal fun chatLocalDate(isoString: String?): LocalDate? = parseChatZdt(isoString)?.toLocalDate()

internal fun formatChatTime(isoString: String?): String =
    parseChatZdt(isoString)?.format(chatTimeFormatter).orEmpty()

internal fun formatChatListTime(isoString: String?): String {
    val zdt = parseChatZdt(isoString) ?: return ""
    val today = LocalDate.now(ZoneId.systemDefault())
    val date = zdt.toLocalDate()
    return when {
        date == today -> zdt.format(chatTimeFormatter)
        date == today.minusDays(1) -> "어제"
        date.year == today.year -> date.format(chatListMonthDayFormatter)
        else -> date.format(chatListFullDateFormatter)
    }
}

internal fun formatChatDateDivider(isoString: String?): String {
    val zdt = parseChatZdt(isoString) ?: return ""
    val today = LocalDate.now(ZoneId.systemDefault())
    val date = zdt.toLocalDate()
    return when {
        date == today -> "오늘"
        date == today.minusDays(1) -> "어제"
        else -> zdt.format(chatDividerFormatter)
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
        currentQuestionHasAnswer = json.optBoolean("current_question_has_answer", false),
        currentQuestionAnswerCount = json.optInt("current_question_answer_count", 0),
        currentQuestionStoryReady = json.optBoolean("current_question_story_ready", false),
        currentQuestionStoryQuality = json.optString("current_question_story_quality", "none"),
        currentQuestionCompleted = json.optBoolean("current_question_completed", false),
        currentQuestionCanMoveNext = json.optBoolean("current_question_can_move_next", false),
        storyTargetQuestionNo = if (json.has("story_target_question_no") && !json.isNull("story_target_question_no")) {
            json.optInt("story_target_question_no")
        } else {
            null
        },
        storyTargetHasAnswer = json.optBoolean("story_target_has_answer", false),
        storyTargetAnswerCount = json.optInt("story_target_answer_count", 0),
        storyTargetStoryReady = json.optBoolean("story_target_story_ready", false),
        storyTargetStoryQuality = json.optString("story_target_story_quality", "none"),
        storyTargetIsCurrentQuestion = json.optBoolean("story_target_is_current_question", true),
    )
}

internal fun parseVoiceTurnData(json: JSONObject): VoiceTurnData {
    val audioUrl = json.optCleanString("audio_url")
    val audioStatus = json.optCleanString("audio_status").ifBlank {
        if (audioUrl.isNotBlank()) "ready" else "disabled"
    }
    return VoiceTurnData(
        userText = json.optString("user_text", ""),
        assistantText = json.optString("assistant_text", ""),
        audioUrl = audioUrl.takeIf { it.isNotBlank() }?.let { toAbsoluteUrl(it) },
        audioStatus = audioStatus,
        audioId = json.optCleanString("audio_id").ifBlank { null },
        decision = json.optCleanString("decision").ifBlank { null },
        reasonCode = json.optCleanString("reason_code").ifBlank { null },
        interviewState = parseInterviewState(json.optJSONObject("interview_state")),
    )
}

internal fun parseVoiceAudioStatusData(json: JSONObject): VoiceAudioStatusData {
    val audioUrl = json.optCleanString("audio_url")
    return VoiceAudioStatusData(
        audioId = json.optCleanString("audio_id"),
        audioStatus = json.optCleanString("audio_status").ifBlank {
            if (audioUrl.isNotBlank()) "ready" else "pending"
        },
        audioUrl = audioUrl.takeIf { it.isNotBlank() }?.let { toAbsoluteUrl(it) },
    )
}
