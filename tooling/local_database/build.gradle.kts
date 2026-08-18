plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "my.noveldokusha.tooling.local_database"

    testOptions {
        unitTests {
            // Robolectric: тесты миграций Room читают schema-json из test assets
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Экспортированные Room-схемы (27.json, 30.json) — вход для MigrationTestHelper
        getByName("test").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

ksp {
    // Room пишет schema-json текущей версии БД в schemas/ (для тестов миграций)
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(projects.core)

    // Room components
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    androidTestImplementation(libs.test.androidx.espresso.core)

    testImplementation(libs.test.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.test.androidx.junit.ktx)
    testImplementation(libs.test.androidx.core.ktx)
    testImplementation(libs.robolectric)
}
