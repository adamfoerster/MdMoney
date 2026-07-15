import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/** SemVer from gradle.properties — see the note there before changing how this is sourced. */
val appVersion: String = providers.gradleProperty("mdmoney.version").get()

/** Android needs a monotonic integer: 0.1.0 -> 100, 1.2.3 -> 10203, so patch/minor stay ordered. */
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(appVersion)
    ?.destructured
    ?.let { (major, minor, patch) -> major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt() }
    ?: error("mdmoney.version must be MAJOR.MINOR.PATCH, was '$appVersion'")

// Hands the version to common code, so the app can show what it is without a per-platform hook.
val generateAppVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/appVersion")
    inputs.property("version", appVersion)
    outputs.dir(outputDir)
    doLast {
        outputDir.get().file("com/mdmoney/AppVersion.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.mdmoney

                /** Generated from `mdmoney.version` in gradle.properties — do not edit by hand. */
                object AppVersion {
                    const val VERSION: String = "$appVersion"
                }

                """.trimIndent(),
            )
        }
    }
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateAppVersion)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.documentfile)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

android {
    namespace = "com.mdmoney"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mdmoney"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

compose.resources {
    packageOfResClass = "com.mdmoney.resources"
    generateResClass = always
}

tasks.withType<Test> {
    systemProperty("mdmoney.projectRoot", rootDir.absolutePath)
}

compose.desktop {
    application {
        mainClass = "com.mdmoney.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MdMoney"
            packageVersion = appVersion
        }
    }
}
