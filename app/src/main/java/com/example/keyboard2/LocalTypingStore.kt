package com.example.keyboard2

import androidx.room.*
import java.util.PriorityQueue
import kotlin.collections.emptyList
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

    @Query("SELECT * FROM word_stats WHERE word LIKE '%' || :fragment || '%' AND frequency >= :minFrequency ORDER BY frequency DESC LIMIT :limit")
    suspend fun suggestContains(fragment: String, minFrequency: Int, limit: Int): List<WordStat>

    @Query("SELECT * FROM word_stats ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTopWords(limit: Int = 10): List<WordStat>

    @Query("SELECT * FROM bigram_stats WHERE prevWord = :prevWord AND frequency >= :minFrequency ORDER BY frequency DESC LIMIT :limit")
    suspend fun suggestNextWordRanked(prevWord: String, minFrequency: Int, limit: Int): List<BigramStat>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWordIfAbsent(stat: WordStat): Long

    @Query("UPDATE word_stats SET frequency = frequency + 1, lastUsedEpochMs = :now WHERE word = :word")
    suspend fun incrementWord(word: String, now: Long)

    @Transaction
    suspend fun recordWord(
        word: String,
        now: Long = System.currentTimeMillis()
    ) {
        val insertedRowId = insertWordIfAbsent(
            WordStat(
                word = word,
                frequency = 1,
                lastUsedEpochMs = now
            )
        )
        if (insertedRowId == -1L) {
            incrementWord(word, now)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBigramIfAbsent(stat: BigramStat): Long

    @Query("UPDATE bigram_stats SET frequency = frequency + 1 WHERE prevWord = :prev AND word = :word")
    suspend fun incrementBigram(prev: String, word: String)

    @Transaction
    suspend fun recordBigram(
        prev: String,
        word: String
    ) {
        val insertedRowId = insertBigramIfAbsent(
            BigramStat(
                prevWord = prev,
                word = word,
                frequency = 1
            )
        )
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

    suspend fun suggestions(
        currentPrefix: String,
        limit: Int = 20
    ): List<String> {
        if (currentPrefix.isBlank()) return blankPrefixSuggestions(limit)
        rankWords(
            dao.suggestByPrefix(
                currentPrefix,
                minFrequency = 2,
                limit),
            limit
        ).let { if (it.isNotEmpty()) return it }
        rankWords(
            dao.suggestByPrefix(
                currentPrefix,
                minFrequency = 1,
                limit),
            limit
        ).let { if (it.isNotEmpty()) return it }
        rankWords(
            dao.suggestContains(
                currentPrefix,
                minFrequency = 1,
                limit),
            limit
        ).let { if (it.isNotEmpty()) return it }
        return dao.getTopWords(limit).map { it.word }
    }

    private suspend fun blankPrefixSuggestions(limit: Int): List<String> {
        val fromBigram = lastWord?.let { prev ->
            rankBigrams(
                dao.suggestNextWordRanked(
                    prev,
                    minFrequency = 1,
                    limit),
                limit
            )
        } ?: emptyList()
        if (fromBigram.isNotEmpty()) return fromBigram
        return dao.getTopWords(limit).map { it.word }
    }

    private fun rankWords(
        entries: List<WordStat>,
        limit: Int
    ): List<String> {
        val heap = PriorityQueue<Pair<String, Int>>(compareByDescending { it.second })
        entries.forEach { heap.add(it.word to it.frequency) }
        val seen = mutableSetOf<String>()
        val ranked = mutableListOf<String>()
        while (heap.isNotEmpty() && ranked.size < limit) {
            val (word, _) = heap.poll()!!
            if (seen.add(word)) ranked.add(word)
        }
        return ranked
    }

    private fun rankBigrams(
        entries: List<BigramStat>,
        limit: Int
    ): List<String> {
        val heap = PriorityQueue<Pair<String, Int>>(compareByDescending { it.second })
        entries.forEach { heap.add(it.word to it.frequency) }
        val seen = mutableSetOf<String>()
        val ranked = mutableListOf<String>()
        while (heap.isNotEmpty() && ranked.size < limit) {
            val (word, _) = heap.poll()!!
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