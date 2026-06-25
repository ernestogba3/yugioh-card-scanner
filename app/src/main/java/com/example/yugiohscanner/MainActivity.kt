package com.example.yugiohscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.yugiohscanner.data.catalog.CatalogImporter
import com.example.yugiohscanner.ui.screens.MainScreen
import com.example.yugiohscanner.ui.theme.YuGiOhScannerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Primer arranque: vuelca el catálogo empaquetado a Room (idempotente; en segundo
        // plano). Las siguientes veces no hace nada porque la tabla ya tiene cartas.
        lifecycleScope.launch {
            CatalogImporter.importarSiHaceFalta(applicationContext)
        }
        enableEdgeToEdge()
        setContent {
            YuGiOhScannerTheme {
                MainScreen()
            }
        }
    }
}
