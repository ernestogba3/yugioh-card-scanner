package com.example.yugiohscanner.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Paleta "binder cálido" (rediseño Fase 5, 2026-06-25).
// Aesthetic: un álbum/carpeta de cartas de cuero viejo — fondos de cuero oscuro,
// texto color pergamino, acentos en oro envejecido y granate.
// ─────────────────────────────────────────────────────────────────────────────

// Cuero (fondos y superficies, de más oscuro a más elevado)
val CueroFondo       = Color(0xFF1C140F)   // fondo general (espresso muy oscuro)
val CueroMedio       = Color(0xFF292019)   // superficies / cards
val CueroClaro       = Color(0xFF362A20)   // superficies elevadas (inputs, botones 2º)
val BordeCuero       = Color(0xFF4D3B2B)   // bordes / costuras

// Oro envejecido (acento principal)
val OroEnvejecido    = Color(0xFFC9A24B)   // dorado principal (cálido, menos saturado)
val OroClaroCalido   = Color(0xFFEBD08A)   // dorado claro (textos destacados)

// Granate (acento secundario, "lacre"/sello)
val Granate          = Color(0xFF9B2D3A)

// Pergamino (texto)
val Pergamino        = Color(0xFFF0E6D2)   // texto principal (crema cálida)
val PergaminoTenue   = Color(0xFFB9A98C)   // texto secundario / muted

val RojoCalido       = Color(0xFFC75D5D)   // error (rojo cálido, no chillón)

// ── Alias de compatibilidad ──────────────────────────────────────────────────
// El resto de pantallas referencian los nombres antiguos (azul/oro). Los
// mantenemos apuntando a la nueva paleta cálida para no tocar 13 archivos.
val OroYuGiOh        = OroEnvejecido
val OroClaro         = OroClaroCalido
val AzulOscuro       = CueroFondo
val AzulMedio        = CueroMedio
val AzulCarta        = CueroClaro
val BordeSutil       = BordeCuero
val RojoAccento      = Granate
val TextoPrincipal   = Pergamino
val TextoSecundario  = PergaminoTenue

// ── Colores por tipo de carta ────────────────────────────────────────────────
// Representan el color real de cada tipo de carta Yu-Gi-Oh; se mantienen tal cual
// (ColorMagico = verde se reutiliza como "válido" en los chips de mazo).
val ColorNormal      = Color(0xFFFAE5A0)
val ColorEfecto      = Color(0xFFE8764A)
val ColorFusion      = Color(0xFF7B3F8C)
val ColorRitual      = Color(0xFF4A7BC8)
val ColorSincronia   = Color(0xFFDDDDDD)
val ColorXYZ         = Color(0xFF555555)
val ColorLink        = Color(0xFF1A5C8C)
val ColorMagico      = Color(0xFF1D9E74)
val ColorTrampa      = Color(0xFFB85CA8)
