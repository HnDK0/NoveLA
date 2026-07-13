import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "my.noveldokusha.convention.plugin"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "noveldokusha.android.application"
            implementationClass =
                "NoveldokushaAndroidApplicationBestPracticesConventionPlugin" // :)
        }
        register("androidLibrary") {
            id = "noveldokusha.android.library"
            implementationClass =
                "NoveldokushaAndroidLibraryBestPracticesConventionPlugin" // ;)
        }
        register("androidLibraryNoHilt") {
            id = "noveldokusha.android.library.nohilt"
            implementationClass =
                "NoveldokushaAndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "noveldokusha.android.compose"
            implementationClass =
                "NoveldokushaAndroidComposeBestPracticesConventionPlugin" // :)
        }
    }
}
