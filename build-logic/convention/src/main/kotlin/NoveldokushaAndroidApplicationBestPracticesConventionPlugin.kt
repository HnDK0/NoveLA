import com.android.build.api.dsl.ApplicationExtension
import my.noveldokusha.convention.plugin.appConfig
import my.noveldokusha.convention.plugin.applyHilt
import my.noveldokusha.convention.plugin.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class NoveldokushaAndroidApplicationBestPracticesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {

        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }
            applyHilt()

            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = appConfig.TARGET_SDK
            }
        }
    }

}
