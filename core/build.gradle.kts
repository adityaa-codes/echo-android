plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    id("jacoco")
    `maven-publish`
    signing
}

group = "io.github.adityaa-codes"
version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0").get()

android {
    namespace = "io.github.adityaacodes.echo"
    compileSdk = 36

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            enableUnitTestCoverage = true
        }
    }
    publishing {
        singleVariant("release")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        explicitApi()
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val androidSourcesJar =
    tasks.register<Jar>("androidSourcesJar") {
        archiveClassifier.set("sources")
        from(android.sourceSets["main"].java.srcDirs)
        from("src/main/kotlin")
    }

val javadocJar =
    tasks.register<Jar>("javadocJar") {
        dependsOn("dokkaGenerateHtml")
        archiveClassifier.set("javadoc")
        from(layout.buildDirectory.dir("dokka/html"))
    }

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "echo"

            artifact(androidSourcesJar.get())
            artifact(javadocJar.get())

            pom {
                name.set("Echo Kotlin SDK")
                description.set("A robust, type-safe, and idiomatic Kotlin client for Pusher-compatible WebSocket services.")
                url.set("https://github.com/adityaacodes/echo-android")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                    }
                }
                developers {
                    developer {
                        id.set("adityaacodes")
                        name.set("Aditya A")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/adityaacodes/echo-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/adityaacodes/echo-android.git")
                    url.set("https://github.com/adityaacodes/echo-android")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = providers.gradleProperty("mavenCentralUsername").orNull
                password = providers.gradleProperty("mavenCentralPassword").orNull
            }
        }
    }
}

afterEvaluate {
    publishing {
        publications.named<MavenPublication>("release") {
            from(components["release"])
        }
    }
}

signing {
    val keyId = providers.gradleProperty("signing.keyId").orNull
    val password = providers.gradleProperty("signing.password").orNull
    val inMemoryKey = providers.gradleProperty("signing.secretKey").orNull

    if (keyId != null && password != null && inMemoryKey != null) {
        useInMemoryPgpKeys(keyId, inMemoryKey, password)
        sign(publishing.publications)
    } else if (keyId != null) {
        useGpgCmd()
        sign(publishing.publications)
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }

    val debugTree =
        fileTree(
            "${project.buildDir}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        ) {
            exclude(
                "**/R.class",
                "**/R$*.class",
                "**/BuildConfig.*",
                "**/Manifest*.*",
            )
        }

    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(project.buildDir) {
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
}
