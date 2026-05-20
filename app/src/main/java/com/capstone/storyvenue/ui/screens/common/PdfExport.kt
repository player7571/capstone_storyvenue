package com.capstone.storyvenue.ui.screens.common

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfExport {

    fun safeFileName(bookTitle: String): String {
        val trimmed = bookTitle.trim().ifBlank { "자서전" }
        val sanitized = trimmed.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
        val truncated = if (sanitized.length > 80) sanitized.substring(0, 80) else sanitized
        return "$truncated.pdf"
    }

    fun saveToDownloads(
        context: Context,
        pdfBytes: ByteArray,
        fileName: String,
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloadsViaMediaStore(context, pdfBytes, fileName)
        } else {
            saveToDownloadsLegacy(context, pdfBytes, fileName)
        }
    }

    private fun saveToDownloadsViaMediaStore(
        context: Context,
        pdfBytes: ByteArray,
        fileName: String,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("PDF 저장 위치를 만들지 못했어요.")

        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(pdfBytes)
            stream.flush()
        } ?: error("PDF를 저장하지 못했어요.")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveToDownloadsLegacy(
        context: Context,
        pdfBytes: ByteArray,
        fileName: String,
    ): Uri {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val file = uniqueFile(downloadsDir, fileName)
        FileOutputStream(file).use { it.write(pdfBytes) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val target = File(dir, fileName)
        if (!target.exists()) return target
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var counter = 1
        while (true) {
            val candidate = File(dir, "$base ($counter)$ext")
            if (!candidate.exists()) return candidate
            counter += 1
        }
    }

    fun cacheForShare(
        context: Context,
        pdfBytes: ByteArray,
        fileName: String,
    ): Uri {
        val dir = File(context.cacheDir, "pdf_exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(pdfBytes) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun shareToKakao(context: Context, uri: Uri, displayName: String) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension("pdf") ?: "application/pdf"

        val kakaoIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            setPackage("com.kakao.talk")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(kakaoIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "카카오톡이 설치돼 있지 않아요. 다른 앱으로 공유할게요.", Toast.LENGTH_SHORT).show()
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, displayName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "PDF 공유하기"))
        }
    }
}
