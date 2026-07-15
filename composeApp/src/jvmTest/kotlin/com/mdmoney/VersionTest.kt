package com.mdmoney

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The version is declared in four places that no compiler can reconcile: gradle.properties (the
 * source of truth), the generated [AppVersion], CHANGELOG.md, and the iOS project. They have drifted
 * before (Android 0.1.0 vs desktop 1.0.0 vs iOS 1.0), so the agreement is asserted here — bumping
 * the version without writing release notes fails the build.
 */
class VersionTest {

    private val root = File(System.getProperty("mdmoney.projectRoot") ?: ".")

    private val declaredVersion: String = File(root, "gradle.properties").readLines()
        .firstNotNullOfOrNull { line ->
            line.trim().takeIf { it.startsWith("mdmoney.version=") }?.substringAfter("=")?.trim()
        } ?: error("mdmoney.version not found in gradle.properties")

    @Test
    fun version_is_semver() {
        assertTrue(
            Regex("""^\d+\.\d+\.\d+$""").matches(declaredVersion),
            "version must be MAJOR.MINOR.PATCH, was '$declaredVersion'",
        )
    }

    @Test
    fun generated_app_version_matches_gradle_properties() {
        assertEquals(declaredVersion, AppVersion.VERSION, "AppVersion is generated from the build")
    }

    @Test
    fun changelog_documents_the_current_version() {
        val changelog = File(root, "CHANGELOG.md")
        assertTrue(changelog.isFile, "CHANGELOG.md must exist at ${changelog.absolutePath}")

        // The newest release heading, ignoring the running "Unreleased" section above it.
        val newest = Regex("""^##\s+\[(\d+\.\d+\.\d+)]""", RegexOption.MULTILINE)
            .find(changelog.readText())?.groupValues?.get(1)
        assertNotNull(newest, "no `## [x.y.z]` release heading in CHANGELOG.md")
        assertEquals(
            declaredVersion,
            newest,
            "CHANGELOG.md's newest entry must be the current version — write the release notes for it",
        )
    }

    @Test
    fun ios_project_matches_the_declared_version() {
        // Xcode can't read gradle.properties, so the pbxproj holds its own copy of the version.
        val pbxproj = File(root, "iosApp/iosApp.xcodeproj/project.pbxproj")
        assertTrue(pbxproj.isFile, "iOS project not found at ${pbxproj.absolutePath}")

        val marketing = Regex("""MARKETING_VERSION = ([^;]+);""").findAll(pbxproj.readText())
            .map { it.groupValues[1].trim() }
            .toList()
        assertTrue(marketing.isNotEmpty(), "no MARKETING_VERSION in the iOS project")
        marketing.forEach { assertEquals(declaredVersion, it, "iOS MARKETING_VERSION is stale") }
    }
}
