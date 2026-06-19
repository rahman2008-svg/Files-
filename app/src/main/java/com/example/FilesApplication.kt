package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.FileManagerRepository
import com.example.data.FilesDatabase

class FilesApplication : Application() {
    
    // Lazy initialization of the database
    val database: FilesDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            FilesDatabase::class.java,
            "files_go_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    // Lazy initialization of the repository
    val repository: FileManagerRepository by lazy {
        FileManagerRepository(applicationContext, database.filesDao())
    }
}
