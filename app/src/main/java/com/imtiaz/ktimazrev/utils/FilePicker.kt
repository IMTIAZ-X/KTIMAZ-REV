package com.imtiaz.ktimazrev.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileOutputStream

class FilePicker(private val activity: FragmentActivity) {

    private var onFilePicked: ((String?) -> Unit)? = null
    private var pickFileLauncher: ActivityResultLauncher<Array<String>>

    init {
        pickFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let {
                val filePath = getPathFromUri(activity, it)
                onFilePicked?.invoke(filePath)
            } ?: run {
                onFilePicked?.invoke(null)
            }
        }
    }

    fun pickFile(callback: (String?) -> Unit) {
        onFilePicked = callback
        // MIME types for common executable files: application/octet-stream for generic binary
        // You might need to add more specific types if targeting specific ELF variants.
        pickFileLauncher.launch(
            arrayOf(
                "application/octet-stream",
                "application/x-executable",
                "application/x-elf",
            ),
        )
    }

    private fun getPathFromUri(
        context: Context,
        uri: Uri,
    ): String? {
        // This method needs to copy the file to a temporary location
        // because mmap (used in native code) requires a direct file path,
        // and SAF URIs often don't provide one directly.
        val fileName = getFileName(context, uri) ?: return null
        val tempFile = File(context.cacheDir, fileName)

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileName(
        context: Context,
        uri: Uri,
    ): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }
}