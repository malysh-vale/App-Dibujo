package com.malysh.dibujoapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

class LayerModel(
    val id: String,
    name: String = "Capa"
) {
    var name by mutableStateOf(name)
    var visible by mutableStateOf(true)
    val strokes: SnapshotStateList<DrawnStroke> = mutableStateListOf()
}
