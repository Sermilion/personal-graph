plugins {
  `kotlin-dsl`
}

group = "com.sermilion.personalgraph.buildlogic"

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.ksp.gradlePlugin)
  compileOnly(libs.detekt.gradlePlugin)
  compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
  plugins {
    register("jvmLibrary") {
      id = "personalgraph.jvm.library"
      implementationClass = "PersonalGraphJvmLibraryConventionPlugin"
    }
    register("application") {
      id = "personalgraph.application"
      implementationClass = "PersonalGraphApplicationConventionPlugin"
    }
    register("detekt") {
      id = "personalgraph.detekt"
      implementationClass = "PersonalGraphDetektConventionPlugin"
    }
    register("spotless") {
      id = "personalgraph.spotless"
      implementationClass = "PersonalGraphSpotlessConventionPlugin"
    }
    register("jacoco") {
      id = "personalgraph.jacoco"
      implementationClass = "PersonalGraphJacocoConventionPlugin"
    }
  }
}
