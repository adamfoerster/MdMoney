package com.mdmoney

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The app icon is generated from the SVGs in `icon/` by `scripts/GenerateIcons.java` and then wired into
 * four build systems by hand — a manifest attribute, an Xcode resources phase, a Gradle `iconFile`.
 * Any one link can break without a compiler noticing (an app with no icon still builds), so each is
 * checked here, along with a few pixels of the rasters against the colours in the SVG.
 */
class AppIconTest {

    private val root = File(System.getProperty("mdmoney.projectRoot") ?: ".")

    private fun file(path: String) = File(root, path).also { assertTrue(it.isFile, "missing $path") }

    /** Light: background #F4EEE2, ink #28221B, coin #9C7C43. Dark: #1C1712, #F0E7D8, #C99B57. */
    @Test
    fun source_svgs_carry_the_colours_this_test_expects() {
        val light = file("icon/mdmoney-light.svg").readText()
        val dark = file("icon/mdmoney-dark.svg").readText()
        listOf("#F4EEE2", "#28221B", "#9C7C43").forEach { assertTrue(it in light, "light SVG lost $it") }
        listOf("#1C1712", "#F0E7D8", "#C99B57").forEach { assertTrue(it in dark, "dark SVG lost $it") }
    }

    @Test
    fun android_manifest_uses_the_adaptive_icon() {
        val manifest = file("composeApp/src/androidMain/AndroidManifest.xml").readText()
        assertTrue("""android:icon="@mipmap/ic_launcher"""" in manifest)
        assertTrue("""android:roundIcon="@mipmap/ic_launcher_round"""" in manifest)

        val res = "composeApp/src/androidMain/res"
        listOf("ic_launcher", "ic_launcher_round").forEach { name ->
            val adaptive = file("$res/mipmap-anydpi-v26/$name.xml").readText()
            assertTrue("@color/ic_launcher_background" in adaptive, "$name has no background")
            assertTrue("<foreground android:drawable=\"@drawable/ic_launcher_foreground\"" in adaptive)
            assertTrue("<monochrome " in adaptive, "$name has no monochrome layer for themed icons")
        }
        assertTrue("#F4EEE2" in file("$res/values/ic_launcher_background.xml").readText())
        assertTrue("#1C1712" in file("$res/values-night/ic_launcher_background.xml").readText())
        assertTrue("#28221B" in file("$res/drawable/ic_launcher_foreground.xml").readText())
        assertTrue("#F0E7D8" in file("$res/drawable-night/ic_launcher_foreground.xml").readText())
    }

    @Test
    fun android_foreground_leaves_out_the_background_and_frame() {
        // Both are square; inside a launcher's circular mask they would show as clipped corners.
        val foreground = file("composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml").readText()
        assertFalse("#F4EEE2" in foreground, "background colour leaked into the foreground layer")
        assertFalse("#C9BCA2" in foreground, "frame leaked into the foreground layer")
    }

    @Test
    fun ios_app_icon_is_an_opaque_1024_with_a_dark_variant() {
        val set = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
        val contents = file("$set/Contents.json").readText()
        assertTrue("\"AppIcon.png\"" in contents && "\"AppIcon-Dark.png\"" in contents)
        assertTrue("\"luminosity\"" in contents, "dark icon is not marked as the dark appearance")

        mapOf("AppIcon.png" to 0xF4EEE2, "AppIcon-Dark.png" to 0x1C1712).forEach { (name, background) ->
            val image = ImageIO.read(file("$set/$name"))
            assertEquals(1024, image.width, name)
            assertEquals(1024, image.height, name)
            assertFalse(image.colorModel.hasAlpha(), "$name has an alpha channel; the App Store rejects it")
            assertEquals(background, image.getRGB(20, 20) and 0xFFFFFF, "$name background")
        }
        // The coin sits at (30, 57) of the 120 viewBox: (256, 486.4) at 1024px.
        val coin = ImageIO.read(file("$set/AppIcon.png")).getRGB(256, 486) and 0xFFFFFF
        assertEquals(0x9C7C43, coin, "coin colour")
    }

    @Test
    fun xcode_project_bundles_the_asset_catalog() {
        val pbxproj = file("iosApp/iosApp.xcodeproj/project.pbxproj").readText()
        val buildFile = Regex("""(\w+) /\* Assets\.xcassets in Resources \*/ = \{isa = PBXBuildFile""")
            .find(pbxproj)?.groupValues?.get(1)
        assertTrue(buildFile != null, "Assets.xcassets is not a build file")
        val phase = Regex("""(\w+) /\* Resources \*/ = \{\s*isa = PBXResourcesBuildPhase;[^}]*${buildFile}""")
            .find(pbxproj)?.groupValues?.get(1)
        assertTrue(phase != null, "Assets.xcassets is not in a Resources build phase")
        val targetPhases = Regex("""isa = PBXNativeTarget;[\s\S]*?buildPhases = \(([^)]*)\)""")
            .find(pbxproj)?.groupValues?.get(1).orEmpty()
        assertTrue(phase in targetPhases, "the app target doesn't run the Resources phase")
        assertTrue("ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;" in pbxproj)
    }

    @Test
    fun desktop_packages_point_at_icons_that_exist() {
        val build = file("composeApp/build.gradle.kts").readText()
        val icons = Regex("""iconFile\.set\(project\.file\("([^"]+)"\)\)""").findAll(build)
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("icons/mdmoney.icns", "icons/mdmoney.ico", "icons/mdmoney.png"), icons)
        icons.forEach { file("composeApp/$it") }
    }

    @Test
    fun windows_ico_is_well_formed_and_goes_up_to_256() {
        val bytes = file("composeApp/icons/mdmoney.ico").readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, buf.getShort(0).toInt())
        assertEquals(1, buf.getShort(2).toInt(), "type 1 = icon")
        val count = buf.getShort(4).toInt()
        val sizes = (0 until count).map { i ->
            val entry = 6 + 16 * i
            val length = buf.getInt(entry + 8)
            val offset = buf.getInt(entry + 12)
            val image = ImageIO.read(ByteArrayInputStream(bytes, offset, length))
            val declared = (bytes[entry].toInt() and 0xFF).let { if (it == 0) 256 else it }
            assertEquals(declared, image.width, "entry $i declares a size its PNG doesn't have")
            image.width
        }
        assertEquals(listOf(16, 24, 32, 48, 64, 128, 256), sizes)
    }

    @Test
    fun macos_icns_is_well_formed_and_goes_up_to_1024() {
        val bytes = file("composeApp/icons/mdmoney.icns").readBytes()
        val buf = ByteBuffer.wrap(bytes)
        assertEquals("icns", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(bytes.size, buf.getInt(4), "icns header length")
        val entries = buildList {
            var at = 8
            while (at < bytes.size) {
                val length = buf.getInt(at + 4)
                val image = ImageIO.read(ByteArrayInputStream(bytes, at + 8, length - 8))
                add(String(bytes, at, 4, Charsets.US_ASCII) to image.width)
                at += length
            }
        }
        assertEquals(
            listOf("icp4" to 16, "icp5" to 32, "icp6" to 64, "ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024),
            entries,
        )
    }

    @Test
    fun desktop_window_icon_is_the_full_light_icon() {
        val vector = file("composeApp/src/commonMain/composeResources/drawable/app_icon.xml").readText()
        listOf("#F4EEE2", "#C9BCA2", "#28221B", "#9C7C43").forEach { assertTrue(it in vector, "window icon lacks $it") }
        assertTrue("app_icon" in file("composeApp/src/jvmMain/kotlin/com/mdmoney/main.kt").readText())
    }
}
