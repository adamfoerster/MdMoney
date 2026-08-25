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
    /**
     * Every JVM-flavoured target is built with **JDK 21**, whichever JDK happens to run Gradle:
     * Gradle picks (or provisions) the toolchain, so desktop, Android and the tests compile the same
     * way on every machine. `gradle/gradle-daemon-jvm.properties` asks for the same version for the
     * daemon itself. The `jvmTarget`s below and Android's `compileOptions` must stay on 21 with it —
     * a mismatch between the Kotlin and Java halves fails the Android build outright.
     */
    jvmToolchain(21)

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
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

    // The Java half of the Android build; keep it in step with the Kotlin `jvmTarget` above.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

/**
 * Rewrites a vault to the current note format (see `VaultMigration`). Previews by default:
 *
 *     ./gradlew :composeApp:migrateVault -Pvault=/path/to/vault [-Papply]
 *
 * The path is never defaulted — a task that rewrites a whole vault must be told which one.
 */
val migrateVault by tasks.registering(JavaExec::class) {
    group = "mdmoney"
    description = "Rewrites a vault's notes to the current format. -Pvault=<path> [-Papply]"
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    dependsOn(jvmMain.compileTaskProvider)
    mainClass.set("com.mdmoney.tools.MigrateVaultKt")
    doFirst {
        val vault = providers.gradleProperty("vault").orNull
            ?: error("Which vault? Pass -Pvault=/path/to/vault")
        args = listOfNotNull(vault, "--apply".takeIf { project.hasProperty("apply") })
    }
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
