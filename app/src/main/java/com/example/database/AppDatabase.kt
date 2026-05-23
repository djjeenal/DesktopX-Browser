package com.example.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tabs")
data class BrowserTab(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val isPinned: Boolean = false,
    val lastActive: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val iconRes: String, // "flutterflow", "figma", etc.
    val isSystem: Boolean = false
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val size: String,
    val status: String, // "COMPLETED", "DOWNLOADING", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "virtual_files")
data class VirtualFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String, // e.g. "/MyProjects/index.html"
    val isDirectory: Boolean,
    val content: String = "",
    val sizeBytes: Long = 0,
    val fileType: String = "" // "html", "css", "js", "zip", "apk", "img"
)

@Dao
interface BrowserDao {
    // Tabs
    @Query("SELECT * FROM tabs ORDER BY lastActive DESC")
    fun getAllTabs(): Flow<List<BrowserTab>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: BrowserTab)

    @Update
    suspend fun updateTab(tab: BrowserTab)

    @Delete
    suspend fun deleteTab(tab: BrowserTab)

    @Query("DELETE FROM tabs")
    suspend fun clearAllTabs()

    // History
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryItem)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // Bookmarks
    @Query("SELECT * FROM bookmarks")
    fun getBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    // Downloads
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getDownloads(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem)

    // Virtual Files
    @Query("SELECT * FROM virtual_files")
    fun getAllVirtualFiles(): Flow<List<VirtualFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VirtualFile)

    @Delete
    suspend fun deleteFile(file: VirtualFile)
}

@Database(
    entities = [BrowserTab::class, HistoryItem::class, Bookmark::class, DownloadItem::class, VirtualFile::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "desktopx_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
