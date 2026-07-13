plugins {
    alias(libs.plugins.noveldokusha.android.library.nohilt)
}

android {
    namespace = "my.noveldokusha.algorithms"
}

dependencies {
    testImplementation(libs.test.junit)
}
