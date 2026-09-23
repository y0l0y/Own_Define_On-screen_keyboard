package com.example.keyboard2

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "word_stats", primaryKeys = ["word"])
data class WordStat(
    val word: String,
    val frequency: Int = 1,
    val lastUsedEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "bigram_stats", primaryKeys = ["prevWord", "word"])
data class BigramStat(
    val prevWord: String,
    val word: String,
    val frequency: Int = 1
)

@Dao
interface LearningDao {
    @Query("SELECT * FROM word_stats WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun suggestByPrefix(prefix: String, limit: Int = 3): List<WordStat>

    @Query("SELECT word FROM bigram_stats WHERE prevWord = :prevWord ORDER BY frequency DESC LIMIT :limit")
    suspend fun suggestNextWord(prevWord: String, limit: Int = 3): List<String>

    @Query("""
        INSERT INTO word_stats (word, frequency, lastUsedEpochMs) VALUES (:word, 1, :now)
        ON CONFLICT(word) DO UPDATE SET frequency = frequency + 1, lastUsedEpochMs = :now
    """)
    suspend fun recordWord(word: String, now: Long = System.currentTimeMillis())

    @Query("""
        INSERT INTO bigram_stats (prevWord, word, frequency) VALUES (:prev, :word, 1)
        ON CONFLICT(prevWord, word) DO UPDATE SET frequency = frequency + 1
    """)
    suspend fun recordBigram(prev: String, word: String)

    @Query("DELETE FROM word_stats")
    suspend fun clearAll()
}

@Database(entities = [WordStat::class, BigramStat::class], version = 1, exportSchema = false)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun dao(): LearningDao

    companion object {
        // Build once in Application/Service onCreate:
        // Room.databaseBuilder(context, LearningDatabase::class.java, "keyboard_learning.db").build()
    }
}

/**
 * Call this from KeyboardViewModel right after a word boundary (space/enter/punctuation)
 * to record what the user just typed. Everything here is local disk I/O only — no network.
 */
class LocalLearningStore(private val dao: LearningDao) {

    private var lastWord: String? = null

    suspend fun onWordCommitted(word: String) {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) return
        dao.recordWord(clean)
        lastWord?.let { dao.recordBigram(it, clean) }
        lastWord = clean
    }

    suspend fun suggestions(currentPrefix: String): List<String> {
        if (currentPrefix.isBlank()) {
            return lastWord?.let { dao.suggestNextWord(it) } ?: emptyList()
        }
        return dao.suggestByPrefix(currentPrefix).map { it.word }
    }

    suspend fun wipeAllData() = dao.clearAll()
}