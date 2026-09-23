package com.example.keyboard2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.ClipboardManager
import android.content.Context

class KeyboardViewModel (
    private val scope: CoroutineScope,
    private val learningStore: LocalLearningStore? = null,
    private val clipboardManager: ClipboardManager? = null
) {
    private val _state = MutableStateFlow(KeyboardUiState())
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    private val _isExpandedSuggestions = MutableStateFlow(false)
    val isExpandedSuggestions: StateFlow<Boolean> = _isExpandedSuggestions.asStateFlow()

    var onCommitText: ((String) -> Unit)? = null
    var onDeleteBackward: (() -> Unit)? = null
    var onCommitEnter: (() -> Unit)? = null
    private var currentWordPrefix = StringBuilder()

    fun onKeyPress(key: KeyModel, isSwipeUp: Boolean) {
        val s = _state.value
        when (key.type) {
            KeyType.CHAR -> {
                val text = if (isSwipeUp && key.altLabel != null) {
                    key.altLabel
                } else {
                    applyCase(key.label, s)
                }
                onCommitText?.invoke(text)
                if (text.length == 1 && text[0].isLetter()) {
                    currentWordPrefix.append(text)
                    updateSuggestions(currentWordPrefix.toString())
                }
                if (s.isShift && !s.isCapsLock) {
                    _state.value = s.copy(isShift = false)
                }
            }
            KeyType.SHIFT -> toggleShift()
            KeyType.DEL ->  {
                onDeleteBackward?.invoke()
                if (currentWordPrefix.isNotEmpty()) {
                    currentWordPrefix.deleteCharAt(currentWordPrefix.length - 1)
                    updateSuggestions(currentWordPrefix.toString())
                }
            }
            KeyType.SPACE -> {
                onCommitText?.invoke(" ")
                commitCurrentWord()
            }
            KeyType.ENTER -> {
                onCommitEnter?.invoke()
                commitCurrentWord()
            }
            KeyType.SYMBOLS -> _state.value = s.copy(mode = KeyboardMode.SYMBOLS_1)
            KeyType.NUMBERS -> _state.value = s.copy(mode = KeyboardMode.NUMPAD)
            KeyType.BACK -> {
                _state.value = s.copy(mode = KeyboardMode.LETTERS)
                _isExpandedSuggestions.value = false
            }
            KeyType.PAGE_TOGGLE -> _state.value = s.copy(
                mode = if (s.mode == KeyboardMode.SYMBOLS_2) KeyboardMode.SYMBOLS_1 else KeyboardMode.SYMBOLS_2
            )
            KeyType.NOOP -> { }
        }
    }
    private fun commitCurrentWord(){
        val word = currentWordPrefix.toString()
        if (word.isNotBlank() && learningStore != null){
            scope.launch { learningStore.onWordCommitted(word) }
        }
        currentWordPrefix.clear()
        updateSuggestions("")
    }
    fun toggleExpandSuggestions(){
        _isExpandedSuggestions.value = !_isExpandedSuggestions.value
    }
    private fun applyCase(label: String, s: KeyboardUiState): String =
        if (label.length == 1 && label[0].isLetter() && (s.isShift || s.isCapsLock)) {
            label.uppercase()
        } else {
            label.lowercase()
        }

    private var lastShiftTapMs = 0L
    fun toggleShift(){
        val now = System.currentTimeMillis()
        val s = _state.value
        val isDoubleTap = now - lastShiftTapMs < 300
        lastShiftTapMs = now
        _state.value = when {
            s.isCapsLock -> s.copy(isShift = false, isCapsLock = false)
            isDoubleTap -> s.copy(isShift = true, isCapsLock = true)
            else -> s.copy(isShift = !s.isShift)
        }
    }
    fun getClipboardText(): String? {
        val clipData = clipboardManager?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            return clipData.getItemAt(0).text?.toString()
        }
        return null
    }
    private fun updateSuggestions(prefix: String) {
        if (learningStore == null) return
        scope.launch {
            val baseSuggestions = learningStore.suggestions(prefix).toMutableList()

            if (prefix.isBlank()) {
                getClipboardText()?.let { clipText ->
                    if (clipText.isNotBlank() && clipText.length < 30) {
                        if (!baseSuggestions.contains(clipText)) {
                            baseSuggestions.add(0, "\uD83D\uDCCB $clipText")
                        }
                    }
                }
            }
            _suggestions.value = baseSuggestions
        }
    }
    fun selectSuggestion(word: String) {
        val cleanWord = if (word.startsWith("\uD83D\uDCCB ")) word.removePrefix("\uD83D\uDCCB ") else word
        onCommitText?.invoke("$cleanWord ")

        if (!word.startsWith("\uD83D\uDCCB ")) {
            scope.launch { learningStore?.onWordCommitted(cleanWord) }
        }

        currentWordPrefix.clear()
        updateSuggestions("")
        _isExpandedSuggestions.value = false
    }
}