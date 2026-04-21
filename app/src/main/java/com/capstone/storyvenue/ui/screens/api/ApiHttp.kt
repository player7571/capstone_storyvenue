package com.capstone.storyvenue.ui.screens

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiHttp {
    const val BASE_URL = "http://10.0.2.2:8000"

    val client: OkHttpClient = OkHttpClient()

    val voiceClient: OkHttpClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    val JSON_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
    val DEFAULT_BINARY: MediaType = "application/octet-stream".toMediaType()

    fun mediaTypeOrDefault(contentType: String): MediaType {
        return contentType.toMediaTypeOrNull() ?: DEFAULT_BINARY
    }
}
