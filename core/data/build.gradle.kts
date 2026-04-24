plugins {
  id("personalgraph.jvm.library")
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(project(":core:common"))
  api(project(":core:domain"))

  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.datetime)
  implementation(libs.commonmark)
  implementation(libs.kaml)

  api(libs.kotlin.inject.runtime)
  ksp(libs.kotlin.inject.compiler)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(project(":core:testing"))
}
