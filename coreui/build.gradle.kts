plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "my.noveldokusha.coreui"
    androidResources {
        resourcePrefix = ""
    }
}

dependencies {
    implementation(projects.strings)
    implementation(projects.core)
    implementation(projects.tooling.localDatabase)
    implementation(projects.tooling.textTranslator.domain)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.compose.androidx.activity)
    implementation(libs.compose.androidx.animation)
    implementation(libs.compose.androidx.runtime.livedata)
    implementation(libs.compose.androidx.lifecycle.viewmodel)
    implementation(libs.compose.androidx.material.icons.extended)
    implementation(libs.compose.material3.android)
    implementation(libs.compose.coil)
    implementation(libs.compose.lazyColumnScrollbar)

    // Sora Editor — LGPL v2.1, (C) 2020-2026 Rosemoe (https://github.com/Rosemoe/sora-editor)
    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor.core)

    testImplementation(libs.test.junit)
}
