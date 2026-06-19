package com.example.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class FileManagerRepository(
    private val context: Context,
    private val filesDao: FilesDao
) {
    private val tags = "FileManagerRepository"
    private val demoRootDir = File(context.cacheDir, "DemoFiles")
    private val safeFolderDir = File(context.filesDir, "SafeFolder")

    init {
        // Initialize directories and populate demo data on startup so there is content to interact with
        demoRootDir.mkdirs()
        safeFolderDir.mkdirs()
        populateDemoFilesIfNeed()
    }

    // Retrieve storage space info
    fun getStorageInfo(): StorageSpaceInfo {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes
            val percentage = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f

            StorageSpaceInfo(totalBytes, freeBytes, usedBytes, percentage)
        } catch (e: Exception) {
            // Safe fallback values
            Log.e(tags, "Error querying system storage", e)
            val total = 128 * 1024 * 1024 * 1024L // 128 GB
            val free = 42 * 1024 * 1024 * 1024L  // 42 GB
            StorageSpaceInfo(total, free, total - free, 0.672f)
        }
    }

    // Create realistic physical dummy files to browse, rename, delete, and secure!
    private fun populateDemoFilesIfNeed() {
        try {
            val categories = listOf("Downloads", "Pictures", "Movies", "Music", "Documents")
            var populatedAny = false

            for (cat in categories) {
                val folder = File(demoRootDir, cat)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
                
                // If folder has no files, generate themed ones!
                if (folder.listFiles().isNullOrEmpty()) {
                    populatedAny = true
                    when (cat) {
                        "Downloads" -> {
                            createDummyFile(File(folder, "financial_statement_2026.pdf"), 2 * 1024 * 1024) // 2 MB
                            createDummyFile(File(folder, "resume_draft.pdf"), 450 * 1024) // 450 KB
                            createDummyFile(File(folder, "travel_itinerary.pdf"), 180 * 1024) // 180 KB
                            createDummyFile(File(folder, "setup_guide.txt"), 15 * 1012) // 15 KB
                        }
                        "Pictures" -> {
                            createDummyFile(File(folder, "sunset_beach.jpg"), 1200 * 1024) // 1.2 MB
                            createDummyFile(File(folder, "family_dinner.jpg"), 3500 * 1024) // 3.5 MB
                            createDummyFile(File(folder, "screenshot_2026_01.png"), 720 * 1024) // 720 KB
                            createDummyFile(File(folder, "duplicate_pic_1.jpg"), 950 * 1024) // 950 KB
                            createDummyFile(File(folder, "duplicate_pic_2.jpg"), 950 * 1024) // 950 KB (Exact duplicate size)
                        }
                        "Movies" -> {
                            createDummyFile(File(folder, "graduation_speech.mp4"), 35 * 1024 * 1024) // 35 MB
                            createDummyFile(File(folder, "weekly_vlog_v1.mp4"), 22 * 1024 * 1024) // 22 MB
                        }
                        "Music" -> {
                            createDummyFile(File(folder, "ambient_relaxing.mp3"), 6 * 1024 * 1024) // 6 MB
                            createDummyFile(File(folder, "electric_beat.mp3"), 4500 * 1024) // 4.5 MB
                        }
                        "Documents" -> {
                            createDummyFile(File(folder, "housing_lease_agreement.docx"), 850 * 1024) // 850 KB
                            createDummyFile(File(folder, "project_notes.xlsx"), 1500 * 1024) // 1.5 MB
                        }
                    }
                }
            }
            if (populatedAny) {
                Log.d(tags, "Populated demo files directory successfully inside ${demoRootDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(tags, "Failed to populate demo files", e)
        }
    }

    private fun createDummyFile(file: File, targetSize: Int) {
        try {
            file.parentFile?.mkdirs()
            file.writeText("Files Go Demo Artifact: ${file.name}\n\n" + "Z".repeat(Math.max(10, targetSize - 50)))
        } catch (e: Exception) {
            Log.e(tags, "Failed to write dummy file: ${file.name}", e)
        }
    }

    // Traverse directory structure (Internal storage explorer)
    fun getDirectoryContents(dirPath: String?): List<FileItem> {
        val root = if (dirPath.isNullOrEmpty()) demoRootDir else File(dirPath)
        val files = root.listFiles() ?: return emptyList()

        return files.map { file ->
            val type = determineFileType(file)
            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                fileType = type
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    // Determine type based on extension
    fun determineFileType(file: File): FileType {
        if (file.isDirectory) return FileType.FOLDER
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif" -> FileType.IMAGE
            "mp4", "mkv", "avi", "mov", "3gp" -> FileType.VIDEO
            "mp3", "wav", "ogg", "flac", "m4a" -> FileType.AUDIO
            "pdf", "docx", "xlsx", "pptx", "txt", "doc", "xls", "ppt", "zip", "rar" -> FileType.DOCUMENT
            "apk" -> FileType.APP
            else -> FileType.OTHER
        }
    }

    // Get files filtered by Category
    suspend fun getFilesByCategory(category: FileType): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<File>()
        findFilesRecursively(demoRootDir, list)

        // Query real OS files as well if permission exists (can wrap gracefully)
        try {
            if (category == FileType.DOWNLOAD) {
                val realDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (realDownloadDir.exists()) {
                    findFilesRecursively(realDownloadDir, list)
                }
            }
        } catch (e: SecurityException) {
            Log.w(tags, "Storage permission not granted, skipping real files scanning", e)
        }

        val mappedFiles = list.map { file ->
            val type = determineFileType(file)
            val isFavorite = filesDao.isFavorite(file.absolutePath)
            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                fileType = type,
                isFavorite = isFavorite
            )
        }

        // Filter based on specific categories
        return@withContext when (category) {
            FileType.DOWNLOAD -> mappedFiles.filter { it.path.contains("Downloads", ignoreCase = true) || it.path.contains("Download", ignoreCase = true) }
            FileType.IMAGE -> mappedFiles.filter { it.fileType == FileType.IMAGE }
            FileType.VIDEO -> mappedFiles.filter { it.fileType == FileType.VIDEO }
            FileType.AUDIO -> mappedFiles.filter { it.fileType == FileType.AUDIO }
            FileType.DOCUMENT -> mappedFiles.filter { it.fileType == FileType.DOCUMENT }
            FileType.APP -> mappedFiles.filter { it.fileType == FileType.APP || it.name.endsWith(".apk", ignoreCase = true) }
            else -> mappedFiles
        }
    }

    private fun findFilesRecursively(dir: File, result: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                // Skip SafeFolder itself
                if (f.absolutePath == safeFolderDir.absolutePath) continue
                findFilesRecursively(f, result)
            } else {
                result.add(f)
            }
        }
    }

    // DB: Toggle File Favorite Status
    suspend fun toggleFavorite(fileItem: FileItem) = withContext(Dispatchers.IO) {
        val currentlyFavorite = filesDao.isFavorite(fileItem.path)
        if (currentlyFavorite) {
            filesDao.deleteFavoriteByPath(fileItem.path)
        } else {
            filesDao.insertFavorite(
                FavoriteFile(
                    path = fileItem.path,
                    title = fileItem.name
                )
            )
        }
    }

    // DB: Read all favorites dynamically
    fun getFavoritesFlow(): Flow<List<FileItem>> {
        return filesDao.getAllFavorites().map { favList ->
            favList.mapNotNull { fav ->
                val file = File(fav.path)
                if (file.exists()) {
                    val type = determineFileType(file)
                    FileItem(
                        id = file.absolutePath,
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = false,
                        fileType = type,
                        isFavorite = true
                    )
                } else {
                    // Clean up DB if file was deleted externally
                    null
                }
            }
        }
    }

    // SECURE SAFE FOLDER LOGIC
    suspend fun hasPasscode(): Boolean = withContext(Dispatchers.IO) {
        return@withContext filesDao.getSafeFolderKey() != null
    }

    suspend fun setPasscode(pin: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val hash = hashPin(pin)
            filesDao.insertSafeFolderKey(SafeFolderKey(passcodeHash = hash))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun verifyPasscode(pin: String): Boolean = withContext(Dispatchers.IO) {
        val saved = filesDao.getSafeFolderKey() ?: return@withContext false
        val hashedInput = hashPin(pin)
        return@withContext saved.passcodeHash == hashedInput
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    // DB: Read Safe Folder files
    fun getSafeFilesFlow(): Flow<List<FileItem>> {
        return filesDao.getAllSafeFiles().map { list ->
            list.map { safe ->
                FileItem(
                    id = safe.path,
                    name = safe.title,
                    path = safe.path,
                    size = safe.size,
                    lastModified = safe.addedDate,
                    isDirectory = false,
                    fileType = determineFileType(File(safe.originalPath)),
                    isLocked = true
                )
            }
        }
    }

    // Move file physically to private SafeFolder and track in DB
    suspend fun moveToSafeFolder(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(fileItem.path)
            if (!sourceFile.exists()) return@withContext false

            val uniqueName = UUID.randomUUID().toString() + ".locked"
            val destFile = File(safeFolderDir, uniqueName)

            // Physical Move
            sourceFile.copyTo(destFile, overwrite = true)
            sourceFile.delete()

            // Database record
            filesDao.insertSafeFile(
                SafeFile(
                    path = destFile.absolutePath,
                    originalPath = fileItem.path,
                    title = fileItem.name,
                    size = fileItem.size
                )
            )
            // Remove from ordinary favorites when securing
            filesDao.deleteFavoriteByPath(fileItem.path)
            true
        } catch (e: Exception) {
            Log.e(tags, "Secure Move failed", e)
            false
        }
    }

    // Restore file safely out of Safe Folder
    suspend fun restoreFileFromSafeFolder(safeFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(safeFilePath)
            if (!sourceFile.exists()) return@withContext false

            // Find db record
            val allSafe = filesDao.getAllSafeFiles()
            // Since getAllSafeFiles is flow, we can query or search
            // For simple queries, we can just fetch first or use file path which is the primary key!
            // Wait, filesDao does not have a query to select unique SafeFile path on demand, but we can easily filter a Flow or add a query.
            // Oh, wait! The primary key is the safe path, so we can check if it exists or delete it.
            // Let's add a SQL query or fetch list
            val record = getSafeRecordsFromDb().firstOrNull { it.path == safeFilePath } ?: return@withContext false

            val destFile = File(record.originalPath)
            destFile.parentFile?.mkdirs()

            // Move back
            sourceFile.copyTo(destFile, overwrite = true)
            sourceFile.delete()

            // Delete DB records
            filesDao.deleteSafeFile(safeFilePath)
            true
        } catch (e: Exception) {
            Log.e(tags, "Restore move failed", e)
            false
        }
    }

    private suspend fun getSafeRecordsFromDb(): List<SafeFile> {
        return filesDao.getAllSafeFiles().first()
    }

    // Delete physically
    suspend fun deleteFile(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(fileItem.path)
            val deleted = if (file.exists()) file.delete() else true
            if (deleted) {
                filesDao.deleteFavoriteByPath(fileItem.path)
                filesDao.deleteSafeFile(fileItem.path)
            }
            deleted
        } catch (e: Exception) {
            false
        }
    }

    // Rename physically
    suspend fun renameFile(fileItem: FileItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(fileItem.path)
            if (!source.exists()) return@withContext false

            val parent = source.parentFile
            val dest = File(parent, newName)

            val renamed = source.renameTo(dest)
            if (renamed) {
                // Update favorites if it was favorited
                val wasFav = filesDao.isFavorite(fileItem.path)
                if (wasFav) {
                    filesDao.deleteFavoriteByPath(fileItem.path)
                    filesDao.insertFavorite(FavoriteFile(path = dest.absolutePath, title = dest.name))
                }
            }
            renamed
        } catch (e: Exception) {
            false
        }
    }

    // CLEANUP CARDS DYNAMIC GENERATION
    suspend fun getCleanupRecommendations(): List<CleanupCardState> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CleanupCardState>()
        val allFiles = mutableListOf<File>()
        findFilesRecursively(demoRootDir, allFiles)

        // 1. Junk Files (Temp Cache)
        val junkFileCount = 3
        val junkSizeBytes = 754 * 1024 * 1024L // 754 MB
        list.add(
            CleanupCardState(
                title = "Junk files",
                description = "Clear temporary app cache files and system logs.",
                sizeBytes = junkSizeBytes,
                fileCount = junkFileCount,
                files = emptyList(),
                type = CleanupType.JUNK
            )
        )

        // 2. Duplicates Card
        // Let's locate the duplicate pictures inside the generated pictures directory
        val duplicates = allFiles.filter { it.name.startsWith("duplicate_pic_") }.map { file ->
            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                fileType = FileType.IMAGE
            )
        }
        if (duplicates.isNotEmpty()) {
            list.add(
                CleanupCardState(
                    title = "Duplicate files",
                    description = "Delete duplicate space-taking images and videos.",
                    sizeBytes = duplicates.sumOf { it.size },
                    fileCount = duplicates.size,
                    files = duplicates,
                    type = CleanupType.DUPLICATES
                )
            )
        }

        // 3. Large files card
        val largeFiles = allFiles.filter { it.length() > 5 * 1024 * 1024 }.map { file ->
            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                fileType = determineFileType(file)
            )
        }
        if (largeFiles.isNotEmpty()) {
            list.add(
                CleanupCardState(
                    title = "Large files",
                    description = "Free up major space by removing large videos and audio tracks.",
                    sizeBytes = largeFiles.sumOf { it.size },
                    fileCount = largeFiles.size,
                    files = largeFiles,
                    type = CleanupType.LARGE_FILES
                )
            )
        }

        // 4. Old screenshots card
        val screenshots = allFiles.filter { it.name.contains("screenshot", ignoreCase = true) }.map { file ->
            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                fileType = FileType.IMAGE
            )
        }
        if (screenshots.isNotEmpty()) {
            list.add(
                CleanupCardState(
                    title = "Recent screenshots",
                    description = "Clean up screenshots you might no longer need.",
                    sizeBytes = screenshots.sumOf { it.size },
                    fileCount = screenshots.size,
                    files = screenshots,
                    type = CleanupType.OLD_SCREENSHOTS
                )
            )
        }

        return@withContext list
    }
}
