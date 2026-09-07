package com.malysh.dibujoapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    var showLayersSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DibujoApp v0.3") },
                actions = {
                    IconButton(onClick = { showLayersSheet = true }) {
                        Icon(Icons.Filled.Layers, contentDescription = "Capas")
                    }
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
                val bounds = Rect(0f, 0f, size.width, size.height)
                // Cada capa se dibuja en su propia "capa offscreen" (saveLayer),
                // así el borrador de una capa nunca afecta a las capas de abajo.
                viewModel.layers.forEach { layer ->
                    if (!layer.visible) return@forEach
                    drawContext.canvas.saveLayer(bounds, Paint())
                    layer.strokes.forEach { stroke ->
                        drawStroke(stroke.path, stroke.color, stroke.strokeWidthPx, stroke.opacity, stroke.brushType)
                    }
                    if (layer.id == viewModel.activeLayerId) {
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
                    drawContext.canvas.restore()
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

    if (showLayersSheet) {
        LayersSheet(viewModel = viewModel, onDismiss = { showLayersSheet = false })
    }
}

/** Dibuja un trazo respetando pincel/opacidad. Si es BORRADOR, borra de verdad (BlendMode.Clear). */
private fun DrawScope.drawStroke(
    path: Path,
    color: Color,
    widthPx: Float,
    opacity: Float,
    brushType: BrushType
) {
    val blendMode = if (brushType == BrushType.BORRADOR) BlendMode.Clear else BlendMode.SrcOver
    brushLayers(brushType).forEach { (widthMul, alphaMul) ->
        drawPath(
            path = path,
            color = color,
            alpha = (opacity * alphaMul).coerceIn(0f, 1f),
            style = Stroke(width = widthPx * widthMul, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = blendMode
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
        // Selector de tipo de pincel (con scroll horizontal por si no caben los 4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        // Opacidad (también aplica al borrador: controla qué tan fuerte borra)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacidad", modifier = Modifier.width(70.dp))
            Slider(
                value = viewModel.brushOpacity,
                onValueChange = { viewModel.brushOpacity = it },
                valueRange = 0.05f..1f,
                modifier = Modifier.weight(1f)
            )
        }

        // Paleta de colores + botón de "más colores" (deshabilitados visualmente si se usa el borrador,
        // aunque no bloqueamos su uso ya que no afecta el resultado del borrado)
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
        BrushType.BORRADOR -> "Borrador"
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
        val icon = if (type == BrushType.BORRADOR) Icons.Filled.Backspace else Icons.Filled.Brush
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Panel de gestión de capas: crear, seleccionar, mostrar/ocultar,
 * reordenar, fusionar y borrar. Se muestra de arriba hacia abajo tal
 * como se ven en el lienzo (la última capa de la lista aparece primero).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayersSheet(viewModel: DrawingViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Capas", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { viewModel.addLayer() }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva capa")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            viewModel.layers.asReversed().forEach { layer ->
                LayerRow(
                    layer = layer,
                    isActive = layer.id == viewModel.activeLayerId,
                    canDeleteOrMerge = viewModel.layers.size > 1,
                    onSelect = { viewModel.selectLayer(layer.id) },
                    onToggleVisible = { viewModel.toggleLayerVisibility(layer.id) },
                    onMoveUp = { viewModel.moveLayerUp(layer.id) },
                    onMoveDown = { viewModel.moveLayerDown(layer.id) },
                    onDelete = { viewModel.deleteLayer(layer.id) },
                    onMergeDown = { viewModel.mergeDown(layer.id) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: LayerModel,
    isActive: Boolean,
    canDeleteOrMerge: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onMergeDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleVisible) {
            Icon(
                if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = "Mostrar/ocultar capa"
            )
        }
        Text(layer.name, modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir capa")
        }
        IconButton(onClick = onMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar capa")
        }
        if (canDeleteOrMerge) {
            TextButton(onClick = onMergeDown) { Text("Fusionar") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Borrar capa")
            }
        }
    }
}

/**
 * Selector de color personalizado, usando matiz/saturación/brillo (HSV).
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
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)

                Text("Saturación")
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)

                Text("Brillo")
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(previewColor) }) { Text("Usar este color") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Exporta el dibujo actual como PNG a la galería del dispositivo.
 * Recorre las capas visibles de abajo hacia arriba, agrupando cada una
 * en su propia "capa offscreen" (canvas.saveLayer) para que el borrador
 * de una capa no afecte a las demás — el mismo esquema que el lienzo en vivo.
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
    val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    viewModel.layers.forEach { layer ->
        if (!layer.visible) return@forEach
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        layer.strokes.forEach { stroke ->
            val androidPath = stroke.path.asAndroidPath()
            val isEraser = stroke.brushType == BrushType.BORRADOR
            paint.xfermode = if (isEraser) clearXfermode else null
            brushLayers(stroke.brushType).forEach { (widthMul, alphaMul) ->
                val finalAlpha = (stroke.opacity * alphaMul).coerceIn(0f, 1f)
                paint.color = stroke.color.toArgb()
                paint.alpha = (finalAlpha * 255).toInt()
                paint.strokeWidth = max(1f, stroke.strokeWidthPx * widthMul)
                canvas.drawPath(androidPath, paint)
            }
        }
        paint.xfermode = null
        canvas.restoreToCount(saveCount)
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
