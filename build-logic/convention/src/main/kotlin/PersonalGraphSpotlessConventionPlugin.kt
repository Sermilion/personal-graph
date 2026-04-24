import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class PersonalGraphSpotlessConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("com.diffplug.spotless")

      extensions.configure<SpotlessExtension> {
        kotlin {
          target("src/**/*.kt")
          ktlint("1.4.0")
            .editorConfigOverride(
              mapOf(
                "indent_size" to "2",
                "ktlint_standard_no-wildcard-imports" to "enabled",
                "ktlint_standard_filename" to "enabled",
              ),
            )
        }
        kotlinGradle {
          target("*.gradle.kts", "**/*.gradle.kts")
          ktlint("1.4.0")
            .editorConfigOverride(
              mapOf("indent_size" to "2"),
            )
        }
      }
    }
  }
}
