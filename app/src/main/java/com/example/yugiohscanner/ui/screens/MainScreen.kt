package com.example.yugiohscanner.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yugiohscanner.data.PreferenciasApp
import com.example.yugiohscanner.ui.components.ToastEscaneo
import com.example.yugiohscanner.ui.viewmodel.ColeccionViewModel
import com.example.yugiohscanner.ui.theme.AzulMedio
import com.example.yugiohscanner.ui.theme.FondoGradiente
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.TextoSecundario

sealed class AppScreen {
    object Scanner : AppScreen()
    object Coleccion : AppScreen()
    object Mazos : AppScreen()
    object Ajustes : AppScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(coleccionViewModel: ColeccionViewModel = viewModel()) {
    val context = LocalContext.current
    // Primer arranque: bienvenida una sola vez, antes del contenido normal.
    var mostrarBienvenida by remember { mutableStateOf(PreferenciasApp.esPrimerArranque(context)) }
    if (mostrarBienvenida) {
        BienvenidaScreen(onEmpezar = {
            PreferenciasApp.marcarBienvenidaVista(context)
            mostrarBienvenida = false
        })
        return
    }

    var pantallaActual by remember { mutableStateOf<AppScreen>(AppScreen.Scanner) }
    // Aviso tras guardar una carta (misma instancia de ColeccionViewModel que usan las pantallas).
    val eventoToast by coleccionViewModel.eventoToast.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoGradiente)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "YU-GI-OH! SCANNER",
                                style = MaterialTheme.typography.titleLarge,
                                color = OroYuGiOh,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .height(2.dp)
                                    .width(48.dp)
                                    .background(OroYuGiOh, RoundedCornerShape(1.dp))
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = AzulMedio,
                    tonalElevation = 0.dp
                ) {
                    val colores = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = OroClaro,
                        indicatorColor = OroYuGiOh,
                        unselectedIconColor = TextoSecundario,
                        unselectedTextColor = TextoSecundario
                    )
                    NavigationBarItem(
                        selected = pantallaActual is AppScreen.Scanner,
                        onClick = { pantallaActual = AppScreen.Scanner },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Añadir") },
                        label = { Text("Añadir") },
                        colors = colores
                    )
                    NavigationBarItem(
                        selected = pantallaActual is AppScreen.Coleccion,
                        onClick = { pantallaActual = AppScreen.Coleccion },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Colección") },
                        label = { Text("Colección") },
                        colors = colores
                    )
                    NavigationBarItem(
                        selected = pantallaActual is AppScreen.Mazos,
                        onClick = { pantallaActual = AppScreen.Mazos },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Mazos") },
                        label = { Text("Mazos") },
                        colors = colores
                    )
                    NavigationBarItem(
                        selected = pantallaActual is AppScreen.Ajustes,
                        onClick = { pantallaActual = AppScreen.Ajustes },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                        label = { Text("Ajustes") },
                        colors = colores
                    )
                }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = pantallaActual,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "cambio-pantalla",
                modifier = Modifier.padding(innerPadding)
            ) { pantalla ->
                // Aporta el color de texto por defecto (el Box de fondo no lo hace como sí haría un Surface).
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (pantalla) {
                            is AppScreen.Scanner -> AnadirFlow()
                            is AppScreen.Coleccion -> ColeccionScreen()
                            is AppScreen.Mazos -> MazosScreen()
                            is AppScreen.Ajustes -> AjustesScreen()
                        }
                    }
                }
            }
        }

        // Aviso flotante tras guardar (sobre las pantallas, justo encima de la barra inferior).
        ToastEscaneo(
            evento = eventoToast,
            onDeshacer = { idLocal -> coleccionViewModel.deshacerGuardado(idLocal) },
            onDescartar = { coleccionViewModel.descartarToast() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .padding(bottom = 96.dp)
        )
    }
}

/** Pasos internos del flujo "Añadir cartas" (pestaña "Añadir"). */
private enum class PasoAnadir { Entrada, Escanear, Manual, PorSet }

/**
 * Flujo de la pestaña "Añadir": aterriza en la pantalla de entrada y desde ahí lleva a escanear
 * (cámara directa), búsqueda manual o el navegador por set. Cada sub-pantalla vuelve a la entrada.
 */
@Composable
private fun AnadirFlow() {
    var paso by remember { mutableStateOf(PasoAnadir.Entrada) }
    when (paso) {
        PasoAnadir.Entrada -> AddCardsEntryScreen(
            onEscanear = { paso = PasoAnadir.Escanear },
            onPorSet = { paso = PasoAnadir.PorSet },
            onManual = { paso = PasoAnadir.Manual }
        )
        PasoAnadir.Escanear -> ScannerScreen(
            abrirCamaraAlEntrar = true,
            onVolver = { paso = PasoAnadir.Entrada }
        )
        PasoAnadir.Manual -> ScannerScreen(
            onVolver = { paso = PasoAnadir.Entrada }
        )
        PasoAnadir.PorSet -> SetBrowserScreen(
            onCerrar = { paso = PasoAnadir.Entrada }
        )
    }
}
