package com.example.keyboard2

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MyKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob())
    private lateinit var keyboardViewModel: KeyboardViewModel
    private lateinit var learningDatabase: LearningDatabase

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onCreate()

        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        // --- This is the "linking" step: build the local DB, wrap it, hand it to the ViewModel ---
        learningDatabase = Room.databaseBuilder(
            applicationContext,
            LearningDatabase::class.java,
            "keyboard_learning.db"
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = applicationContext.assets.open("CET_4+6_edited.txt")
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
        }).fallbackToDestructiveMigration().build()
        val learningStore = LocalLearningStore(learningDatabase.dao())
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        keyboardViewModel = KeyboardViewModel(
            serviceScope,
            learningStore,
            clipboardManager
        ).apply {
            onCommitText = {
                text -> currentInputConnection?.commitText(
                text, 1)
            }
            onDeleteBackward = {
                currentInputConnection?.deleteSurroundingText(
                    1, 0)
            }
            onCommitEnter = {
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
            }
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { KeyboardScreen(keyboardViewModel) }
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceScope.cancel()
        if (::learningDatabase.isInitialized) learningDatabase.close()
        super.onDestroy()
    }
}

