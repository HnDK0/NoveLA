plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
}

android {
    namespace = "my.noveldokusha.data"
}

dependencies {
    implementation(projects.core)
    implementation(projects.coreui)
    implementation(projects.networking)
    implementation(projects.scraper)
    implementation(projects.tooling.localDatabase)
    implementation(projects.tooling.epubParser)
    implementation(projects.tooling.textTranslator.domain)

    implementation(libs.jsoup)
    implementation(libs.readability4j)
    implementation(libs.gson)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.timber)

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.test.junit)
}
