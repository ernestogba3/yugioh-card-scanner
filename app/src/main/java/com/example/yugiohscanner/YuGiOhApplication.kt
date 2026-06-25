package com.example.yugiohscanner

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Clase Application de la app. Su única función por ahora es configurar el [ImageLoader]
 * global de Coil con una caché de imágenes EN DISCO persistente.
 *
 * Encaja con la arquitectura offline-first (ver CLAUDE.md): las imágenes de las cartas viven
 * en YGOPRODeck y solo se descargan cuando hay red. Con esta caché, una vez descargada una
 * imagen se conserva en disco y se vuelve a mostrar sin internet. La caché por defecto de Coil
 * es pequeña (~2% del disco) y se purga pronto; aquí le damos un tope mayor y desactivamos el
 * respeto a las cabeceras de caché del servidor para que el guardado sea fiable.
 *
 * Registrada en AndroidManifest.xml con android:name=".YuGiOhApplication".
 */
class YuGiOhApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Caché en memoria: hasta el 25% de la RAM disponible para la app.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Caché en disco: carpeta dedicada, hasta el 10% del almacenamiento.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.10)
                    .build()
            }
            // Ignora las cabeceras Cache-Control del servidor: queremos cachear siempre
            // para poder mostrar las cartas offline.
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
