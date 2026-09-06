package com.malysh.dibujoapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val PALETTE = listOf(
    Color.Black, Color.DarkGray, Color.Red, Color(0xFFFF9800),
    Color(0xFFFFEB3B), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0), Color.White
)

@Composable
fun DrawingScreen(viewModel: DrawingViewModel) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DibujoApp v0.1") },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = viewModel.canUndo()) {
                        Icon(Icons.Filled.Undo, contentDescription = "Deshacer")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = viewModel.canRedo()) {
                        Icon(Icons.Filled.Redo, contentDescription = "Rehacer")
                    }
                    IconButton(onClick = {
                        exportCanvasAsPng(context, viewModel, canvasSize.width, canvasSize.height)
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Guardar")
                    }
                    IconButton(onClick = { viewModel.clearAll() }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Borrar todo")
                    }
                }
            )
        },
        bottomBar = { ToolBar(viewModel) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> viewModel.startStroke(offset.x, offset.y) },
                        onDrag = { change, _ -> viewModel.continueStroke(change.position.x, change.position.y) },
                        onDragEnd = { viewModel.endStroke() }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().onGloballyPositioned {
                canvasSize = IntSize(it.size.width, it.size.height)
            }) {
                // Trazos ya confirmados
                viewModel.strokes.forEach { stroke ->
                    drawPath(
                        path = stroke.path,
                        color = stroke.color,
                        style = Stroke(width = stroke.strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                // Trazo en progreso
                viewModel.currentPath?.let { path ->
                    drawPath(
                        path = path,
                        color = viewModel.brushColor,
                        style = Stroke(width = viewModel.brushWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolBar(viewModel: DrawingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grosor", modifier = Modifier.width(70.dp))
            Slider(
                value = viewModel.brushWidthPx,
                onValueChange = { viewModel.brushWidthPx = it },
                valueRange = 2f..60f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PALETTE.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, shape = CircleShape)
                        .border(
                            width = if (viewModel.brushColor == color) 3.dp else 1.dp,
                            color = if (viewModel.brushColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { viewModel.brushColor = color }
                )
            }
        }
    }
}

/**
 * Exporta el dibujo actual como PNG a la galería del dispositivo
 * usando MediaStore (funciona sin permisos extra en Android 10+).
 */
private fun exportCanvasAsPng(
    context: android.content.Context,
    viewModel: DrawingViewModel,
    width: Int,
    height: Int
) {
    if (width <= 0 || height <= 0) {
        Toast.makeText(context, "Espera a que el lienzo esté listo", Toast.LENGTH_SHORT).show()
        return
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    viewModel.strokes.forEach { stroke ->
        paint.color = stroke.color.toArgb()
        paint.strokeWidth = stroke.strokeWidthPx
        canvas.drawPath(stroke.path.asAndroidPath(), paint)
    }

    val filename = "dibujo_${System.currentTimeMillis()}.png"
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DibujoApp")
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Toast.makeText(context, "Guardado en Galería > DibujoApp", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "No se pudo guardar la imagen", Toast.LENGTH_SHORT).show()
    }
}

private data class IntSize(val width: Int, val height: Int)

private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
