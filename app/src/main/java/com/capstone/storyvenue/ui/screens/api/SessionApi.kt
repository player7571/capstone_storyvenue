package com.capstone.storyvenue.ui.screens

import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object SessionApi {
    fun createSession(token: String, title: String, theme: String): Result<SessionData> {
        return try {
            val payload = JSONObject().apply {
                put("title", title)
                put("theme", theme)
            }.toString()
            val response = apiClient.newCall(
                authPost("$API_BASE_URL/sessions", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(
                    SessionData(
                        id = json.getString("id"),
                        title = json.optCleanString("title"),
                        theme = json.optCleanString("theme"),
                        status = json.optCleanString("status"),
                        createdAt = json.optString("created_at", ""),
                        interviewState = parseInterviewState(json.optJSONObject("interview_state")),
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "문답 시작 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSessions(token: String): Result<List<InterviewSession>> {
        return try {
            val response = apiClient.newCall(authGet("$API_BASE_URL/sessions", token)).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<InterviewSession>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        InterviewSession(
                            id = obj.getString("id"),
                            number = arr.length() - i,
                            date = obj.optString("created_at", "").take(10).replace("-", "."),
                            title = obj.optString("title", "문답"),
                            sessionType = obj.optCleanString("session_type"),
                            status = obj.optCleanString("status"),
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "문답 목록 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteSession(token: String, sessionId: String): Result<Unit> {
        return try {
            val response = apiClient.newCall(
                authDelete("$API_BASE_URL/sessions/$sessionId", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "문답 삭제 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createPhotoSession(
        token: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
    ): Result<PhotoSessionStartData> {
        return try {
            val mediaType = ApiHttp.mediaTypeOrDefault(contentType)
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file",
                    fileName,
                    imageBytes.toRequestBody(mediaType),
                )
                .build()

            val request = Request.Builder()
                .url("$API_BASE_URL/sessions/photo")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val response = apiClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                Result.success(
                    PhotoSessionStartData(
                        sessionId = json.getString("session_id"),
                        photoUrl = json.optString("photo_url", ""),
                        aiMessage = json.optString("ai_message", ""),
                        createdAt = json.optString("created_at", ""),
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "사진 문답 시작에 실패했습니다")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSessionDetail(token: String, sessionId: String): Result<SessionDetailData> {
        return try {
            val response = apiClient.newCall(
                authGet("$API_BASE_URL/sessions/$sessionId", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                Result.success(
                    SessionDetailData(
                        id = json.getString("id"),
                        sessionType = json.optCleanString("session_type").ifBlank { "voice" },
                        photoUrl = json.optCleanString("photo_url").ifBlank { null },
                        status = json.optCleanString("status"),
                        interviewState = parseInterviewState(json.optJSONObject("interview_state")),
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "문답 상세 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun goToPreviousQuestion(
        token: String,
        sessionId: String,
    ): Result<InterviewPromptData> {
        return try {
            val request = Request.Builder()
                .url("$API_BASE_URL/sessions/$sessionId/previous-question")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(apiJsonType))
                .build()

            val response = apiClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(
                    parseInterviewState(JSONObject(body))
                        ?: throw Exception("이전 질문 상태를 불러오지 못했습니다.")
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "이전 질문으로 이동 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun goToNextQuestion(
        token: String,
        sessionId: String,
    ): Result<InterviewPromptData> {
        return try {
            val request = Request.Builder()
                .url("$API_BASE_URL/sessions/$sessionId/next-question")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(apiJsonType))
                .build()

            val response = apiClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(
                    parseInterviewState(JSONObject(body))
                        ?: throw Exception("다음 질문 상태를 불러오지 못했습니다.")
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "다음 질문으로 이동 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchImageBytes(url: String): Result<ByteArray> {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = apiClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) Result.failure(Exception("이미지 응답이 비어 있습니다."))
                else Result.success(bytes)
            } else {
                Result.failure(Exception("이미지 로드 실패 (${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
