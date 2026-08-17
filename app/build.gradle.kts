plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "ai.deepseek.harness"
  compileSdk = 36

  // Release signing: configured via Gradle properties (set by GitHub Actions from secrets)
  val storeFile = project.findProperty("DSH_ANDROID_STORE_FILE") as String?
  val storePassword = project.findProperty("DSH_ANDROID_STORE_PASSWORD") as String?
  val keyAlias = project.findProperty("DSH_ANDROID_KEY_ALIAS") as String?
  val keyPassword = project.findProperty("DSH_ANDROID_KEY_PASSWORD") as String?
  val hasSigning = storeFile != null && storePassword != null && keyAlias != null && keyPassword != null

  if (hasSigning) {
    signingConfigs {
      create("release") {
        storeFile = file(storeFile)
        storePassword = storePassword
        keyAlias = keyAlias
        keyPassword = keyPassword
      }
    }
  }

  defaultConfig {
    applicationId = "ai.deepseek.harness"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
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
}