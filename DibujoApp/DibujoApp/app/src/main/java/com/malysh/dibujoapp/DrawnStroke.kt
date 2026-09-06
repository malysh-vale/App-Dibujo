package com.malysh.dibujoapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * Representa un único trazo dibujado por el usuario.
 * Guardamos el Path junto con las propiedades del pincel usadas
 * en el momento de dibujarlo, para que deshacer/rehacer y el
 * futuro sistema de capas puedan reconstruir el dibujo exacto.
 */
data class DrawnStroke(
    val path: Path,
    val color: Color,
    val strokeWidthPx: Float
)
