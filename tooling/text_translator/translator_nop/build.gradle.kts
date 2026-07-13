plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
}

android {
    namespace = "my.noveldokusha.tooling.text_translator"
}

dependencies {
    implementation(projects.core)
    implementation(projects.networking)
    implementation(projects.tooling.textTranslator.domain)
    
    // OkHttp for Gemini API calls
    implementation(libs.okhttp)

    // Gson for JSON parsing in TranslationManagerGooglePA
    implementation(libs.gson)
    
    // Free Google Translate library
    implementation("com.github.therealbush:translator:1.1.1")
}