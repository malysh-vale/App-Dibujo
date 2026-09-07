package com.malysh.dibujoapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

enum class BrushType {
    LAPIZ,
    MARCADOR,
    AEROGRAFO,
    BORRADOR
}

data class DrawnStroke(
    val path: Path,
    val color: Color,
    val strokeWidthPx: Float,
    val opacity: Float = 1f,
    val brushType: BrushType = BrushType.LAPIZ
)

fun brushLayers(brushType: BrushType): List<Pair<Float, Float>> {
    return when (brushType) {
        BrushType.LAPIZ -> listOf(1f to 1f)
        BrushType.MARCADOR -> listOf(1f to 1f)
        BrushType.BORRADOR -> listOf(1f to 1f)
        BrushType.AEROGRAFO -> listOf(
            1.8f to 0.15f,
            1.5f to 0.25f,
            1.2f to 0.4f,
            1.0f to 0.6f
        )
    }
}
