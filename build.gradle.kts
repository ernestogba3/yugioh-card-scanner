// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // Disponible en el classpath; el módulo app lo aplica solo si existe google-services.json.
    alias(libs.plugins.google.services) apply false
}
