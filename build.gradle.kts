plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.spotless) apply false
}

allprojects {
  group = "com.sermilion.personalgraph"
  version = "0.1.0-SNAPSHOT"
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}
