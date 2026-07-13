plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
}

android {
    namespace = "my.noveldokusha.networking"
}

dependencies {

    implementation(projects.core)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.interceptor.brotli)
    debugImplementation(libs.okhttp.interceptor.logging)

    // Logging
    implementation(libs.timber)

    implementation(libs.jsoup)
    implementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}