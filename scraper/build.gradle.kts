plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "my.noveldokusha.scraper"
}

dependencies {
    implementation(projects.strings)
    implementation(projects.core)
    implementation(projects.coreui)
    implementation(projects.networking)
    implementation(libs.compose.material3.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.jsoup)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.compose.androidx.material.icons.extended)

    // Lua and YAML support
    implementation(libs.luajvm)
    implementation(libs.snakeyaml)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.test.androidx.espresso.core)
}