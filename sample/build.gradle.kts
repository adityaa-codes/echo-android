plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

private fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.github.adityaacodes.echo.sample"
    compileSdk = 36

    defaultConfig {
        val sampleHost = providers.gradleProperty("ECHO_SAMPLE_HOST").orElse("10.0.2.2").get()
        val samplePort = providers.gradleProperty("ECHO_SAMPLE_PORT").orElse("8080").get()
        val sampleUseTls = providers.gradleProperty("ECHO_SAMPLE_USE_TLS").orElse("false").get()
        val sampleAppKey = providers.gradleProperty("ECHO_SAMPLE_APP_KEY").orElse("reverb-app-key").get()
        val sampleAuthEndpoint =
            providers.gradleProperty("ECHO_SAMPLE_AUTH_ENDPOINT")
                .orElse("http://10.0.2.2:8000/broadcasting/auth")
                .get()

        applicationId = "io.github.adityaacodes.echo.sample"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "ECHO_SAMPLE_HOST", sampleHost.asBuildConfigString())
        buildConfigField("int", "ECHO_SAMPLE_PORT", samplePort)
        buildConfigField("boolean", "ECHO_SAMPLE_USE_TLS", sampleUseTls)
        buildConfigField("String", "ECHO_SAMPLE_APP_KEY", sampleAppKey.asBuildConfigString())
        buildConfigField("String", "ECHO_SAMPLE_AUTH_ENDPOINT", sampleAuthEndpoint.asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":echo"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
