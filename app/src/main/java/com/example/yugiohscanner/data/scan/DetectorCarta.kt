package com.example.yugiohscanner.data.scan

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Cuadrilátero (4 esquinas) de la carta detectada, con coordenadas NORMALIZADAS 0..1 respecto
 * a la imagen analizada, más la proporción ancho/alto de esa imagen. Lo usa el modo en vivo
 * para dibujar el contorno sobre la vista de cámara.
 */
data class CuadrilateroNorm(val puntos: List<PointF>, val aspecto: Float)

/**
 * Detección del rectángulo de la carta con OpenCV (el "OCR avanzado" de verdad).
 *
 * En vez de obligar al usuario a alinear el nombre en una franja fija, busca el contorno
 * cuadrilátero más grande del encuadre (la carta), corrige su perspectiva para verla "de
 * frente" y recorta la franja del nombre de la carta ya rectificada. Así el escaneo funciona
 * aunque la carta esté inclinada, girada o descentrada: solo tiene que caber en cámara.
 *
 * Robustez ante ruido y poca luz:
 *  - CLAHE (ecualización adaptativa) para realzar el contraste con poca luz.
 *  - Filtro bilateral, que suaviza el ruido del fondo SIN difuminar los bordes de la carta.
 *  - Canny con umbrales automáticos (calculados a partir del brillo medio de la escena).
 *  - Segunda estrategia con umbral adaptativo si Canny no encuentra la carta.
 *
 * Todo es local y síncrono; debe llamarse en un hilo de fondo (es trabajo pesado de imagen).
 * Si no encuentra una carta clara devuelve null y la pantalla cae al recorte fijo de respaldo.
 */
object DetectorCarta {

    private const val TAG = "DetectorCarta"

    // Proporción real de una carta de Yu-Gi-Oh (ancho/alto).
    private const val CARTA_RATIO = 0.686

    // Tamaño por defecto al que se rectifica la carta. Suficiente para el nombre.
    private const val CARD_W = 720
    private const val CARD_H = 1050

    // Resolución ALTA para leer el passcode (dígitos diminutos de la esquina inferior).
    const val CARD_W_ALTA = 1000

    // Franja del nombre dentro de la carta YA rectificada (en fracciones). El nombre va arriba,
    // ocupando casi todo el ancho; dejamos fuera el icono de atributo de la esquina derecha.
    private const val NOMBRE_X = 0.035f
    private const val NOMBRE_TOP = 0.040f
    private const val NOMBRE_ANCHO = 0.80f
    private const val NOMBRE_ALTO = 0.095f

    // Passcode: 8 dígitos en la esquina INFERIOR-IZQUIERDA de la carta. Caja generosa porque su
    // posición varía un poco entre ediciones; el OCR luego extrae los 8 dígitos con regex.
    private const val PASSCODE_X = 0.030f
    private const val PASSCODE_TOP = 0.948f
    private const val PASSCODE_ANCHO = 0.45f
    private const val PASSCODE_ALTO = 0.040f

    // Set code (p. ej. "LOB-EN001"): esquina INFERIOR-DERECHA en las cartas modernas.
    private const val SETCODE_X = 0.560f
    private const val SETCODE_TOP = 0.930f
    private const val SETCODE_ANCHO = 0.410f
    private const val SETCODE_ALTO = 0.038f

    // Lado máximo para la fase de detección (acelera findContours sin perder el cuadrilátero).
    private const val LADO_DETECCION = 1280.0

    // Área mínima del cuadrilátero respecto al encuadre para aceptarlo como carta.
    private const val AREA_MINIMA_FRAC = 0.12

    /** Carga la parte nativa de OpenCV una sola vez (la trae el propio artefacto de Maven). */
    private val iniciado: Boolean by lazy {
        OpenCVLoader.initLocal().also { ok ->
            if (!ok) Log.e(TAG, "OpenCV no se pudo inicializar")
        }
    }

    /** Indica si OpenCV está listo (para que la UI sepa si el modo en vivo está disponible). */
    fun disponible(): Boolean = iniciado

    /**
     * Devuelve el recorte del NOMBRE de la carta detectada y rectificada, o null si no se
     * detecta ninguna carta con suficiente confianza. Pensado para la captura (modo completo).
     */
    fun recortarNombre(bitmap: Bitmap): Bitmap? {
        val carta = detectarYRectificar(bitmap) ?: return null
        return recortarNombreDe(carta)
    }

    /** Recorta la franja del NOMBRE de una carta YA rectificada. */
    fun recortarNombreDe(carta: Bitmap): Bitmap =
        recortar(carta, NOMBRE_X, NOMBRE_TOP, NOMBRE_ANCHO, NOMBRE_ALTO)

    /** Recorta la esquina del PASSCODE (8 dígitos) de una carta YA rectificada. */
    fun recortarPasscodeDe(carta: Bitmap): Bitmap =
        recortar(carta, PASSCODE_X, PASSCODE_TOP, PASSCODE_ANCHO, PASSCODE_ALTO)

    /** Recorta la esquina del SET CODE de una carta YA rectificada. */
    fun recortarSetCodeDe(carta: Bitmap): Bitmap =
        recortar(carta, SETCODE_X, SETCODE_TOP, SETCODE_ANCHO, SETCODE_ALTO)

    /** Recorta una región (fracciones 0..1) de un bitmap, con límites seguros. */
    private fun recortar(carta: Bitmap, fx: Float, ftop: Float, fw: Float, fh: Float): Bitmap {
        val x = (carta.width * fx).toInt().coerceIn(0, carta.width - 1)
        val y = (carta.height * ftop).toInt().coerceIn(0, carta.height - 1)
        val w = (carta.width * fw).toInt().coerceIn(1, carta.width - x)
        val h = (carta.height * fh).toInt().coerceIn(1, carta.height - y)
        return Bitmap.createBitmap(carta, x, y, w, h)
    }

    /**
     * Detecta la carta y la devuelve rectificada (vista de frente), o null.
     * @param anchoSalida ancho del bitmap rectificado (el alto se calcula con la proporción real).
     *   Usa [CARD_W_ALTA] para leer el passcode con más nitidez.
     */
    fun detectarYRectificar(bitmap: Bitmap, anchoSalida: Int = CARD_W): Bitmap? {
        if (!iniciado) return null

        val w = anchoSalida
        val h = (anchoSalida / CARTA_RATIO).toInt()

        val original = Mat()
        Utils.bitmapToMat(bitmap, original) // RGBA
        try {
            val esquinas = encontrarCuadrilatero(original, rapido = false) ?: return null

            // Si el cuadrilátero sale claramente apaisado, la carta no está vertical:
            // mejor caer al respaldo que producir una rectificación deformada.
            val (tl, tr, br, bl) = esquinas
            val anchoMedio = (distancia(tl, tr) + distancia(bl, br)) / 2
            val altoMedio = (distancia(tl, bl) + distancia(tr, br)) / 2
            if (anchoMedio > altoMedio * 1.1) {
                Log.d(TAG, "Carta apaisada: se descarta y se usa el respaldo")
                return null
            }

            val rectificada = rectificar(original, esquinas, w, h)
            try {
                val salida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(rectificada, salida)
                return salida
            } finally {
                rectificada.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo detectando la carta: ${e.message}")
            return null
        } finally {
            original.release()
        }
    }

    /**
     * Modo EN VIVO: detección ligera y rápida para cada frame de la cámara. Devuelve las 4
     * esquinas normalizadas (0..1) para dibujar el contorno sobre la vista previa, o null.
     * No rectifica ni recorta; solo localiza la carta lo más rápido posible.
     */
    fun detectarEsquinasNormalizadas(bitmap: Bitmap): CuadrilateroNorm? {
        if (!iniciado) return null
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        try {
            val esquinas = encontrarCuadrilatero(mat, rapido = true) ?: return null
            val w = mat.width().toFloat()
            val h = mat.height().toFloat()
            if (w <= 0f || h <= 0f) return null
            val puntos = esquinas.map { PointF((it.x / w).toFloat(), (it.y / h).toFloat()) }
            return CuadrilateroNorm(puntos, w / h)
        } catch (e: Exception) {
            return null
        } finally {
            mat.release()
        }
    }

    /**
     * Busca el cuadrilátero convexo más grande (la carta) y devuelve sus 4 esquinas EN
     * COORDENADAS DEL BITMAP ORIGINAL, ordenadas: arriba-izq, arriba-der, abajo-der, abajo-izq.
     *
     * @param rapido true para el modo en vivo (una sola pasada ligera); false para la captura
     *   (pipeline completo con CLAHE + bilateral + segunda estrategia adaptativa).
     */
    private fun encontrarCuadrilatero(original: Mat, rapido: Boolean): Array<Point>? {
        // 1) Reducir para que la detección sea rápida.
        val escala = min(1.0, LADO_DETECCION / max(original.width(), original.height()))
        val peq = Mat()
        if (escala < 1.0) {
            Imgproc.resize(original, peq, Size(original.width() * escala, original.height() * escala))
        } else {
            original.copyTo(peq)
        }

        val gris = Mat()
        val proc = Mat()
        try {
            Imgproc.cvtColor(peq, gris, Imgproc.COLOR_RGBA2GRAY)
            val areaImagen = (peq.width() * peq.height()).toDouble()

            if (rapido) {
                // Ligero: solo un desenfoque suave para el modo en vivo.
                Imgproc.GaussianBlur(gris, proc, Size(5.0, 5.0), 0.0)
            } else {
                // Completo: realza contraste (poca luz) y quita ruido conservando bordes.
                val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                clahe.apply(gris, gris)
                Imgproc.bilateralFilter(gris, proc, 9, 75.0, 75.0)
            }

            // Estrategia 1: bordes con Canny de umbrales automáticos.
            val bordes = cannyAuto(proc)
            var quad = buscarMayorCuadrilatero(bordes, areaImagen)
            bordes.release()

            // Estrategia 2 (solo captura): umbral adaptativo, útil con poca luz o poco contraste.
            if (quad == null && !rapido) {
                val adapt = Mat()
                Imgproc.adaptiveThreshold(
                    proc, adapt, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 4.0
                )
                quad = buscarMayorCuadrilatero(adapt, areaImagen)
                adapt.release()
            }

            val puntos = quad ?: return null

            // Mapear de la imagen reducida a la original y ordenar las esquinas.
            val factor = if (escala < 1.0) 1.0 / escala else 1.0
            val escalados = puntos.map { Point(it.x * factor, it.y * factor) }
            return ordenarEsquinas(escalados)
        } finally {
            peq.release()
            gris.release()
            proc.release()
        }
    }

    /** Canny con umbrales calculados a partir del brillo medio (auto-Canny). */
    private fun cannyAuto(gris: Mat): Mat {
        val media = Core.mean(gris).`val`[0]
        val inferior = max(0.0, 0.66 * media)
        val superior = min(255.0, 1.33 * media)
        val bordes = Mat()
        Imgproc.Canny(gris, bordes, inferior, superior)
        return bordes
    }

    /**
     * Sobre una máscara de bordes/binaria, cierra huecos, busca contornos externos y devuelve
     * el mayor cuadrilátero convexo (4 puntos, sin ordenar) cuya área supere el mínimo, o null.
     */
    private fun buscarMayorCuadrilatero(mascara: Mat, areaImagen: Double): Array<Point>? {
        // Cerrar huecos en los bordes para que el contorno de la carta quede continuo.
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(mascara, mascara, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()

        val contornos = ArrayList<MatOfPoint>()
        val jerarquia = Mat()
        Imgproc.findContours(
            mascara, contornos, jerarquia,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        jerarquia.release()

        var mejor: Array<Point>? = null
        var mejorArea = 0.0
        for (c in contornos) {
            val c2f = MatOfPoint2f(*c.toArray())
            val perimetro = Imgproc.arcLength(c2f, true)
            val aprox = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, aprox, 0.02 * perimetro, true)
            c2f.release()

            if (aprox.total() == 4L) {
                val poligono = MatOfPoint(*aprox.toArray())
                val esConvexo = Imgproc.isContourConvex(poligono)
                val area = Imgproc.contourArea(aprox)
                poligono.release()
                if (esConvexo && area > mejorArea && area > areaImagen * AREA_MINIMA_FRAC) {
                    mejorArea = area
                    mejor = aprox.toArray()
                }
            }
            aprox.release()
            c.release()
        }
        return mejor
    }

    /** Ordena 4 puntos como tl, tr, br, bl usando la suma y la diferencia de coordenadas. */
    private fun ordenarEsquinas(p: List<Point>): Array<Point> {
        val porSuma = p.sortedBy { it.x + it.y }     // menor suma = arriba-izq; mayor = abajo-der
        val tl = porSuma.first()
        val br = porSuma.last()
        val porDif = p.sortedBy { it.y - it.x }       // menor (y-x) = arriba-der; mayor = abajo-izq
        val tr = porDif.first()
        val bl = porDif.last()
        return arrayOf(tl, tr, br, bl)
    }

    /** Corrige la perspectiva: mapea el cuadrilátero a un rectángulo recto w x h. */
    private fun rectificar(original: Mat, esquinas: Array<Point>, w: Int, h: Int): Mat {
        val src = MatOfPoint2f(esquinas[0], esquinas[1], esquinas[2], esquinas[3])
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(w - 1.0, 0.0),
            Point(w - 1.0, h - 1.0),
            Point(0.0, h - 1.0)
        )
        val transform = Imgproc.getPerspectiveTransform(src, dst)
        val salida = Mat()
        Imgproc.warpPerspective(
            original, salida, transform,
            Size(w.toDouble(), h.toDouble())
        )
        src.release()
        dst.release()
        transform.release()
        return salida
    }

    private fun distancia(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
