package com.capstone.storyvenue.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

object ChapterBookApi {
    fun generateChapter(
        token: String,
        sessionId: String,
        questionNo: Int? = null,
        chapterType: String? = null,
        allowBasic: Boolean = false,
    ): Result<GeneratedChapterData> {
        return try {
            val payload = JSONObject().apply {
                put("session_id", sessionId)
                if (questionNo != null) {
                    put("question_no", questionNo)
                }
                if (!chapterType.isNullOrBlank()) {
                    put("chapter_type", chapterType)
                }
                put("allow_basic", allowBasic)
            }.toString()

            val response = apiChapterClient.newCall(
                authPost("$API_BASE_URL/chapters/generate", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                Result.success(
                    GeneratedChapterData(
                        id = json.optString("id", ""),
                        sessionId = json.optString("session_id", sessionId),
                        title = json.optString("title", ""),
                        content = json.optString("content", ""),
                        chapterType = json.optString("chapter_type", chapterType.orEmpty()),
                        sourceQuestionNo = if (json.has("source_question_no") && !json.isNull("source_question_no")) json.optInt("source_question_no") else null,
                        storyQualityAtGeneration = json.optCleanString("story_quality_at_generation").ifBlank { null },
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "챕터 생성 실패")))
            }
        } catch (_: SocketTimeoutException) {
            Result.failure(Exception("이야기 생성 시간이 길어지고 있어요. 다시 시도해주세요."))
        } catch (_: InterruptedIOException) {
            Result.failure(Exception("이야기 생성 시간이 길어지고 있어요. 다시 시도해주세요."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listChapters(token: String, sessionId: String? = null): Result<List<ChapterDraftData>> {
        return try {
            val url = if (sessionId.isNullOrBlank()) {
                "$API_BASE_URL/chapters"
            } else {
                "$API_BASE_URL/chapters?session_id=$sessionId"
            }
            val response = apiClient.newCall(authGet(url, token)).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<ChapterDraftData>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ChapterDraftData(
                            id = obj.getString("id"),
                            title = obj.optString("title", ""),
                            content = obj.optString("content", ""),
                            chapterType = obj.optString("chapter_type", ""),
                            createdAt = obj.optString("created_at", ""),
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "챕터 목록 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun compileBook(
        token: String,
        chapterIds: List<String>,
        title: String,
    ): Result<BookDetailData> {
        return try {
            val payload = JSONObject().apply {
                put("chapter_ids", JSONArray(chapterIds))
                put("title", title)
            }.toString()
            val response = apiClient.newCall(
                authPost("$API_BASE_URL/book/compile", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                Result.success(
                    BookDetailData(
                        id = json.getString("id"),
                        title = json.optString("title", ""),
                        subtitle = json.optCleanString("subtitle").ifBlank { null },
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "책 만들기 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
