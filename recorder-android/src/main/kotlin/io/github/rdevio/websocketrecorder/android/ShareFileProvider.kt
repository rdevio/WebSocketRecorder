package io.github.rdevio.websocketrecorder.android

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

internal class ShareFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "text/plain"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Only read access is supported")
        val file = resolveFile(uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = resolveFile(uri)
        val requestedColumns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
        )
        return MatrixCursor(requestedColumns).apply {
            addRow(
                requestedColumns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> file.name
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    }
                },
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read only")

    private fun resolveFile(uri: Uri): File {
        val providerContext = context ?: throw FileNotFoundException("Provider unavailable")
        val name = uri.lastPathSegment
            ?.takeIf { it.endsWith(".txt") && File(it).name == it }
            ?: throw FileNotFoundException("Invalid file")
        val shareDirectory = File(providerContext.cacheDir, SHARE_DIRECTORY).canonicalFile
        val file = File(shareDirectory, name).canonicalFile
        if (file.parentFile != shareDirectory || !file.isFile) {
            throw FileNotFoundException("File not found")
        }
        return file
    }

    companion object {
        private const val SHARE_DIRECTORY = "websocket-recorder-share"

        fun uriFor(context: Context, file: File): Uri =
            Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.websocketrecorder.fileprovider")
                .appendPath(file.name)
                .build()
    }
}
