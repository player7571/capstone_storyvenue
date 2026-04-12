package com.capstone.storyvenue.ui.screens.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ApiService {

    private const val BASE_URL = "http://10.0.2.2:8000"
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 로그인
    fun login(email: String, password: String): Result<Pair<String, String>> {
        return try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString().toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/auth/login")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)

            if (response.isSuccessful) {
                val token = json.getString("access_token")
                val userId = json.getString("user_id")
                Result.success(Pair(token, userId))
            } else {
                val detail = json.optString("detail", "로그인에 실패했습니다")
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 회원가입
    fun signup(name: String, email: String, password: String): Result<String> {
        return try {
            val body = JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", password)
            }.toString().toRequestBody(JSON)

            val request = Request.Builder()
                .url("$BASE_URL/auth/signup")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)

            if (response.isSuccessful) {
                Result.success(json.optString("message", "회원가입 성공"))
            } else {
                val detail = json.optString("detail", "회원가입에 실패했습니다")
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}