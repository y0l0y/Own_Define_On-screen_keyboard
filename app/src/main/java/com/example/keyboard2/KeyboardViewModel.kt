package com.example.keyboard2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyboardViewModel (private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(KeyboardUiState())
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    var onCommitText: ((String) -> Unit)? = null
    var onDeleteBackward: (() -> Unit)? = null
    var onCommitEnter: (() -> Unit)? = null

    fun onKeyPress(key: KeyModel, isSwipeUp: Boolean) {
        val s = _state.value
        when(key.type) {
            KeyType.CHAR -> {
                val text = if (isSwipeUp && key.altLabel != null) {
                    key.altLabel
                } else {
                    applyCase(key.label, s)
                }
                onCommitText?.invoke(text)
                if (s.isShift && !s.isCapsLock) {
                    _state.value = s.copy(isShift = false)
                }
            }
            KeyType.SHIFT -> toggleShift()
            KeyType.DEL ->  onDeleteBackward?.invoke()
            KeyType.SPACE -> onCommitText?.invoke(" ")
            KeyType.ENTER -> onCommitEnter?.invoke()
            KeyType.SYMBOLS -> _state.value = s.copy(mode = KeyboardMode.SYMBOLS_1)
            KeyType.NUMBERS -> _state.value = s.copy(mode = KeyboardMode.NUMPAD)
            KeyType.BACK -> _state.value = s.copy(mode = KeyboardMode.LETTERS)
            KeyType.PAGE_TOGGLE -> _state.value = s.copy(
                mode = if (s.mode == KeyboardMode.SYMBOLS_2) KeyboardMode.SYMBOLS_1 else KeyboardMode.SYMBOLS_2
            )
        }
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
}