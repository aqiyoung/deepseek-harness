plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "ai.deepseek.wear.shared"
  // Lowered from 37: CI runners do not ship platforms;android-37. Only depends on
  // kotlinx-serialization, so 36 compiles cleanly. Wear is unused on the phone client.
  compileSdk = 36

  defaultConfig {
    minSdk = 31
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    warningsAsErrors = false
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    allWarningsAsErrors.set(false)
  }
}

ktlint {
  android.set(true)
  ignoreFailures.set(true)
  filter {
    exclude("**/build/**")
  }
}

dependencies {
  api(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
}
