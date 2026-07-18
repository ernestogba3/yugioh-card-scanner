package com.example.yugiohscanner.data

import android.content.Context

/**
 * Preferencias sencillas de la app (SharedPreferences). De momento solo controla si es el
 * primer arranque, para enseñar la pantalla de bienvenida una única vez.
 */
object PreferenciasApp {
    private const val ARCHIVO = "app_prefs"
    private const val KEY_BIENVENIDA_VISTA = "bienvenida_vista"

    private fun prefs(context: Context) =
        context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    /** true la primera vez que se abre la app (hasta que se marca [marcarBienvenidaVista]). */
    fun esPrimerArranque(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_BIENVENIDA_VISTA, false)

    /** Deja constancia de que ya se mostró la bienvenida; no volverá a aparecer. */
    fun marcarBienvenidaVista(context: Context) {
        prefs(context).edit().putBoolean(KEY_BIENVENIDA_VISTA, true).apply()
    }
}
