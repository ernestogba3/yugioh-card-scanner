package com.example.yugiohscanner.ui.screens

import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.yugiohscanner.data.model.CartaYuGiOh
import com.example.yugiohscanner.data.scan.CuadrilateroNorm
import com.example.yugiohscanner.data.scan.DetectorCarta
import com.example.yugiohscanner.data.scan.ResultadoIdentificacion
import com.example.yugiohscanner.data.scan.aBitmapVertical
import com.example.yugiohscanner.data.scan.construirCandidatos
import com.example.yugiohscanner.data.scan.distanciaMediaEsquinas
import com.example.yugiohscanner.data.scan.mezclarEsquinas
import com.example.yugiohscanner.data.scan.prepararParaOcr
import com.example.yugiohscanner.data.scan.recortarZonaNombre
import com.example.yugiohscanner.data.scan.reducirSiEsEnorme
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.max

// Marco guía con forma de carta (proporción real de una carta de Yu-Gi-Oh ~0.686), centrado.
// Con la detección de OpenCV basta con que la carta entre en el marco; no hace falta alinear
// el nombre con precisión.
private const val CARTA_RATIO = 0.686f
private const val MARCO_ANCHO = 0.72f // fracción del ancho de pantalla que ocupa el marco

// Verde de "carta detectada" para el contorno en vivo y el marco cuando hay carta.
private val VerdeDeteccion = Color(0xFF34C759)

// --- Ajustes del modo en vivo (anti-shaking + disparo automático) ---
// Frames QUIETOS seguidos antes de disparar solo en modo automático.
private const val FRAMES_PARA_AUTODISPARO = 12
// Frames sin detección que toleramos antes de borrar el contorno (evita parpadeo).
private const val GRACIA_FRAMES = 4
// Suavizado del contorno: 0 = inmóvil, 1 = sigue el crudo al instante.
private const val ALPHA_SUAVE = 0.35f
// Movimiento medio de esquinas (fracción 0..1) bajo el cual se considera la MISMA carta (suaviza).
private const val UMBRAL_MISMA_CARTA = 0.18f
// Movimiento medio de esquinas por debajo del cual la carta se considera QUIETA (para el auto).
private const val UMBRAL_ESTABLE = 0.015f

@Composable
fun CameraScreen(
    // Identifica la carta de un frame (passcode → pHash). El resultado vuelve por el callback.
    onIdentificar: (Bitmap, (ResultadoIdentificacion) -> Unit) -> Unit,
    // La carta se identificó directamente (passcode o pHash): se abre su detalle. Incluye el arte
    // sugerido (de pHash) y el set code leído, si los hay.
    onCartaIdentificada: (CartaYuGiOh, Long?, String?) -> Unit,
    // Respaldo: no se pudo identificar, pero se leyó el NOMBRE; se buscan estos candidatos.
    onTextReconocido: (List<String>) -> Unit,
    onCerrar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val cameraRef = remember { mutableStateOf<Camera?>(null) }
    var procesando by remember { mutableStateOf(false) }
    var flashEncendido by remember { mutableStateOf(false) }
    var mensajeEstado by remember { mutableStateOf("Encuadra la carta y pulsa para escanear") }

    // Modo de detección en vivo: contorno detectado de la carta y disparo automático.
    var cuadroDetectado by remember { mutableStateOf<CuadrilateroNorm?>(null) }
    var autoCaptura by remember { mutableStateOf(true) }
    // Contadores y último contorno suavizado (mutables sin recomponer, para no parpadear).
    val framesEstables = remember { intArrayOf(0) }
    val framesSinCarta = remember { intArrayOf(0) }
    val refSuave = remember { arrayOfNulls<CuadrilateroNorm>(1) }

    // Hilos de fondo: uno para la captura (OpenCV + OCR) y otro para el análisis en vivo.
    val ocrExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProviderRef.value?.unbindAll()
            ocrExecutor.shutdown()
            analysisExecutor.shutdown()
        }
    }

    // Procesa el OCR sobre un recorte ya preparado y publica los candidatos del nombre.
    // ML Kit ejecuta sus callbacks en el hilo principal, así que es seguro tocar el estado.
    fun procesarOcr(recorte: Bitmap) {
        val inputImage = InputImage.fromBitmap(recorte, 0)
        TextRecognition
            .getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(inputImage)
            .addOnSuccessListener { visionText ->
                // Solo líneas con letras de verdad: descarta números sueltos,
                // símbolos y ruido que el OCR mete en el recorte.
                val lineas = visionText.textBlocks
                    .flatMap { it.lines }
                    .map { it.text.trim() }
                    .filter { linea -> linea.count { it.isLetter() } >= 2 }

                // En vez de jugárselo todo a una lectura, generamos varias
                // hipótesis del nombre; la búsqueda local probará todas.
                val candidatos = construirCandidatos(lineas)

                if (candidatos.isEmpty()) {
                    mensajeEstado = "No se detectó el nombre. Acércate a la carta."
                    procesando = false
                } else {
                    onTextReconocido(candidatos)
                }
            }
            .addOnFailureListener {
                mensajeEstado = "Error al leer el texto. Intenta de nuevo."
                procesando = false
            }
    }

    // Respaldo cuando NO se identifica por passcode/pHash: OCR del NOMBRE sobre el frame.
    fun respaldoPorNombre(reducido: Bitmap, exigirDeteccion: Boolean) {
        ocrExecutor.execute {
            val recorteDetectado = DetectorCarta.recortarNombre(reducido)
            // En modo automático, si no se detecta la carta de verdad, no buscamos.
            if (recorteDetectado == null && exigirDeteccion) {
                ContextCompat.getMainExecutor(context).execute {
                    procesando = false
                    mensajeEstado = "Ajusta la carta: acércala o mejora la luz"
                }
                return@execute
            }
            // En manual sí se permite el recorte fijo de respaldo.
            val recorteNombre = recorteDetectado ?: recortarZonaNombre(reducido)
            val preparado = prepararParaOcr(recorteNombre)
            ContextCompat.getMainExecutor(context).execute { procesarOcr(preparado) }
        }
    }

    // Captura un frame e intenta identificar la carta por passcode (8 dígitos) y, si falla, por
    // pHash (aspecto). Si tampoco, cae al OCR del nombre (respaldo verificado).
    // [exigirDeteccion] = true (disparo automático): si NO hay carta de verdad, no busca nada.
    fun capturar(exigirDeteccion: Boolean = false) {
        val imageCapture = imageCaptureRef.value ?: return
        procesando = true
        mensajeEstado = "Identificando la carta..."
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = try {
                        image.aBitmapVertical()
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    if (bitmap == null) {
                        mensajeEstado = "Error al procesar la imagen. Intenta de nuevo."
                        procesando = false
                        return
                    }
                    val mainExecutor = ContextCompat.getMainExecutor(context)
                    // Reducir es trabajo de imagen: fuera del hilo principal.
                    ocrExecutor.execute {
                        val reducido = reducirSiEsEnorme(bitmap, 2400)
                        mainExecutor.execute {
                            onIdentificar(reducido) { resultado ->
                                when {
                                    // Identificada por passcode o pHash: abrir su detalle.
                                    resultado.carta != null -> {
                                        procesando = false
                                        onCartaIdentificada(
                                            resultado.carta, resultado.artId, resultado.setCode
                                        )
                                    }
                                    // Auto + no había ninguna carta: no buscar (evita falsos).
                                    !resultado.huboCarta && exigirDeteccion -> {
                                        procesando = false
                                        mensajeEstado = "Ajusta la carta: acércala o mejora la luz"
                                    }
                                    // Había carta pero sin passcode/pHash legibles: OCR del nombre.
                                    else -> respaldoPorNombre(reducido, exigirDeteccion)
                                }
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mensajeEstado = "Error al capturar. Intenta de nuevo."
                    procesando = false
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Marco guía con forma de carta, centrado. Calculamos su borde inferior para colocar
        // el hint justo debajo.
        val marcoAnchoDp = maxWidth * MARCO_ANCHO
        val marcoAltoDp = marcoAnchoDp / CARTA_RATIO
        val bordeInferiorGuia = (maxHeight - marcoAltoDp) / 2 + marcoAltoDp

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val cameraProvider = future.get()
                        cameraProviderRef.value = cameraProvider

                        // Misma proporción 4:3 en vista previa, análisis y captura: así el
                        // contorno detectado en vivo coincide con lo que se ve en pantalla.
                        val selector43 = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build()

                        val preview = Preview.Builder()
                            .setResolutionSelector(selector43)
                            .build()
                            .also { it.setSurfaceProvider(surfaceProvider) }

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setResolutionSelector(selector43)
                            .build()
                        imageCaptureRef.value = imageCapture

                        // Análisis en vivo: cada frame se mira con la detección ligera de OpenCV.
                        val analisis = ImageAnalysis.Builder()
                            .setResolutionSelector(selector43)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                        analisis.setAnalyzer(analysisExecutor) { proxy ->
                            val crudo = try {
                                DetectorCarta.detectarEsquinasNormalizadas(proxy.aBitmapVertical())
                            } catch (e: Exception) {
                                null
                            } finally {
                                proxy.close()
                            }
                            // Volvemos al hilo principal para tocar el estado de Compose.
                            ContextCompat.getMainExecutor(ctx).execute {
                                if (crudo != null) {
                                    val prev = refSuave[0]
                                    // Si es la misma carta (apenas se movió), suavizamos su contorno;
                                    // si saltó lejos, lo tomamos tal cual (cambió de posición).
                                    val suave = if (prev != null &&
                                        distanciaMediaEsquinas(prev, crudo) < UMBRAL_MISMA_CARTA
                                    ) {
                                        mezclarEsquinas(prev, crudo, ALPHA_SUAVE)
                                    } else {
                                        crudo
                                    }
                                    val movimiento = if (prev != null) {
                                        distanciaMediaEsquinas(prev, suave)
                                    } else 1f

                                    refSuave[0] = suave
                                    cuadroDetectado = suave
                                    framesSinCarta[0] = 0

                                    // Auto: solo cuenta frames si la carta está QUIETA (estable).
                                    if (autoCaptura && !procesando && movimiento < UMBRAL_ESTABLE) {
                                        framesEstables[0]++
                                        if (framesEstables[0] >= FRAMES_PARA_AUTODISPARO) {
                                            framesEstables[0] = 0
                                            capturar(exigirDeteccion = true)
                                        }
                                    } else {
                                        framesEstables[0] = 0
                                    }
                                } else {
                                    // Sin detección: damos un margen de frames antes de borrar el
                                    // contorno, para que no parpadee en lecturas dudosas.
                                    framesEstables[0] = 0
                                    framesSinCarta[0]++
                                    if (framesSinCarta[0] >= GRACIA_FRAMES) {
                                        refSuave[0] = null
                                        cuadroDetectado = null
                                    }
                                }
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraRef.value = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                analisis
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val hayCarta = cuadroDetectado != null

        // Guía visual: oscurece todo menos el marco con forma de carta, con esquinas reforzadas.
        // El marco se pone verde cuando se está detectando una carta en vivo.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width * MARCO_ANCHO
            val h = w / CARTA_RATIO
            val x = (size.width - w) / 2
            val top = (size.height - h) / 2
            val scrim = Color.Black.copy(alpha = 0.55f)
            val colorBorde = if (hayCarta) VerdeDeteccion else OroYuGiOh
            val colorEsquina = if (hayCarta) VerdeDeteccion else OroClaro

            drawRect(scrim, Offset.Zero, Size(size.width, top))
            drawRect(scrim, Offset(0f, top + h), Size(size.width, size.height - (top + h)))
            drawRect(scrim, Offset(0f, top), Size(x, h))
            drawRect(scrim, Offset(x + w, top), Size(size.width - (x + w), h))

            // Borde fino del marco.
            drawRoundRect(
                color = colorBorde,
                topLeft = Offset(x, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(20f, 20f),
                style = Stroke(width = 4f)
            )

            // Esquinas reforzadas.
            val largo = 44f
            val grosor = 10f
            fun esquina(cx: Float, cy: Float, dx: Float, dy: Float) {
                drawLine(colorEsquina, Offset(cx, cy), Offset(cx + dx, cy), grosor, StrokeCap.Round)
                drawLine(colorEsquina, Offset(cx, cy), Offset(cx, cy + dy), grosor, StrokeCap.Round)
            }
            esquina(x, top, largo, largo)                      // sup. izq.
            esquina(x + w, top, -largo, largo)                 // sup. der.
            esquina(x, top + h, largo, -largo)                 // inf. izq.
            esquina(x + w, top + h, -largo, -largo)            // inf. der.
        }

        // Contorno detectado EN VIVO: dibuja el cuadrilátero de la carta sobre la vista previa.
        // Las esquinas vienen normalizadas (0..1); las mapeamos a la pantalla replicando el
        // recorte FILL_CENTER que aplica el PreviewView a la imagen 4:3.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val quad = cuadroDetectado ?: return@Canvas
            if (quad.puntos.size != 4) return@Canvas
            val escaladoW = max(size.width, size.height * quad.aspecto)
            val escaladoH = max(size.height, size.width / quad.aspecto)
            val offX = (size.width - escaladoW) / 2
            val offY = (size.height - escaladoH) / 2
            val pts = quad.puntos.map { Offset(it.x * escaladoW + offX, it.y * escaladoH + offY) }
            val ruta = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            drawPath(ruta, color = VerdeDeteccion, style = Stroke(width = 8f, cap = StrokeCap.Round))
        }

        // Botón cerrar (X) arriba a la izquierda.
        IconButton(
            onClick = onCerrar,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
        }

        // Toggle de disparo automático arriba a la derecha.
        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = if (autoCaptura) VerdeDeteccion else Color.White,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable { autoCaptura = !autoCaptura }
        ) {
            Text(
                text = if (autoCaptura) "Auto ●" else "Auto ○",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Banner de estado arriba (muestra la detección en vivo cuando no se está procesando).
        val textoBanner = when {
            procesando -> mensajeEstado
            hayCarta -> if (autoCaptura) "Carta detectada ✓ mantén firme" else "Carta detectada ✓ pulsa para escanear"
            else -> mensajeEstado
        }
        Surface(
            color = if (hayCarta && !procesando) VerdeDeteccion.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp, start = 24.dp, end = 24.dp)
        ) {
            Text(
                text = textoBanner,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Hint justo debajo del marco guía.
        Text(
            text = "Encuadra la carta completa dentro del marco",
            color = OroClaro,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = bordeInferiorGuia + 12.dp, start = 32.dp, end = 32.dp)
        )

        // Footer con 3 botones: búsqueda manual · disparador · flash.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BotonCircular(onClick = onCerrar) {
                Icon(Icons.Default.Search, contentDescription = "Búsqueda manual", tint = Color.White)
            }

            BotonDisparador(procesando = procesando, onClick = { capturar() })

            BotonCircular(
                onClick = {
                    val encender = !flashEncendido
                    cameraRef.value?.cameraControl?.enableTorch(encender)
                    flashEncendido = encender
                }
            ) {
                Text(
                    "⚡",
                    color = if (flashEncendido) OroClaro else Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun BotonCircular(onClick: () -> Unit, contenido: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        contenido()
    }
}

/** Disparador grande (60dp) dorado con anillo, estilo cámara. */
@Composable
private fun BotonDisparador(procesando: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(3.dp, OroClaro, CircleShape)
            .padding(6.dp)
            .background(OroYuGiOh, CircleShape)
            .clickable(enabled = !procesando, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (procesando) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.Black,
                strokeWidth = 3.dp
            )
        }
    }
}
