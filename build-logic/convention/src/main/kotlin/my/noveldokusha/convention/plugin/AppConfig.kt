package my.noveldokusha.convention.plugin

import org.gradle.api.JavaVersion

internal object appConfig {
    val javaVersion = JavaVersion.VERSION_21
    const val JAVA_VERSION_STRING = "21"
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 35
    const val MIN_SDK = 26
}