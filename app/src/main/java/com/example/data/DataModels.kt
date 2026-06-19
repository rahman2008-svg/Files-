package com.example.data

import android.os.Environment
import java.io.File

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, DOWNLOAD, APP, FOLDER, OTHER
}

data class FileItem(
    val id: String, // Dynamic identifier: path or URL
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val fileType: FileType,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false
) {
    val sizeString: String get() = formatSize(size)
}

data class StorageSpaceInfo(
    val totalSpaceBytes: Long,
    val freeSpaceBytes: Long,
    val usedSpaceBytes: Long,
    val usedPercentage: Float
) {
    val totalSpaceString: String get() = formatSize(totalSpaceBytes)
    val freeSpaceString: String get() = formatSize(freeSpaceBytes)
    val usedSpaceString: String get() = formatSize(usedSpaceBytes)
}

data class CleanupCardState(
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val files: List<FileItem>,
    val type: CleanupType
) {
    val sizeString: String get() = formatSize(sizeBytes)
}

enum class CleanupType {
    JUNK, DUPLICATES, LARGE_FILES, OLD_SCREENSHOTS
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups < 0 || digitGroups >= units.size) return "$size B"
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
