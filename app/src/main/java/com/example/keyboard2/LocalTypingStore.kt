package com.example.keyboard2

import androidx.compose.runtime.mutableStateOf
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.PriorityQueue
import kotlin.comparisons.compareByDescending

@Entity(
    tableName = "word_stats",
    primaryKeys = ["word"],
    indices = [Index(value = ["frequency"])]
)
data class WordStat(
    val word: String,
    val frequency: Int = 1,
    val lastUsedEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bigram_stats",
    primaryKeys = ["prevWord", "word"],
    indices = [Index(value = ["prevWord", "frequency"])]
)
data class BigramStat(
    val prevWord: String,
    val word: String,
    val frequency: Int = 1
)

@Dao
interface LearningDao {
    @Query("SELECT * FROM word_stats WHERE word LIKE :prefix || '%' AND frequency >= :minFrequency ORDER BY frequency DESC LIMIT :limit")
    suspend fun suggestByPrefix(prefix: String, minFrequency: Int, limit: Int): List<WordStat>

    @Query("SELECT * FROM word_stats ORDER BY frequency DESC LIMIT 5")
    suspend fun getTopWords(): List<WordStat>

    @Query("SELECT * FROM bigram_stats WHERE prevWord = :prevWord AND frequency >= :minFrequency ORDER BY frequency DESC LIMIT :limit")
    suspend fun suggestNextWordRanked(prevWord: String, minFrequency: Int, limit: Int): List<BigramStat>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWordIfAbsent(stat: WordStat): Long
    @Query("UPDATE word_stats SET frequency = frequency + 1, lastUsedEpochMs = :now WHERE word = :word")
    suspend fun incrementWord(word: String, now: Long)
    @Transaction
    suspend fun recordWord(word: String, now: Long = System.currentTimeMillis()) {
        val insertedRowId = insertWordIfAbsent(WordStat(
            word = word,
            frequency = 1,
            lastUsedEpochMs = now
        ))
        if (insertedRowId == -1L) {
            incrementWord(word, now)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBigramIfAbsent(stat: BigramStat): Long

    @Query("UPDATE bigram_stats SET frequency = frequency + 1 WHERE prevWord = :prev AND word = :word")
    suspend fun incrementBigram(prev: String, word: String)

    @Transaction
    suspend fun recordBigram(prev: String, word: String) {
        val insertedRowId = insertBigramIfAbsent(BigramStat(prevWord = prev, word = word, frequency = 1))
        if (insertedRowId == -1L) {
            incrementBigram(prev, word)
        }
    }

    @Query("DELETE FROM word_stats WHERE frequency <= :threshold")
    suspend fun deleteWordsBelow(threshold: Int)

    @Query("DELETE FROM bigram_stats WHERE frequency <= :threshold")
    suspend fun deleteBigramsBelow(threshold: Int)

    @Query("DELETE FROM word_stats")
    suspend fun clearWordStats()
    @Query("DELETE FROM bigram_stats")
    suspend fun clearBigramStats()
}

@Database(entities = [WordStat::class, BigramStat::class], version = 2, exportSchema = false)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun dao(): LearningDao
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

    suspend fun suggestions(currentPrefix: String, limit: Int = 8): List<String> {
        val trusted = rankedCandidates(currentPrefix, minFrequency = 2, limit)
        if (trusted.isNotEmpty()) return trusted
        return rankedCandidates(currentPrefix, minFrequency = 1, limit)
    }

    private suspend fun rankedCandidates(prefix: String, minFrequency: Int, limit: Int): List<String> {
        val heap = PriorityQueue<Pair<String, Int>>(compareByDescending { it.second })

        if (prefix.isBlank()) {
            var addedCount = 0
            lastWord?.let { prev ->
                dao.suggestNextWordRanked(prev, minFrequency, limit).forEach {
                    heap.add(it.word to it.frequency)
                    addedCount++
                }
            }
            if (addedCount < limit) {
                dao.getTopWords().forEach {
                    heap.add(it.word to it.frequency)
                }
            }
        } else {
            dao.suggestByPrefix(prefix, minFrequency, limit).forEach {
                heap.add(it.word to it.frequency)
            }
        }
        val seen = mutableSetOf<String>()
        val ranked = mutableListOf<String>()
        while (heap.isNotEmpty() && ranked.size < limit) {
            val (word, _) = heap.poll()
            if (seen.add(word)) ranked.add(word)
        }
        return ranked
    }
    suspend fun pruneBelow(threshold: Int = 1) {
        dao.deleteWordsBelow(threshold)
        dao.deleteBigramsBelow(threshold)
    }

    suspend fun wipeAllData() {
        dao.clearWordStats()
        dao.clearBigramStats()
        lastWord = null
    }
}