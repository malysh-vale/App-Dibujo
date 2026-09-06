package com.malysh.dibujoapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel

/**
 * Estado central del lienzo.
 *
 * Diseño pensado para crecer: en vez de guardar bitmaps completos
 * para deshacer/rehacer (caro en memoria), guardamos la lista de
 * trazos (comandos de dibujo). Esto también nos servirá más adelante
 * para el sistema de capas: cada capa será simplemente su propia
 * lista de DrawnStroke.
 */
class DrawingViewModel : ViewModel() {

    // Trazos ya confirmados (visibles en el lienzo)
    val strokes = mutableStateListOf<DrawnStroke>()

    // Pila de trazos deshechos, para poder rehacer
    private val redoStack = mutableStateListOf<DrawnStroke>()

    // Trazo que se está dibujando en este momento (mientras el dedo se mueve)
    var currentPath by mutableStateOf<Path?>(null)
        private set

    // Configuración actual del pincel
    var brushColor by mutableStateOf(Color.Black)
    var brushWidthPx by mutableStateOf(12f)

    fun startStroke(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        currentPath = path
        // Empezar un trazo nuevo invalida cualquier "rehacer" pendiente
        redoStack.clear()
    }

    fun continueStroke(x: Float, y: Float) {
        currentPath?.lineTo(x, y)
        // Reasignar para forzar recomposición (Path es mutable internamente)
        currentPath = currentPath
    }

    fun endStroke() {
        val path = currentPath ?: return
        strokes.add(DrawnStroke(path, brushColor, brushWidthPx))
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
}
