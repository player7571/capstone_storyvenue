package com.capstone.storyvenue.ui.screens

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

object FeedApi {
    private fun parseFeedPost(obj: JSONObject): FeedPost = FeedPost(
        id = obj.getString("id"),
        bookId = obj.optCleanString("book_id").ifBlank { null },
        authorId = obj.optString("user_id", ""),
        authorName = obj.optString("author_name", "익명"),
        authorAvatarUrl = obj.optCleanString("author_avatar_url").ifBlank { null },
        title = obj.optString("title", ""),
        preview = obj.optString("preview", ""),
        likeCount = obj.optInt("like_count", 0),
        commentCount = obj.optInt("comment_count", 0),
        timeAgo = timeAgo(obj.optString("created_at", null)),
        likedByMe = obj.optBoolean("liked_by_me", false),
    )

    private fun fetchFeedList(url: String, token: String): Result<List<FeedPost>> {
        return try {
            val response = apiClient.newCall(authGet(url, token)).execute()
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

    fun createFeedPost(
        token: String,
        bookId: String,
        title: String,
        preview: String,
    ): Result<FeedPost> {
        return try {
            val payload = JSONObject().apply {
                put("book_id", bookId)
                put("title", title)
                put("preview", preview)
            }.toString()
            val response = apiClient.newCall(
                authPost("$API_BASE_URL/feed", token, payload)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(parseFeedPost(JSONObject(body)))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "게시 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createChapterFeedPost(
        token: String,
        chapterId: String,
    ): Result<FeedPost> {
        return try {
            val response = apiClient.newCall(
                authPost("$API_BASE_URL/feed/chapter/$chapterId", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(parseFeedPost(JSONObject(body)))
            } else {
                Result.failure(Exception(parseErrorMessage(body, "초안 게시 실패")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFeed(token: String, limit: Int = 20, offset: Int = 0, query: String = ""): Result<List<FeedPost>> {
        val trimmedQuery = query.trim()
        val searchParam = if (trimmedQuery.isBlank()) {
            ""
        } else {
            "&q=${URLEncoder.encode(trimmedQuery, "UTF-8")}"
        }
        return fetchFeedList("$API_BASE_URL/feed?limit=$limit&offset=$offset$searchParam", token)
    }

    fun getMyFeed(token: String, limit: Int = 20, offset: Int = 0): Result<List<FeedPost>> =
        fetchFeedList("$API_BASE_URL/feed/me?limit=$limit&offset=$offset", token)

    fun getLikedFeed(token: String, limit: Int = 20, offset: Int = 0): Result<List<FeedPost>> =
        fetchFeedList("$API_BASE_URL/feed/liked?limit=$limit&offset=$offset", token)

    fun getFeedDetail(token: String, postId: String): Result<FeedPost> {
        return try {
            val response = apiClient.newCall(
                authGet("$API_BASE_URL/feed/$postId", token)
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
            val response = apiClient.newCall(
                authPost("$API_BASE_URL/feed/$postId/like", token)
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

    fun getComments(token: String, postId: String): Result<List<ApiService.CommentData>> {
        return try {
            val response = apiClient.newCall(
                authGet("$API_BASE_URL/feed/$postId/comments", token)
            ).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val arr = JSONArray(body)
                val list = mutableListOf<ApiService.CommentData>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        ApiService.CommentData(
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

    fun createComment(token: String, postId: String, content: String): Result<ApiService.CommentData> {
        return try {
            val jsonBody = JSONObject().apply { put("content", content) }.toString()
            val response = apiClient.newCall(
                authPost("$API_BASE_URL/feed/$postId/comments", token, jsonBody)
            ).execute()
            val body = response.body?.string() ?: ""
            val obj = JSONObject(body)
            if (response.isSuccessful) {
                Result.success(
                    ApiService.CommentData(
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
            val response = apiClient.newCall(
                authDelete("$API_BASE_URL/comments/$commentId", token)
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
}
