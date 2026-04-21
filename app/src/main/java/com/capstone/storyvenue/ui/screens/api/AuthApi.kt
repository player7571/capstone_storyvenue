package com.capstone.storyvenue.ui.screens

import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AuthApi {
    fun login(email: String, password: String): Result<Pair<String, String>> {
        return try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString().toRequestBody(apiJsonType)

            val request = Request.Builder()
                .url("$API_BASE_URL/auth/login")
                .post(body)
                .build()

            val response = apiClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = try {
                    JSONObject(responseBody)
                } catch (_: Exception) {
                    return Result.failure(Exception("로그인 응답 형식이 올바르지 않습니다."))
                }
                val token = json.getString("access_token")
                val userId = json.getString("user_id")
                Result.success(Pair(token, userId))
            } else {
                val detail = parseErrorMessage(responseBody, "로그인에 실패했습니다")
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signup(name: String, email: String, password: String): Result<String> {
        return try {
            val body = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", password)
            }.toString().toRequestBody(apiJsonType)

            val request = Request.Builder()
                .url("$API_BASE_URL/auth/signup")
                .post(body)
                .build()

            val response = apiClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val message = try {
                    JSONObject(responseBody).optString("message", "회원가입 성공")
                } catch (_: Exception) {
                    "회원가입 성공"
                }
                Result.success(message)
            } else {
                val detail = parseErrorMessage(responseBody, "회원가입에 실패했습니다")
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getProfile(token: String): Result<ProfileData> {
        return try {
            val response = apiClient.newCall(authGet("$API_BASE_URL/users/me", token)).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(parseProfile(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "내정보 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfileName(token: String, name: String): Result<ProfileData> {
        return try {
            val payload = JSONObject().apply { put("name", name) }.toString()
            val response = apiClient.newCall(
                authPut("$API_BASE_URL/users/me", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(parseProfile(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "내정보 수정 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun uploadAvatar(
        token: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
    ): Result<ProfileData> {
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
                .url("$API_BASE_URL/users/me/avatar")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()
            val response = apiClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(parseProfile(JSONObject(body)))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "사진 올리기 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteMe(token: String): Result<String> {
        return try {
            val response = apiClient.newCall(authDelete("$API_BASE_URL/users/me", token)).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val message = try {
                    JSONObject(body).optString("message", "회원탈퇴가 완료되었습니다")
                } catch (_: Exception) {
                    "회원탈퇴가 완료되었습니다"
                }
                Result.success(message)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "회원탈퇴 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
