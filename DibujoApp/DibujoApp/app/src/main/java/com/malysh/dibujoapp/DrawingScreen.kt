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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
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
import kotlin.math.max

private val PALETTE = listOf(
    Color.Black, Color.DarkGray, Color.Red, Color(0xFFFF9800),
    Color(0xFFFFEB3B), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0), Color.White
)

private data class IntSize(val width: Int, val height: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(viewModel: DrawingViewModel) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }
    var showColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DibujoApp v0.2") },
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
        bottomBar = { ToolBar(viewModel, onOpenColorDialog = { showColorDialog = true }) }
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
                    drawStroke(stroke.path, stroke.color, stroke.strokeWidthPx, stroke.opacity, stroke.brushType)
                }
                // Trazo en progreso (usa la config actual del pincel)
                viewModel.currentPath?.let { path ->
                    drawStroke(
                        path,
                        viewModel.brushColor,
                        viewModel.brushWidthPx,
                        viewModel.brushOpacity,
                        viewModel.brushType
                    )
                }
            }
        }
    }

    if (showColorDialog) {
        ColorPickerDialog(
            initialColor = viewModel.brushColor,
            onDismiss = { showColorDialog = false },
            onColorSelected = {
                viewModel.brushColor = it
                showColorDialog = false
            }
        )
    }
}

/** Dibuja un trazo en un DrawScope de Compose, respetando pincel/opacidad. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    path: androidx.compose.ui.graphics.Path,
    color: Color,
    widthPx: Float,
    opacity: Float,
    brushType: BrushType
) {
    brushLayers(brushType).forEach { (widthMul, alphaMul) ->
        drawPath(
            path = path,
            color = color.copy(alpha = (opacity * alphaMul).coerceIn(0f, 1f)),
            style = Stroke(
                width = widthPx * widthMul,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun ToolBar(viewModel: DrawingViewModel, onOpenColorDialog: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Selector de tipo de pincel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BrushType.values().forEach { type ->
                BrushTypeButton(
                    type = type,
                    selected = viewModel.brushType == type,
                    onClick = { viewModel.selectBrush(type) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grosor
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grosor", modifier = Modifier.width(70.dp))
            Slider(
                value = viewModel.brushWidthPx,
                onValueChange = { viewModel.brushWidthPx = it },
                valueRange = 2f..60f,
                modifier = Modifier.weight(1f)
            )
        }

        // Opacidad
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacidad", modifier = Modifier.width(70.dp))
            Slider(
                value = viewModel.brushOpacity,
                onValueChange = { viewModel.brushOpacity = it },
                valueRange = 0.05f..1f,
                modifier = Modifier.weight(1f)
            )
        }

        // Paleta de colores + botón de "más colores"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PALETTE.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = viewModel.brushColor == color,
                    onClick = { viewModel.brushColor = color }
                )
            }
            // Botón "más colores": muestra el color actual si no está en la paleta
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        ),
                        shape = CircleShape
                    )
                    .border(1.dp, Color.Gray, CircleShape)
                    .clickable { onOpenColorDialog() }
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color, shape = CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun BrushTypeButton(type: BrushType, selected: Boolean, onClick: () -> Unit) {
    val label = when (type) {
        BrushType.LAPIZ -> "Lápiz"
        BrushType.MARCADOR -> "Marcador"
        BrushType.AEROGRAFO -> "Aerógrafo"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Brush, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Selector de color personalizado, usando matiz/saturación/brillo (HSV).
 * Se eligió HSV con sliders en vez de una rueda de color táctil porque
 * es mucho más simple de implementar de forma confiable en Compose puro,
 * y da control igual de preciso.
 */
@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val hsv = remember {
        val hsvArr = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.rgb(
                (initialColor.red * 255).toInt(),
                (initialColor.green * 255).toInt(),
                (initialColor.blue * 255).toInt()
            ),
            hsvArr
        )
        hsvArr
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }

    val previewColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir color") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(previewColor, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Matiz")
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f
                )

                Text("Saturación")
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f
                )

                Text("Brillo")
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(previewColor) }) {
                Text("Usar este color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Exporta el dibujo actual como PNG a la galería del dispositivo
 * usando MediaStore (funciona sin permisos extra en Android 10+).
 * Reproduce el mismo esquema de capas por pincel (brushLayers) que
 * el lienzo en vivo, para que el PNG final se vea igual a lo dibujado.
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
        val androidPath = stroke.path.asAndroidPath()
        brushLayers(stroke.brushType).forEach { (widthMul, alphaMul) ->
            val finalAlpha = (stroke.opacity * alphaMul).coerceIn(0f, 1f)
            paint.color = stroke.color.toArgb()
            paint.alpha = (finalAlpha * 255).toInt()
            paint.strokeWidth = max(1f, stroke.strokeWidthPx * widthMul)
            canvas.drawPath(androidPath, paint)
        }
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

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
