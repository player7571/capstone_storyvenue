package com.capstone.storyvenue.ui.screens

import android.media.MediaPlayer

class AudioPlayerController {
    private var mediaPlayer: MediaPlayer? = null

    fun play(
        url: String,
        onPrepared: () -> Unit,
        onCompleted: () -> Unit,
        onError: () -> Unit,
    ) {
        stop()
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setDataSource(url)
            player.setOnPreparedListener {
                onPrepared()
                it.start()
            }
            player.setOnCompletionListener {
                onCompleted()
                it.release()
                mediaPlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                mediaPlayer = null
                onError()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            onError()
        }
    }

    fun stop() {
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
