import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.kotlin.dsl.configure

class PersonalGraphJacocoConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("jacoco")

      extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
      }

      tasks.withType<Test>().configureEach {
        finalizedBy(tasks.withType<JacocoReport>())
      }

      tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
          xml.required.set(true)
          html.required.set(true)
        }
      }
    }
  }
}
