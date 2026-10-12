
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import my.noveldokusha.convention.plugin.implementation
import my.noveldokusha.convention.plugin.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType

class NoveldokushaAndroidComposeBestPracticesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            val extension = extensions.findByType<ApplicationExtension>()
                ?: extensions.getByType<LibraryExtension>()

            extension.apply {
                buildFeatures.apply {
                    compose = true
                }

                dependencies {
                    val bom = platform(libs.findLibrary("compose-bom").get())
                    implementation(bom)
                    implementation(libs.findLibrary("compose-androidx-ui").get())
                    implementation(libs.findLibrary("compose-androidx-ui-tooling").get())
                    implementation(libs.findLibrary("compose-foundation-layout").get())
                }

                testOptions.apply {
                    unitTests.apply {
                        // For Robolectric
                        isIncludeAndroidResources = true
                    }
                }
            }

            // Compose modules carry @Preview composables but no JVM tests;
            // Gradle 9.6 fails the unit-test task when 0 tests are discovered.
            val testTasks = tasks.withType(org.gradle.api.tasks.testing.Test::class.java)
            testTasks.configureEach {
                failOnNoDiscoveredTests.set(false)
            }
        }
    }
}
