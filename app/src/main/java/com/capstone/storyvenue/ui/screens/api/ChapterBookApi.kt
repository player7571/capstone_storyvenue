package com.capstone.storyvenue.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

object ChapterBookApi {
    private fun parseBookDetail(json: JSONObject): BookDetailData {
        val chapters = mutableListOf<BookChapterPayloadData>()
        val chaptersArray = json.optJSONArray("chapters") ?: JSONArray()
        for (i in 0 until chaptersArray.length()) {
            val obj = chaptersArray.optJSONObject(i) ?: continue
            chapters.add(
                BookChapterPayloadData(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    sourceQuestionNo = if (obj.has("source_question_no") && !obj.isNull("source_question_no")) {
                        obj.optInt("source_question_no")
                    } else {
                        null
                    },
                )
            )
        }
        return BookDetailData(
            id = json.getString("id"),
            title = json.optString("title", ""),
            subtitle = json.optCleanString("subtitle").ifBlank { null },
            chapters = chapters,
        )
    }

    private fun parseGeneratedChapter(
        json: JSONObject,
        sessionId: String,
        fallbackChapterType: String? = null,
    ) = GeneratedChapterData(
        id = json.optString("id", ""),
        sessionId = json.optString("session_id", sessionId),
        title = json.optString("title", ""),
        content = json.optString("content", ""),
        chapterType = json.optString("chapter_type", fallbackChapterType.orEmpty()),
        sourceQuestionNo = if (json.has("source_question_no") && !json.isNull("source_question_no")) json.optInt("source_question_no") else null,
        storyQualityAtGeneration = json.optCleanString("story_quality_at_generation").ifBlank { null },
    )

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
                Result.success(parseGeneratedChapter(json, sessionId, chapterType))
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

    fun getLatestChapter(
        token: String,
        sessionId: String,
        questionNo: Int? = null,
    ): Result<GeneratedChapterData?> {
        return try {
            val url = buildString {
                append("$API_BASE_URL/chapters/latest?session_id=$sessionId")
                if (questionNo != null) {
                    append("&question_no=$questionNo")
                }
            }
            val response = apiClient.newCall(authGet(url, token)).execute()
            val body = response.body?.string() ?: ""
            when {
                response.isSuccessful -> {
                    val json = JSONObject(body)
                    Result.success(parseGeneratedChapter(json, sessionId))
                }
                response.code == 404 -> {
                    val detail = parseErrorMessage(body, "최신 초안 조회 실패")
                    if (detail == "생성된 초안이 없습니다.") {
                        Result.success(null)
                    } else {
                        Result.failure(Exception(detail))
                    }
                }
                else -> Result.failure(Exception(parseErrorMessage(body, "최신 초안 조회 실패")))
            }
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
                            sourceQuestionNo = if (obj.has("source_question_no") && !obj.isNull("source_question_no")) {
                                obj.optInt("source_question_no")
                            } else {
                                null
                            },
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

    fun deleteChapter(token: String, chapterId: String): Result<Unit> {
        return try {
            val response = apiClient.newCall(
                authDelete("$API_BASE_URL/chapters/$chapterId", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "챕터 삭제 실패")))
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
                Result.success(parseBookDetail(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "책 만들기 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createAutobiography(
        token: String,
        sessionId: String,
        chapterIds: List<String>,
        title: String,
    ): Result<BookDetailData> {
        return try {
            val payload = JSONObject().apply {
                put("session_id", sessionId)
                put("chapter_ids", JSONArray(chapterIds))
                put("title", title)
            }.toString()
            val response = apiChapterClient.newCall(
                authPost("$API_BASE_URL/book/autobiography", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                Result.success(parseBookDetail(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "자서전 생성 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun publishAutobiography(
        token: String,
        sessionId: String,
        chapterIds: List<String>,
        title: String,
    ): Result<Unit> {
        return try {
            val payload = JSONObject().apply {
                put("session_id", sessionId)
                put("chapter_ids", JSONArray(chapterIds))
                put("title", title)
            }.toString()
            val response = apiChapterClient.newCall(
                authPost("$API_BASE_URL/book/autobiography/publish", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "자서전 게시 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
