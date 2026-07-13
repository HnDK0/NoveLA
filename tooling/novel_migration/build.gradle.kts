plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
}

android {
    namespace = "my.noveldokusha.tooling.novel_migration"
}

dependencies {
    implementation(projects.core)
    implementation(projects.coreui)
    implementation(projects.strings)
    implementation(projects.data)
    implementation(projects.scraper)
    implementation(projects.tooling.localDatabase)
    implementation(projects.features.globalSourceSearch)
    implementation(projects.navigation)

    implementation(libs.timber)
    implementation(libs.hilt.android)
    implementation(libs.compose.androidx.lifecycle.viewmodel)
    implementation(libs.compose.androidx.activity)
    implementation(libs.compose.material3.android)
    implementation(libs.compose.androidx.material.icons.extended)
    implementation(libs.compose.coil)
    implementation(libs.androidx.appcompat)
}
