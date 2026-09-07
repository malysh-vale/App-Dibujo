package com.malysh.dibujoapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import java.util.UUID

/** Una acción registrada en el historial global de deshacer/rehacer. */
private data class LayerAction(val layerId: String, val stroke: DrawnStroke)

class DrawingViewModel : ViewModel() {

    // Capas del documento. Índice 0 = capa más al fondo, la última = más arriba.
    val layers = mutableStateListOf(LayerModel(id = newId(), name = "Capa 1"))

    var activeLayerId by mutableStateOf(layers.first().id)
        private set

    // Historial global (cronológico), sea cual sea la capa donde se dibujó.
    private val history = mutableStateListOf<LayerAction>()
    private val redoStack = mutableStateListOf<LayerAction>()

    var currentPath by mutableStateOf<Path?>(null, policy = neverEqualPolicy())
        private set

    // Configuración actual del pincel
    var brushColor by mutableStateOf(Color.Black)
    var brushWidthPx by mutableStateOf(12f)
    var brushOpacity by mutableStateOf(1f)
    var brushType by mutableStateOf(BrushType.LAPIZ)

    private fun activeLayer(): LayerModel =
        layers.find { it.id == activeLayerId } ?: layers.first()

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
        val stroke = DrawnStroke(
            path = path,
            color = brushColor,
            strokeWidthPx = brushWidthPx,
            opacity = brushOpacity,
            brushType = brushType
        )
        val layer = activeLayer()
        layer.strokes.add(stroke)
        history.add(LayerAction(layer.id, stroke))
        currentPath = null
    }

    fun undo() {
        val action = history.removeLastOrNull() ?: return
        layers.find { it.id == action.layerId }?.strokes?.remove(action.stroke)
        redoStack.add(action)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        layers.find { it.id == action.layerId }?.strokes?.add(action.stroke)
        history.add(action)
    }

    fun canUndo(): Boolean = history.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clearAll() {
        layers.forEach { it.strokes.clear() }
        history.clear()
        redoStack.clear()
    }

    fun selectBrush(type: BrushType) {
        brushType = type
        brushOpacity = when (type) {
            BrushType.LAPIZ -> 1f
            BrushType.MARCADOR -> 0.55f
            BrushType.AEROGRAFO -> 0.8f
            BrushType.BORRADOR -> 1f
        }
    }

    // ---------- Gestión de capas ----------

    fun addLayer() {
        val newLayer = LayerModel(id = newId(), name = "Capa ${layers.size + 1}")
        layers.add(newLayer)
        activeLayerId = newLayer.id
    }

    fun selectLayer(layerId: String) {
        activeLayerId = layerId
    }

    fun toggleLayerVisibility(layerId: String) {
        layers.find { it.id == layerId }?.let { it.visible = !it.visible }
    }

    fun deleteLayer(layerId: String) {
        if (layers.size <= 1) return // siempre debe quedar al menos una capa
        val index = layers.indexOfFirst { it.id == layerId }
        if (index == -1) return
        layers.removeAt(index)
        // Limpiamos del historial cualquier acción que apuntara a la capa borrada,
        // para que deshacer/rehacer no intente resucitar algo de una capa que ya no existe.
        history.removeAll { it.layerId == layerId }
        redoStack.removeAll { it.layerId == layerId }
        if (activeLayerId == layerId) {
            val newIndex = index.coerceAtMost(layers.size - 1)
            activeLayerId = layers[newIndex].id
        }
    }

    fun moveLayerUp(layerId: String) {
        val index = layers.indexOfFirst { it.id == layerId }
        if (index == -1 || index == layers.size - 1) return
        layers.add(index + 1, layers.removeAt(index))
    }

    fun moveLayerDown(layerId: String) {
        val index = layers.indexOfFirst { it.id == layerId }
        if (index <= 0) return
        layers.add(index - 1, layers.removeAt(index))
    }

    /**
     * Fusiona la capa indicada con la que está justo debajo de ella.
     * Nota: esto limpia el historial de deshacer/rehacer, ya que fusionar
     * capas es una operación que no se puede deshacer de forma consistente
     * con el resto del historial (es una limitación consciente por ahora).
     */
    fun mergeDown(layerId: String) {
        val index = layers.indexOfFirst { it.id == layerId }
        if (index <= 0) return // no hay capa debajo
        val current = layers[index]
        val below = layers[index - 1]
        below.strokes.addAll(current.strokes)
        layers.removeAt(index)
        history.clear()
        redoStack.clear()
        if (activeLayerId == current.id) {
            activeLayerId = below.id
        }
    }

    companion object {
        private fun newId(): String = UUID.randomUUID().toString()
    }
}
