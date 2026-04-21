package com.capstone.storyvenue.ui.screens

import org.json.JSONArray
import org.json.JSONObject

object NotificationApi {
    fun getNotifications(token: String, limit: Int = 30, offset: Int = 0): Result<List<NotificationItem>> {
        return try {
            val response = apiClient.newCall(
                authGet("$API_BASE_URL/notifications?limit=$limit&offset=$offset", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<NotificationItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        NotificationItem(
                            id = obj.getString("id"),
                            actorName = obj.optString("actor_name", "알 수 없음"),
                            actorAvatarUrl = obj.optCleanString("actor_avatar_url").ifBlank { null },
                            message = obj.optString("message", ""),
                            commentPreview = if (obj.isNull("comment_preview")) null
                            else "\"${obj.getString("comment_preview")}\"",
                            timeAgo = timeAgo(obj.optString("created_at", null)),
                            isRead = obj.optBoolean("is_read", false),
                            type = obj.optString("type", "comment"),
                            postId = obj.optCleanString("post_id").ifBlank { null },
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("알림 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUnreadCount(token: String): Result<Int> {
        return try {
            val response = apiClient.newCall(
                authGet("$API_BASE_URL/notifications/unread-count", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(JSONObject(body).optInt("count", 0))
            } else {
                Result.success(0)
            }
        } catch (_: Exception) {
            Result.success(0)
        }
    }

    fun deleteNotification(token: String, notificationId: String): Result<Unit> {
        return try {
            val response = apiClient.newCall(
                authDelete("$API_BASE_URL/notifications/$notificationId", token)
            ).execute()
            if (response.code == 204 || response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(response.body?.string() ?: "", "알림 삭제 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun markNotificationRead(token: String, notificationId: String): Result<Unit> {
        return try {
            val response = apiClient.newCall(
                authPut("$API_BASE_URL/notifications/$notificationId/read", token)
            ).execute()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("읽음 처리 실패"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
