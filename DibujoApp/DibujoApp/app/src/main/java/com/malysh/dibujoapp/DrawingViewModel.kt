package com.malysh.dibujoapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel

class DrawingViewModel : ViewModel() {

    val strokes = mutableStateListOf<DrawnStroke>()
    private val redoStack = mutableStateListOf<DrawnStroke>()

    var currentPath by mutableStateOf<Path?>(null, policy = neverEqualPolicy())
        private set

    var brushColor by mutableStateOf(Color.Black)
    var brushWidthPx by mutableStateOf(12f)
    var brushOpacity by mutableStateOf(1f)
    var brushType by mutableStateOf(BrushType.LAPIZ)

    fun startStroke(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        currentPath = path
        redoStack.clear()
    }

    fun continueStroke(x: Float, y: Float) {
        currentPath?.lineTo(x, y)
        currentPath = currentPath
    }

    fun endStroke() {
        val path = currentPath ?: return
        strokes.add(
            DrawnStroke(
                path = path,
                color = brushColor,
                strokeWidthPx = brushWidthPx,
                opacity = brushOpacity,
                brushType = brushType
            )
        )
        currentPath = null
    }

    fun undo() {
        if (strokes.isEmpty()) return
        val last = strokes.removeAt(strokes.lastIndex)
        redoStack.add(last)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val stroke = redoStack.removeAt(redoStack.lastIndex)
        strokes.add(stroke)
    }

    fun canUndo(): Boolean = strokes.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clearAll() {
        strokes.clear()
        redoStack.clear()
    }

    fun selectBrush(type: BrushType) {
        brushType = type
        brushOpacity = when (type) {
            BrushType.LAPIZ -> 1f
            BrushType.MARCADOR -> 0.55f
            BrushType.AEROGRAFO -> 0.8f
        }
    }
}
