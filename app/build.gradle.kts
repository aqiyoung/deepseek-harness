import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

// Version properties loaded from Config/Version.properties
val dshAndroidVersionFile = rootProject.file("Config/Version.properties")
val dshAndroidVersionProperties = Properties().apply {
  if (!dshAndroidVersionFile.isFile) {
    error("Missing Android version properties at ${dshAndroidVersionFile.absolutePath}")
  }
  dshAndroidVersionFile.inputStream().use { load(it) }
}
val dshAndroidVersionName = dshAndroidVersionProperties.getProperty("DSH_ANDROID_VERSION_NAME")?.trim()
  ?: error("Missing DSH_ANDROID_VERSION_NAME in Config/Version.properties")
val dshAndroidVersionCode = dshAndroidVersionProperties.getProperty("DSH_ANDROID_VERSION_CODE")?.trim()?.toIntOrNull()
  ?: error("Invalid DSH_ANDROID_VERSION_CODE in Config/Version.properties")

android {
  namespace = "ai.deepseek.harness"
  compileSdk = 36

  // Release signing: OPENCLAW_ANDROID_* Gradle properties set by GitHub Actions env.
  val hasSigning = project.hasProperty("OPENCLAW_ANDROID_STORE_FILE")

  if (hasSigning) {
    signingConfigs {
      create("release") {
        storeFile = project.findProperty("OPENCLAW_ANDROID_STORE_FILE")?.let { file(it) }
        storePassword = project.findProperty("OPENCLAW_ANDROID_STORE_PASSWORD") as String?
        keyAlias = project.findProperty("OPENCLAW_ANDROID_KEY_ALIAS") as String?
        keyPassword = project.findProperty("OPENCLAW_ANDROID_KEY_PASSWORD") as String?
      }
    }
  }

  defaultConfig {
    applicationId = "ai.deepseek.harness"
    minSdk = 31
    targetSdk = 36
    versionCode = dshAndroidVersionCode
    versionName = dshAndroidVersionName
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      isMinifyEnabled = false
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources {
      excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    allWarningsAsErrors.set(false)
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  debugImplementation(libs.androidx.compose.ui.tooling)

  implementation(libs.material)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.androidx.security.crypto)
  implementation(libs.okhttp)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)
}