package com.example.keyboard2

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

object AppDatabaseProvider {
    @Volatile private var instance: LearningDatabase? = null
    fun get(context: Context): LearningDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LearningDatabase::class.java,
                "keyboard_learning.db"
            )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val inputStream = context.applicationContext.assets.open("CET_4+6_edited.txt")
                                val reader = BufferedReader(InputStreamReader(inputStream))
                                val now = System.currentTimeMillis()

                                db.beginTransaction()
                                try {
                                    reader.lineSequence().forEach { line ->
                                        val word = line.trim().lowercase()
                                        if (word.isNotBlank()) {
                                            db.execSQL(
                                                "INSERT OR IGNORE INTO word_stats (word, frequency, lastUsedEpochMs) VALUES (?, ?, ?)",
                                                arrayOf(word, 10, now)
                                            )
                                        }
                                    }
                                    db.setTransactionSuccessful()
                                } finally {
                                    db.endTransaction()
                                    reader.close()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}