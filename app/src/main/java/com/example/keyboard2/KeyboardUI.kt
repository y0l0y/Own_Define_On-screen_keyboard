package com.example.keyboard2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val  SWIPE_UP_THRESHOLD_PX = 60f
private val KEY_ROW_HEIGHT = 52.dp
private val ROW_SPACING = 4.dp
private val NUMPAD_GRID_HEIGHT = KEY_ROW_HEIGHT * 3 + ROW_SPACING * 2
private val SUGGESTION_BAR_HEIGHT = 44.dp
private val KEYBOARD_CONTENT_HEIGHT = SUGGESTION_BAR_HEIGHT + (KEY_ROW_HEIGHT * 4) + (ROW_SPACING * 3)

@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isExpanded by viewModel.isExpandedSuggestions.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        when (state.mode) {
            KeyboardMode.LETTERS if isExpanded -> {
                ExpandedSuggestionsScreen(
                    suggestions = suggestions,
                    onSelect = { viewModel.selectSuggestion(it) },
                    onCollapse = { viewModel.toggleExpandSuggestions() },
                    onHide = { viewModel.hideKeyboard() }
                )
            }
            KeyboardMode.NUMPAD -> NumpadScreen(viewModel)
            else -> Column {
                if (state.mode == KeyboardMode.LETTERS && suggestions.isNotEmpty()) {
                    SuggestionBar(
                        suggestions = suggestions,
                        onSelect = { viewModel.selectSuggestion(it) },
                        onExpand = { viewModel.toggleExpandSuggestions() },
                        onHide = { viewModel.hideKeyboard() }
                    )
                }
                RowBasedScreen(state, viewModel)
            }
        }
    }
}
@Composable
private fun SuggestionBar(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
    onExpand: () -> Unit,
    onHide: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SUGGESTION_BAR_HEIGHT)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            suggestions.forEach { word ->
                Text(
                    text = word,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onSelect(word) }
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            Text(
                text = "\u25BC",
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { onExpand() }
                    .padding(start = 12.dp, end = 12.dp)
            )
        }
        HideKeyboardButton(
            onClick = onHide,
            modifier = Modifier.padding(start = 4.dp)
        )

    }
}

@Composable
private fun HideKeyboardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u2715",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpandedSuggestionsScreen(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
    onCollapse: () -> Unit,
    onHide: () -> Unit
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(KEYBOARD_CONTENT_HEIGHT)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(suggestions) { word ->
                Text(
                    text = word,
                    fontSize = 26.sp,
                    modifier = Modifier.clickable { onSelect(word) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u2303", // ^ (up)
                fontSize = 20.sp,
                modifier = Modifier.clickable {
                    scope.launch { gridState.animateScrollBy(-800f) }
                }
            )
            Text(
                text = "\u2304", // v (down)
                fontSize = 20.sp,
                modifier = Modifier.clickable {
                    scope.launch { gridState.animateScrollBy(800f) }
                }
            )
            Text(
                text = "返回",
                fontSize = 16.sp,
                modifier = Modifier.clickable { onCollapse() }
            )
            HideKeyboardButton(onClick = onHide)
        }
    }
}
@Composable
private fun RowBasedScreen(
    state: KeyboardUiState,
    viewModel: KeyboardViewModel
) {
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
                        modifier = Modifier
                            .weight(key.flex)
                            .height(KEY_ROW_HEIGHT),
                        onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                        onSwipeUp = { viewModel.onKeyPress(key, isSwipeUp = true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NumpadScreen(viewModel: KeyboardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NUMPAD_GRID_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
            ) {
                items(KeyboardLayouts.numpadLeftColumn) { key ->
                    KeyButton(
                        key = key,
                        isShiftActive = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KEY_ROW_HEIGHT),
                        onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                        onSwipeUp = {}
                    )
                }
            }
            Column(
                modifier = Modifier.weight(2.8f),
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
            ) {
                KeyboardLayouts.numpadGrid.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KEY_ROW_HEIGHT),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { key ->
                            KeyButton(
                                key = key,
                                isShiftActive = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onTap = { viewModel.onKeyPress(key, isSwipeUp = false) },
                                onSwipeUp = {}
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(0.6f),
                verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
            ) {
                KeyboardLayouts.numpadRightColumn.forEach { key ->
                    KeyButton(
                        key = key,
                        isShiftActive = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KEY_ROW_HEIGHT),
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
                    modifier = Modifier
                        .weight(key.flex)
                        .height(52.dp),
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (key.altLabel != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        key.altLabel,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Text(
                        displayLabel(key, isShiftActive),
                        fontSize = 18.sp
                    )
                }
            } else {
                Text(
                    displayLabel(key, isShiftActive),
                    fontSize = 16.sp
                )
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