package my.noveldokusha.convention.plugin

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.configureAndroid(
    commonExtension: CommonExtension
) {
    commonExtension.apply {
        compileSdk = appConfig.COMPILE_SDK

        defaultConfig.apply {
            minSdk = appConfig.MIN_SDK

            testInstrumentationRunnerArguments["clearPackageData"] = "true"
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildFeatures.apply {
            buildConfig = true
        }

        compileOptions.apply {
            sourceCompatibility = appConfig.javaVersion
            targetCompatibility = appConfig.javaVersion
        }

        lint.apply {
            showAll = true
            abortOnError = false
            lintConfig = rootProject.file("lint.xml")
        }

        testOptions.apply {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }

    configureKotlin()
}

private fun Project.configureKotlin() {
    // Use withType to workaround https://youtrack.jetbrains.com/issue/KT-55947
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            // Set JVM target to 21
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                // Kotlin 2.2+: -jvm-default menggunakan 'enable' (menggantikan nilai lama 'all-compatibility')
                "-jvm-default=enable",
            )
        }
    }
}
