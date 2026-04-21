package com.capstone.storyvenue.ui.screens

import org.json.JSONArray
import org.json.JSONObject

object ChatApi {
    fun getChatPartners(token: String): Result<List<ChatPartner>> {
        return try {
            val response = apiClient.newCall(authGet("$API_BASE_URL/chat", token)).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<ChatPartner>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ChatPartner(
                            userId = obj.getString("user_id"),
                            userName = obj.optString("name", obj.optString("display_name", "익명")),
                            avatarUrl = obj.optCleanString("avatar_url").ifBlank { null },
                            lastMessage = obj.optString("last_message", ""),
                            lastMessageTime = timeAgo(obj.optString("last_message_at", null)),
                            unreadCount = obj.optInt("unread_count", 0),
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("대화 목록 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMessages(token: String, otherUserId: String, limit: Int = 50): Result<List<ChatMessageData>> {
        return try {
            val response = apiClient.newCall(
                authGet("$API_BASE_URL/chat/$otherUserId/messages?limit=$limit", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<ChatMessageData>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ChatMessageData(
                            id = obj.getString("id"),
                            senderId = obj.getString("sender_id"),
                            content = obj.optString("content", ""),
                            timeAgo = timeAgo(obj.optString("created_at", null)),
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("쪽지 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendMessage(token: String, otherUserId: String, content: String): Result<ChatMessageData> {
        return try {
            val jsonBody = JSONObject().apply { put("content", content) }.toString()
            val response = apiClient.newCall(
                authPost("$API_BASE_URL/chat/$otherUserId/messages", token, jsonBody)
            ).execute()
            val body = response.body?.string() ?: ""
            val obj = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(
                    ChatMessageData(
                        id = obj.getString("id"),
                        senderId = obj.getString("sender_id"),
                        content = obj.optString("content", ""),
                        timeAgo = "방금",
                    )
                )
            } else {
                Result.failure(Exception(obj.optString("detail", "쪽지 전송 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
