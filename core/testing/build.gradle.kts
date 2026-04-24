plugins {
  id("personalgraph.jvm.library")
}

dependencies {
  api(project(":core:common"))
  api(project(":core:domain"))

  api(libs.kotest.assertions.core)
  api(libs.kotest.framework.datatest)
  api(libs.mockk)
  api(libs.kotlinx.coroutines.test)
}
