package com.mdmoney

import androidx.compose.ui.graphics.vector.PathParser
import com.mdmoney.ui.components.Icons24
import kotlin.test.Test
import kotlin.test.assertTrue

class IconPathsTest {
    /** The gear's centre ring must be a full circle (9..15 on both axes), not the half the parser gave. */
    @Test
    fun gear_ring_is_a_full_circle_and_the_teeth_span_the_icon() {
        val ring = PathParser().parsePathString(Icons24.Settings[0]).toPath().getBounds()
        assertTrue(ring.top <= 9.01f && ring.bottom >= 14.99f && ring.left <= 9.01f && ring.right >= 14.99f, "ring was $ring")
        val gear = PathParser().parsePathString(Icons24.Settings[1]).toPath().getBounds()
        assertTrue(gear.width > 18f && gear.height > 18f, "gear was $gear")
    }
}
