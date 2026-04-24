plugins {
  id("personalgraph.application")
  alias(libs.plugins.kotlin.serialization)
}

application {
  mainClass.set("com.sermilion.personalgraph.cli.MainKt")
  applicationName = "personal-graph-cli"
}

dependencies {
  implementation(project(":core:common"))
  implementation(project(":core:domain"))
  implementation(project(":core:data"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.clikt)
  implementation(libs.logback.classic)
  implementation(libs.kotlin.logging)

  implementation(libs.kotlin.inject.runtime)
  ksp(libs.kotlin.inject.compiler)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(project(":core:testing"))
}
