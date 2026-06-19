package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteFile(
    @PrimaryKey val path: String,
    val title: String,
    val addedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "safe_files")
data class SafeFile(
    @PrimaryKey val path: String, // Path of the file inside safe folder directory
    val originalPath: String, // Original location of the file before move
    val title: String,
    val size: Long,
    val addedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "safe_folder_key")
data class SafeFolderKey(
    @PrimaryKey val id: Int = 1,
    val passcodeHash: String
)

@Dao
interface FilesDao {
    // Favorites Queries
    @Query("SELECT * FROM favorites ORDER BY addedDate DESC")
    fun getAllFavorites(): Flow<List<FavoriteFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteFile)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun deleteFavoriteByPath(path: String)

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    // Safe Files Queries
    @Query("SELECT * FROM safe_files ORDER BY addedDate DESC")
    fun getAllSafeFiles(): Flow<List<SafeFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafeFile(safeFile: SafeFile)

    @Query("DELETE FROM safe_files WHERE path = :path")
    suspend fun deleteSafeFile(path: String)

    @Query("SELECT EXISTS(SELECT * FROM safe_files WHERE path = :path)")
    suspend fun isSafeFile(path: String): Boolean

    // Safe Folder Key Queries
    @Query("SELECT * FROM safe_folder_key WHERE id = 1 LIMIT 1")
    suspend fun getSafeFolderKey(): SafeFolderKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafeFolderKey(key: SafeFolderKey)

    @Query("DELETE FROM safe_folder_key WHERE id = 1")
    suspend fun deleteSafeFolderKey()
}

@Database(entities = [FavoriteFile::class, SafeFile::class, SafeFolderKey::class], version = 1, exportSchema = false)
abstract class FilesDatabase : RoomDatabase() {
    abstract fun filesDao(): FilesDao
}
