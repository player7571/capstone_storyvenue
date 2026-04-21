package com.capstone.storyvenue.ui.screens

import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException

object VoiceApi {
    fun voiceTurn(
        token: String,
        sessionId: String,
        audioBytes: ByteArray,
        fileName: String = "recording.m4a",
        contentType: String = "audio/m4a",
    ): Result<VoiceTurnData> {
        return try {
            val mediaType = ApiHttp.mediaTypeOrDefault(contentType)
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart(
                    "audio_file",
                    fileName,
                    audioBytes.toRequestBody(mediaType),
                )
                .build()

            val request = Request.Builder()
                .url("$API_BASE_URL/voice/turn")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val response = apiVoiceClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                Result.success(parseVoiceTurnData(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "음성 문답 처리 실패")))
            }
        } catch (_: SocketTimeoutException) {
            Result.failure(Exception("분석 시간이 길어지고 있습니다. 네트워크 상태를 확인하고 다시 시도해주세요."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun voiceTextTurn(
        token: String,
        sessionId: String,
        userText: String,
    ): Result<VoiceTurnData> {
        return try {
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart("user_text", userText)
                .build()

            val request = Request.Builder()
                .url("$API_BASE_URL/voice/text-turn")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val response = apiVoiceClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)

            if (response.isSuccessful) {
                Result.success(parseVoiceTurnData(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "텍스트 문답 처리 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSessionMessages(token: String, sessionId: String): Result<List<SessionMessageData>> {
        return try {
            val response = apiClient.newCall(
                authGet("$API_BASE_URL/messages?session_id=$sessionId", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<SessionMessageData>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SessionMessageData(
                            id = obj.getString("id"),
                            role = obj.optString("role", ""),
                            content = obj.optString("content", ""),
                            createdAt = obj.optString("created_at", ""),
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "대화 기록 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
