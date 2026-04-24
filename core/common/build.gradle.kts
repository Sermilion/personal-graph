plugins {
  id("personalgraph.jvm.library")
}

dependencies {
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlin.logging)
  api(libs.kotlin.inject.runtime)
  ksp(libs.kotlin.inject.compiler)

  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
