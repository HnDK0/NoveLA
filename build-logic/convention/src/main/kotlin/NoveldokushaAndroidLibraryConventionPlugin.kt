import com.android.build.gradle.LibraryExtension
import my.noveldokusha.convention.plugin.appConfig
import my.noveldokusha.convention.plugin.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class NoveldokushaAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = appConfig.TARGET_SDK
                resourcePrefix = path
                    .split("""\W""".toRegex()).drop(1).distinct()
                    .joinToString(separator = "_")
                    .lowercase() + "_"

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }
            }
        }
    }
}
