package com.capstone.storyvenue.ui.screens

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class KakaoAuthorizeData(
    val authorizeUrl: String,
    val redirectUri: String,
    val state: String,
)

data class AuthSessionData(
    val accessToken: String,
    val refreshToken: String? = null,
    val userId: String,
    val name: String = "",
    val email: String = "",
)

object AuthApi {
    private fun toEmulatorReachableBackendUrl(url: String): String {
        val parsedUrl = url.toHttpUrlOrNull() ?: return url
        val apiBaseUrl = API_BASE_URL.toHttpUrlOrNull() ?: return url
        val isLocalBackendUrl = parsedUrl.port == apiBaseUrl.port &&
            (parsedUrl.host == "127.0.0.1" || parsedUrl.host == "localhost")

        return if (isLocalBackendUrl) {
            parsedUrl.newBuilder()
                .scheme(apiBaseUrl.scheme)
                .host(apiBaseUrl.host)
                .port(apiBaseUrl.port)
                .build()
                .toString()
        } else {
            url
        }
    }

    fun getKakaoAuthorizeUrl(): Result<KakaoAuthorizeData> {
        return try {
            val request = Request.Builder()
                .url("$API_BASE_URL/auth/kakao/authorize-url")
                .get()
                .build()

            val response = apiClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = parseErrorMessage(responseBody, "카카오 로그인 URL 조회에 실패했습니다")
                return Result.failure(Exception(detail))
            }

            val json = try {
                JSONObject(responseBody)
            } catch (_: Exception) {
                return Result.failure(Exception("카카오 로그인 URL 응답 형식이 올바르지 않습니다."))
            }

            val authorizeUrl = toEmulatorReachableBackendUrl(json.optString("authorize_url", "").trim())
            if (authorizeUrl.isBlank()) {
                return Result.failure(Exception("카카오 로그인 URL이 비어 있습니다."))
            }

            val parsedAuthorizeUrl = authorizeUrl.toHttpUrlOrNull()
            val redirectUri = parsedAuthorizeUrl
                ?.queryParameter("redirect_uri")
                ?.trim()
                .orEmpty()
            if (redirectUri.isBlank()) {
                return Result.failure(Exception("카카오 redirect URI를 확인할 수 없습니다."))
            }
            val state = json.optString("state", "").trim()
                .ifBlank { parsedAuthorizeUrl?.queryParameter("state")?.trim().orEmpty() }
            if (state.isBlank()) {
                return Result.failure(Exception("카카오 인증 state 값을 확인할 수 없습니다."))
            }

            Result.success(
                KakaoAuthorizeData(
                    authorizeUrl = authorizeUrl,
                    redirectUri = redirectUri,
                    state = state,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loginWithKakaoCode(code: String, state: String): Result<AuthSessionData> {
        return try {
            val body = JSONObject().apply {
                put("code", code)
                put("state", state)
            }.toString().toRequestBody(apiJsonType)

            val request = Request.Builder()
                .url("$API_BASE_URL/auth/kakao/login")
                .post(body)
                .build()

            val response = apiClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = parseErrorMessage(responseBody, "카카오 로그인에 실패했습니다")
                return Result.failure(Exception(detail))
            }

            val json = try {
                JSONObject(responseBody)
            } catch (_: Exception) {
                return Result.failure(Exception("카카오 로그인 응답 형식이 올바르지 않습니다."))
            }

            val accessToken = json.optString("access_token", "").trim()
            val refreshToken = json.optString("refresh_token", "").trim()
            val userId = json.optString("user_id", "").trim()
            val name = json.optString("name", "").trim()
            val email = json.optString("email", "").trim()
            if (accessToken.isBlank() || userId.isBlank()) {
                return Result.failure(Exception("카카오 로그인 토큰 정보가 올바르지 않습니다."))
            }

            Result.success(
                AuthSessionData(
                    accessToken = accessToken,
                    refreshToken = refreshToken.ifBlank { null },
                    userId = userId,
                    name = name,
                    email = email,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
