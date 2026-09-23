package com.example.keyboard2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val  SWIPE_UP_THRESHOLD_PX = 60f

@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.state.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        if (state.mode == KeyboardMode.NUMPAD) {
            NumpadScreen(viewModel)
        } else {
            RowBasedScreen(state, viewModel)
        }
    }
}

@Composable
private fun RowBasedScreen(state: KeyboardUiState, viewModel: KeyboardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        isShiftActive = state.isShift || state.isCapsLock,
                        modifier = Modifier.weight(key.flex).height(52.dp),
                        onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                        onSwipeUp = { viewModel.onKeyPress(key, isSwipeUp = true) }
                    )
                }
            }
        }
    }
}
private val NUMPAD_KEY_HEIGHT = 52.dp
private val NUMPAD_ROW_SPACING = 4.dp
private  val NUMPAD_GRID_HEIGHT = NUMPAD_KEY_HEIGHT * 3 + NUMPAD_ROW_SPACING * 2
@Composable
private fun NumpadScreen(viewModel: KeyboardViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(NUMPAD_GRID_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(0.6f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(NUMPAD_ROW_SPACING)
            ) {
                items(KeyboardLayouts.numpadLeftColumn) { key ->
                    KeyButton(
                        key = key,
                        isShiftActive = false,
                        modifier = Modifier.fillMaxWidth().height(NUMPAD_KEY_HEIGHT),
                        onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                        onSwipeUp = {}
                    )
                }
            }
            Column(
                modifier = Modifier.weight(2.8f),
                verticalArrangement = Arrangement.spacedBy(NUMPAD_ROW_SPACING)
            ) {
                KeyboardLayouts.numpadGrid.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(NUMPAD_KEY_HEIGHT),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { key ->
                            KeyButton(
                                key = key,
                                isShiftActive = false,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                                onSwipeUp = {}
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(0.6f),
                verticalArrangement = Arrangement.spacedBy(NUMPAD_ROW_SPACING)
            ) {
                KeyboardLayouts.numpadRightColumn.forEach { key ->
                    KeyButton(
                        key = key,
                        isShiftActive = false,
                        modifier = Modifier.fillMaxWidth().height(NUMPAD_KEY_HEIGHT),
                        onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                        onSwipeUp = {}
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyboardLayouts.numpadBottomBar.forEach { key ->
                KeyButton(
                    key = key,
                    isShiftActive = false,
                    modifier = Modifier.weight(key.flex).height(52.dp),
                    onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                    onSwipeUp = {}
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    key: KeyModel,
    isShiftActive: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit
) {
    val bg = if (key.type == KeyType.SHIFT && isShiftActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = modifier
            .then(
                if (key.altLabel != null) {
                    Modifier.pointerInput(key) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var accumulatedDy = 0f
                            var pointerUp = false
                            while (!pointerUp) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                accumulatedDy += change.positionChange().y
                                change.consume()
                                pointerUp = event.changes.none { it.pressed }
                            }
                            if (accumulatedDy < -SWIPE_UP_THRESHOLD_PX) onSwipeUp() else onTap()
                        }
                    }
                } else {
                    Modifier.clickable(onClick = onTap)
                }
            ),
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (key.altLabel != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(key.altLabel, fontSize = 10.sp, color = Color.Gray)
                    Text(displayLabel(key, isShiftActive), fontSize = 18.sp)
                }
            } else {
                Text(displayLabel(key, isShiftActive), fontSize = 16.sp)
            }
        }
    }
}

private fun displayLabel(key: KeyModel, isShiftActive: Boolean): String =
    if (key.type == KeyType.CHAR && key.label.length == 1 && isShiftActive) {
        key.label.uppercase()
    } else {
        key.label
    }