plugins {
  id("personalgraph.jvm.library")
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(project(":core:common"))
  api(libs.kotlinx.serialization.core)
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.datetime)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.mockk)
}
