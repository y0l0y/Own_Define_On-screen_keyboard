package com.example.keyboard2

data class KeyModel(
    val label: String,
    val altLabel: String? = null,
    val type: KeyType = KeyType.CHAR,
    val flex: Float = 1f
)

enum class KeyType{
    CHAR,SHIFT,DEL,SYMBOLS,NUMBERS,SPACE,ENTER,BACK,PAGE_TOGGLE,NOOP
}

enum class KeyboardMode{
    LETTERS, NUMPAD, SYMBOLS_1, SYMBOLS_2
}

object KeyboardLayouts{
    private val row1 = listOf("1" to "q", "2" to "w", "3" to "e", "4" to "r", "5" to "t",
        "6" to "y", "7" to "u", "8" to "i", "9" to "o", "0" to "p")
        .map { KeyModel(label = it.second, altLabel = it.first) }
    private val row2 = listOf("!" to "a", "@" to "s", "#" to "d", "$" to "f", "%" to "g",
        "&" to "h", "*" to "j", "(" to "k", ")" to "l")
        .map { KeyModel(label = it.second, altLabel = it.first) }
    private val row3Letters = listOf("'" to "z", "/" to "x", "-" to "c", "_" to "v",
        ":" to "b", ";" to "n", "?" to "m")
        .map { KeyModel(label = it.second, altLabel = it.first) }
    private val row3 = listOf(
        KeyModel(label = "\uD83D\uDE80", type = KeyType.SHIFT, flex = 1.3f)
    ) + row3Letters + listOf(
        KeyModel(label = "DEL", type = KeyType.DEL, flex = 1.3f)
    )
    private val row4Letters = listOf("," to ".")
        .map { KeyModel(label = it.second, altLabel = it.first) }
    private val row4 = listOf(
        KeyModel(label = "符", type = KeyType.SYMBOLS, flex = 1.2f),
        KeyModel(label = "123", type = KeyType.NUMBERS, flex = 1.2f),
        KeyModel(label = "abc", flex = 1f),
        KeyModel(label = "Space", type = KeyType.SPACE, flex = 3f)
    ) + row4Letters + listOf(
        KeyModel(label = "英/中", type = KeyType.NOOP, flex = 1.2f),
        KeyModel(label = "Enter", type = KeyType.ENTER, flex = 1.4f)
    )
    val letters: List<List<KeyModel>> = listOf(row1, row2, row3, row4)
    private val sym1Row1 = (1..9).map { it.toString() }.plus("0").map { KeyModel(it) }
    private val sym1Row2 = listOf("-", "/", ":", ";", "(", ")", "_", "$", "&", "\"").map { KeyModel(it) }
    private val sym1Row3 = listOf(
        KeyModel(label = "#+=", type = KeyType.PAGE_TOGGLE, flex = 1.3f),
        KeyModel("~"), KeyModel(","), KeyModel("···"), KeyModel("@"), KeyModel("!"), KeyModel("'"),
        KeyModel(label = "DEL", type = KeyType.DEL, flex = 1.3f)
    )
    private val sym2Row1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=").map { KeyModel(it) }
    private val sym2Row2 = listOf("`", "\\", "|", "<", ">", "¥", "€", "£", "¢", "·").map { KeyModel(it) }
    private val sym2Row3 = listOf(
        KeyModel(label = "123", type = KeyType.PAGE_TOGGLE, flex = 1.3f),
        KeyModel("~"), KeyModel(","), KeyModel("···"), KeyModel("@"), KeyModel("!"), KeyModel("`"),
        KeyModel(label = "DEL", type = KeyType.DEL, flex = 1.3f)
    )
    private val symbolsBottomBar = listOf(
        KeyModel(label = "返回", type = KeyType.BACK, flex = 1.3f),
        KeyModel(label = "Space", type = KeyType.SPACE, flex = 3f),
        KeyModel(label = "?"),
        KeyModel(label = "更多", type = KeyType.PAGE_TOGGLE, flex = 1.3f),
        KeyModel(label = "搜索", type = KeyType.ENTER, flex = 1.4f)
    )
    val symbolsPage1: List<List<KeyModel>> = listOf(sym1Row1, sym1Row2, sym1Row3, symbolsBottomBar)
    val symbolsPage2: List<List<KeyModel>> = listOf(sym2Row1, sym2Row2, sym2Row3, symbolsBottomBar)
    val numpadGrid: List<List<KeyModel>> = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    ).map { row -> row.map { KeyModel(it) } }
    val numpadLeftColumn: List<KeyModel> = listOf(
        "+", "-", "*", "/", "()", ".", ":", ",", "#", "=", "%"
    ).map { KeyModel(it) }
    val numpadRightColumn: List<KeyModel> = listOf(
        KeyModel(label = "DEL", type = KeyType.DEL),
        KeyModel(label = "@"),
        KeyModel(label = ".")
    )
    val numpadBottomBar: List<KeyModel> = listOf(
        KeyModel(label = "符号", type = KeyType.SYMBOLS, flex = 0.65f),
        KeyModel(label = "返回", type = KeyType.BACK, flex = 1f),
        KeyModel(label = "0", flex = 1f),
        KeyModel(label = "Space", type = KeyType.SPACE, flex = 1f),
        KeyModel(label = "搜索", type = KeyType.ENTER, flex = 0.65f)
    )
}

data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.LETTERS,
    val isShift: Boolean = false,
    val isCapsLock: Boolean = false
) { val rows: List<List<KeyModel>>
        get() = when (mode) {
            KeyboardMode.LETTERS -> KeyboardLayouts.letters
            KeyboardMode.SYMBOLS_1 -> KeyboardLayouts.symbolsPage1
            KeyboardMode.SYMBOLS_2 -> KeyboardLayouts.symbolsPage2
            KeyboardMode.NUMPAD -> emptyList()
        }
}