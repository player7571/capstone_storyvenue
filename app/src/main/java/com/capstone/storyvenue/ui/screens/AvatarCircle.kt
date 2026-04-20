package com.capstone.storyvenue.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

object AvatarCache {
    private val cache: MutableMap<String, ImageBitmap> =
        Collections.synchronizedMap(mutableMapOf())

    fun get(url: String): ImageBitmap? = cache[url]
    fun put(url: String, bitmap: ImageBitmap) {
        cache[url] = bitmap
    }
}

@Composable
fun AvatarCircle(
    name: String,
    avatarUrl: String?,
    size: Dp = 40.dp,
    fontSize: TextUnit = 14.sp,
    fallbackBackground: Color = StoryVenueColors.Primary,
) {
    var bitmap by remember(avatarUrl) {
        mutableStateOf(avatarUrl?.let { AvatarCache.get(it) })
    }

    LaunchedEffect(avatarUrl) {
        val url = avatarUrl ?: return@LaunchedEffect
        if (AvatarCache.get(url) != null) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { ApiService.fetchImageBytes(url) }
        result.onSuccess { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()?.let { decoded ->
                AvatarCache.put(url, decoded)
                bitmap = decoded
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fallbackBackground),
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = "$name 프로필 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.firstOrNull()?.toString() ?: "?",
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = SBAggroFamily,
            )
        }
    }
}
