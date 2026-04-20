package com.capstone.storyvenue.ui.screens

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ApiService {

    private const val BASE_URL = "http://10.0.2.2:8000"
    private val client = OkHttpClient()
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val DEFAULT_BINARY = "application/octet-stream".toMediaType()

    // ── 시간 포맷 헬퍼 ──────────────────────────────
    private fun timeAgo(isoString: String?): String {
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

    // ── 인증 요청 빌더 ──────────────────────────────
    private fun authGet(url: String, token: String): Request =
        Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .get().build()

    private fun authPost(url: String, token: String, body: String = "{}"): Request =
        Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body.toRequestBody(JSON_TYPE)).build()

    private fun authPut(url: String, token: String, body: String = "{}"): Request =
        Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .put(body.toRequestBody(JSON_TYPE)).build()

    private fun authDelete(url: String, token: String): Request =
        Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .delete().build()

    private fun parseErrorMessage(body: String, fallback: String): String {
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

    private fun JSONObject.optCleanString(name: String): String {
        if (!has(name) || isNull(name)) return ""
        return optString(name, "").takeUnless { it == "null" } ?: ""
    }

    private fun toAbsoluteUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
        return if (pathOrUrl.startsWith("/")) "$BASE_URL$pathOrUrl" else "$BASE_URL/$pathOrUrl"
    }

    // ── Auth ─────────────────────────────────────────
    fun login(email: String, password: String): Result<Pair<String, String>> {
        return try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString().toRequestBody(JSON_TYPE)

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
            }.toString().toRequestBody(JSON_TYPE)

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
                val detail = parseErrorMessage(responseBody, "회원가입에 실패했습니다")
                Result.failure(Exception(detail))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Profile ──────────────────────────────────────
    private fun parseProfile(json: JSONObject) = ProfileData(
        id = json.getString("id"),
        name = json.optCleanString("name"),
        email = json.optCleanString("email"),
        avatarUrl = json.optCleanString("avatar_url").ifBlank { null },
    )

    fun getProfile(token: String): Result<ProfileData> {
        return try {
            val response = client.newCall(authGet("$BASE_URL/users/me", token)).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(parseProfile(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "프로필 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfileName(token: String, name: String): Result<ProfileData> {
        return try {
            val payload = JSONObject().apply { put("name", name) }.toString()
            val response = client.newCall(
                authPut("$BASE_URL/users/me", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(parseProfile(json))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "프로필 수정 실패")))
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
            val mediaType = contentType.toMediaTypeOrNull() ?: DEFAULT_BINARY
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file",
                    fileName,
                    imageBytes.toRequestBody(mediaType),
                )
                .build()
            val request = Request.Builder()
                .url("$BASE_URL/users/me/avatar")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(parseProfile(JSONObject(body)))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "프로필 사진 업로드 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteMe(token: String): Result<String> {
        return try {
            val response = client.newCall(authDelete("$BASE_URL/users/me", token)).execute()
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

    // ── Sessions ─────────────────────────────────────
    fun createSession(token: String, title: String, theme: String): Result<SessionData> {
        return try {
            val payload = JSONObject().apply {
                put("title", title)
                put("theme", theme)
            }.toString()
            val response = client.newCall(
                authPost("$BASE_URL/sessions", token, payload)
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
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "세션 생성 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSessions(token: String): Result<List<InterviewSession>> {
        return try {
            val response = client.newCall(authGet("$BASE_URL/sessions", token)).execute()
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
                            title = "\"${obj.optString("title", "인터뷰")}\"",
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "세션 목록 조회 실패")))
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
            val mediaType = contentType.toMediaTypeOrNull() ?: DEFAULT_BINARY
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file",
                    fileName,
                    imageBytes.toRequestBody(mediaType),
                )
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/sessions/photo")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
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
                Result.failure(Exception(parseErrorMessage(body, "사진 인터뷰 시작에 실패했습니다")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSessionDetail(token: String, sessionId: String): Result<SessionDetailData> {
        return try {
            val response = client.newCall(
                authGet("$BASE_URL/sessions/$sessionId", token)
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
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "세션 상세 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchImageBytes(url: String): Result<ByteArray> {
        return try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
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

    fun voiceTurn(
        token: String,
        sessionId: String,
        audioBytes: ByteArray,
        fileName: String = "recording.m4a",
        contentType: String = "audio/m4a",
    ): Result<VoiceTurnData> {
        return try {
            val mediaType = contentType.toMediaTypeOrNull() ?: DEFAULT_BINARY
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
                .url("$BASE_URL/voice/turn")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)

            if (response.isSuccessful) {
                val audioUrl = json.optString("audio_url", "")
                Result.success(
                    VoiceTurnData(
                        userText = json.optString("user_text", ""),
                        assistantText = json.optString("assistant_text", ""),
                        audioUrl = toAbsoluteUrl(audioUrl),
                    )
                )
            } else {
                Result.failure(Exception(parseErrorMessage(body, "음성 인터뷰 처리 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Feed ─────────────────────────────────────────
    private fun parseFeedPost(obj: JSONObject): FeedPost = FeedPost(
        id = obj.getString("id"),
        authorName = obj.optString("author_name", "익명"),
        authorAvatarUrl = obj.optCleanString("author_avatar_url").ifBlank { null },
        title = obj.optString("title", ""),
        preview = obj.optString("preview", ""),
        likeCount = obj.optInt("like_count", 0),
        commentCount = obj.optInt("comment_count", 0),
        timeAgo = timeAgo(obj.optString("created_at", null)),
        likedByMe = obj.optBoolean("liked_by_me", false),
    )

    fun getFeed(token: String, limit: Int = 20, offset: Int = 0): Result<List<FeedPost>> {
        return fetchFeedList("$BASE_URL/feed?limit=$limit&offset=$offset", token)
    }

    fun getMyFeed(token: String, limit: Int = 20, offset: Int = 0): Result<List<FeedPost>> {
        return fetchFeedList("$BASE_URL/feed/me?limit=$limit&offset=$offset", token)
    }

    fun getLikedFeed(token: String, limit: Int = 20, offset: Int = 0): Result<List<FeedPost>> {
        return fetchFeedList("$BASE_URL/feed/liked?limit=$limit&offset=$offset", token)
    }

    private fun fetchFeedList(url: String, token: String): Result<List<FeedPost>> {
        return try {
            val response = client.newCall(authGet(url, token)).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<FeedPost>()
                for (i in 0 until arr.length()) {
                    list.add(parseFeedPost(arr.getJSONObject(i)))
                }
                Result.success(list)
            } else {
                Result.failure(Exception(parseErrorMessage(body, "게시물 조회 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFeedDetail(token: String, postId: String): Result<FeedPost> {
        return try {
            val response = client.newCall(
                authGet("$BASE_URL/feed/$postId", token)
            ).execute()
            val body = response.body?.string() ?: ""
            val obj = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(parseFeedPost(obj))
            } else {
                Result.failure(Exception("게시물 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun toggleLike(token: String, postId: String): Result<Pair<Boolean, Int>> {
        return try {
            val response = client.newCall(
                authPost("$BASE_URL/feed/$postId/like", token)
            ).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(Pair(json.getBoolean("liked"), json.getInt("like_count")))
            } else {
                Result.failure(Exception("좋아요 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Comments ─────────────────────────────────────
    data class CommentData(
        val id: String,
        val authorName: String,
        val authorAvatarUrl: String? = null,
        val content: String,
        val timeAgo: String,
    )

    fun getComments(token: String, postId: String): Result<List<CommentData>> {
        return try {
            val response = client.newCall(
                authGet("$BASE_URL/feed/$postId/comments", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<CommentData>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        CommentData(
                            id = obj.getString("id"),
                            authorName = obj.optString("author_name", "익명"),
                            authorAvatarUrl = obj.optCleanString("author_avatar_url").ifBlank { null },
                            content = obj.optString("content", ""),
                            timeAgo = timeAgo(obj.optString("created_at", null)),
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("댓글 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createComment(token: String, postId: String, content: String): Result<CommentData> {
        return try {
            val jsonBody = JSONObject().apply { put("content", content) }.toString()
            val response = client.newCall(
                authPost("$BASE_URL/feed/$postId/comments", token, jsonBody)
            ).execute()
            val body = response.body?.string() ?: ""
            val obj = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(
                    CommentData(
                        id = obj.getString("id"),
                        authorName = obj.optString("author_name", "나"),
                        authorAvatarUrl = obj.optCleanString("author_avatar_url").ifBlank { null },
                        content = obj.optString("content", ""),
                        timeAgo = "방금",
                    )
                )
            } else {
                Result.failure(Exception(obj.optString("detail", "댓글 작성 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteComment(token: String, commentId: String): Result<Unit> {
        return try {
            val response = client.newCall(
                authDelete("$BASE_URL/comments/$commentId", token)
            ).execute()
            if (response.code == 204 || response.isSuccessful) {
                Result.success(Unit)
            } else {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                Result.failure(Exception(json.optString("detail", "댓글 삭제 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Notifications ────────────────────────────────
    fun getNotifications(token: String, limit: Int = 30, offset: Int = 0): Result<List<NotificationItem>> {
        return try {
            val response = client.newCall(
                authGet("$BASE_URL/notifications?limit=$limit&offset=$offset", token)
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
            val response = client.newCall(
                authGet("$BASE_URL/notifications/unread-count", token)
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

    fun markNotificationRead(token: String, notificationId: String): Result<Unit> {
        return try {
            val response = client.newCall(
                authPut("$BASE_URL/notifications/$notificationId/read", token)
            ).execute()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("읽음 처리 실패"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Chat ─────────────────────────────────────────
    fun getChatPartners(token: String): Result<List<ChatPartner>> {
        return try {
            val response = client.newCall(authGet("$BASE_URL/chat", token)).execute()
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
                Result.failure(Exception("채팅 목록 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMessages(token: String, otherUserId: String, limit: Int = 50): Result<List<ChatMessageData>> {
        return try {
            val response = client.newCall(
                authGet("$BASE_URL/chat/$otherUserId/messages?limit=$limit", token)
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
                Result.failure(Exception("메시지 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendMessage(token: String, otherUserId: String, content: String): Result<ChatMessageData> {
        return try {
            val jsonBody = JSONObject().apply { put("content", content) }.toString()
            val response = client.newCall(
                authPost("$BASE_URL/chat/$otherUserId/messages", token, jsonBody)
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
                Result.failure(Exception(obj.optString("detail", "메시지 전송 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ── 공통 데이터 클래스 ───────────────────────────────
data class ProfileData(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
)

data class SessionData(
    val id: String,
    val title: String,
    val theme: String,
    val status: String,
    val createdAt: String,
)

data class VoiceTurnData(
    val userText: String,
    val assistantText: String,
    val audioUrl: String,
)

data class PhotoSessionStartData(
    val sessionId: String,
    val photoUrl: String,
    val aiMessage: String,
    val createdAt: String,
)

data class SessionDetailData(
    val id: String,
    val sessionType: String,
    val photoUrl: String?,
    val status: String,
)

data class ChatMessageData(
    val id: String,
    val senderId: String,
    val content: String,
    val timeAgo: String,
)
