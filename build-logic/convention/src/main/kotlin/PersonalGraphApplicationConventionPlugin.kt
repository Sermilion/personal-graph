import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class PersonalGraphApplicationConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("personalgraph.jvm.library")
      pluginManager.apply("application")

      extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
          allWarningsAsErrors.set(false)
        }
      }
    }
  }
}
