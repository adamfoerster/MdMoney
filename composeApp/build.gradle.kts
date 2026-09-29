import java.net.URI
import java.security.MessageDigest
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

/**
 * The llama.cpp build the iOS app links against (statement import). Pinned by tag and checksum so a
 * rebuilt release can never slip in unnoticed; bump both together.
 */
val llamaCppTag = "b11234"
val llamaXcframeworkSha256 = "2dbad83a3967c901308a66f0a91bab65f4d2e09a09695a2a18edfeafc462ddf1"
// A fixed path (the tag is only a task input): the Xcode project copies llama.framework from here
// into the app bundle for device builds.
val llamaXcframeworkRoot = layout.buildDirectory.dir("llama")
val llamaIosFrameworkDir = llamaXcframeworkRoot.map { it.dir("build-apple/llama.xcframework/ios-arm64") }

val downloadLlamaXcframework by tasks.registering {
    group = "mdmoney"
    description = "Downloads and unpacks llama.cpp's xcframework for the iOS build."
    val root = llamaXcframeworkRoot
    val tag = llamaCppTag
    val expected = llamaXcframeworkSha256
    inputs.property("tag", tag)
    outputs.dir(root)
    doLast {
        val dir = root.get().asFile.apply { mkdirs() }
        val zip = File(dir, "llama-$tag.zip")
        if (!zip.isFile) {
            val url = "https://github.com/ggml-org/llama.cpp/releases/download/$tag/llama-$tag-xcframework.zip"
            URI(url).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(zip.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (digest != expected) {
            zip.delete()
            error("llama xcframework checksum mismatch: $digest")
        }
        project.copy { from(project.zipTree(zip)); into(dir) }
    }
}
tasks.matching { it.name.startsWith("cinteropLlama") || it.name.startsWith("linkDebugFrameworkIosArm64") || it.name.startsWith("linkReleaseFrameworkIosArm64") }
    .configureEach { dependsOn(downloadLlamaXcframework) }

/**
 * Android: java-llama.cpp's jar already carries an arm64 `libjllama.so`, which needs the NDK's
 * `libc++_shared.so` and `libomp.so` beside it. This lays the three out as jniLibs, so the app loads
 * the model engine straight from the APK and no C++ is compiled here. Other ABIs simply have no
 * engine and import with the simple parser.
 */
val llamaAndroidJniLibs = layout.buildDirectory.dir("generated/llamaJniLibs")

kotlin {
    /**
     * Every JVM-flavoured target is built with **JDK 21**, whichever JDK happens to run Gradle:
     * Gradle picks (or provisions) the toolchain, so desktop, Android and the tests compile the same
     * way on every machine. `gradle/gradle-daemon-jvm.properties` asks for the same version for the
     * daemon itself. The `jvmTarget`s below and Android's `compileOptions` must stay on 21 with it —
     * a mismatch between the Kotlin and Java halves fails the Android build outright.
     */
    jvmToolchain(21)

    // Desktop and Android share the statement-import engine (java-llama.cpp) and its file plumbing.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

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

    // llama.cpp for statement import. Its official xcframework ships a device slice only, so the
    // simulator build gets a stub (iosSimulatorArm64Main) and imports with the simple parser.
    iosArm64 {
        compilations.getByName("main").cinterops.create("llama") {
            defFile(project.file("src/nativeInterop/cinterop/llama.def"))
            compilerOpts("-F${llamaIosFrameworkDir.get().asFile.absolutePath}")
        }
        binaries.all {
            linkerOpts("-F${llamaIosFrameworkDir.get().asFile.absolutePath}", "-framework", "llama")
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
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmAndAndroidMain by getting {
            dependencies {
                implementation(libs.java.llama)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.documentfile)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.pdfbox.android)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.pdfbox)
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

    // Only for its runtime libraries (libc++_shared, libomp) — see prepareLlamaJniLibs.
    ndkVersion = "27.2.12479018"
    sourceSets["main"].jniLibs.srcDir(llamaAndroidJniLibs)

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // java-llama.cpp's desktop natives ride in its jar as resources; Android loads the
            // arm64 one from jniLibs instead, so the rest would only bloat the APK.
            excludes += "de/kherud/llama/**"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

val javaLlamaJar: Configuration by configurations.creating { isTransitive = false }
dependencies { javaLlamaJar(libs.java.llama) }

/**
 * Read from the SDK folder directly rather than `android.ndkDirectory`, which fails the whole
 * configuration when the NDK is missing. Without it the APK simply ships no engine — the import
 * screen then says the model can't run here and falls back to the simple parser.
 */
val llamaNdkDir: File = android.sdkDirectory.resolve("ndk/${android.ndkVersion}")

val prepareLlamaJniLibs by tasks.registering(Sync::class) {
    val ndkDir = llamaNdkDir
    if (ndkDir.isDirectory) {
        from(javaLlamaJar.elements.map { jars -> jars.map { zipTree(it) } }) {
            include("de/kherud/llama/Linux-Android/aarch64/libjllama.so")
        }
        from(fileTree(ndkDir.resolve("toolchains/llvm/prebuilt"))) {
            include("*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
            include("*/lib/clang/*/lib/linux/aarch64/libomp.so")
        }
    } else {
        logger.warn("NDK ${android.ndkVersion} not installed: the Android build ships without the statement-import model engine.")
    }
    eachFile { path = "arm64-v8a/$name" }
    includeEmptyDirs = false
    into(llamaAndroidJniLibs)
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareLlamaJniLibs) }

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

/**
 * Measures statement extraction on a folder of real PDFs (see `EvalStatements`). Nothing is written.
 *
 *     ./gradlew :composeApp:evalStatements -Pdir=/path/to/pdfs [-Pmodel=/path/to/model.gguf] [-Ptext]
 */
val evalStatements by tasks.registering(JavaExec::class) {
    group = "mdmoney"
    description = "Extracts every PDF statement in a folder and prints the result. -Pdir=<path> [-Pmodel=<gguf>] [-Ptext]"
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    dependsOn(jvmMain.compileTaskProvider)
    mainClass.set("com.mdmoney.tools.eval.EvalStatementsKt")
    doFirst {
        val dir = providers.gradleProperty("dir").orNull ?: error("Which folder? Pass -Pdir=/path/to/pdfs")
        args = listOfNotNull(
            dir,
            providers.gradleProperty("model").orNull ?: "-",
            "--text".takeIf { project.hasProperty("text") },
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.mdmoney.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MdMoney"
            packageVersion = appVersion

            // Generated from icon/*.svg by scripts/GenerateIcons.java, like every other platform's icon.
            macOS { iconFile.set(project.file("icons/mdmoney.icns")) }
            windows { iconFile.set(project.file("icons/mdmoney.ico")) }
            linux { iconFile.set(project.file("icons/mdmoney.png")) }
        }
    }
}
