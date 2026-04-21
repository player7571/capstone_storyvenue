package com.capstone.storyvenue.ui.screens

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class AudioRecorderController {
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFilePath: String? = null

    fun start(context: Context): Result<File> {
        val outputDir = File(context.cacheDir, "voice-recordings").apply { mkdirs() }
        val outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.m4a")

        return try {
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recordingFilePath = outputFile.absolutePath
            Result.success(outputFile)
        } catch (e: Exception) {
            stop(deleteTempFile = true)
            Result.failure(e)
        }
    }

    fun stop(deleteTempFile: Boolean = false): File? {
        mediaRecorder?.runCatching { stop() }
        mediaRecorder?.release()
        mediaRecorder = null

        val file = recordingFilePath?.let { File(it) }
        if (deleteTempFile) {
            file?.runCatching { delete() }
            recordingFilePath = null
            return null
        }
        recordingFilePath = null
        return file
    }

    fun release(deleteTempFile: Boolean = true) {
        stop(deleteTempFile = deleteTempFile)
    }
}
