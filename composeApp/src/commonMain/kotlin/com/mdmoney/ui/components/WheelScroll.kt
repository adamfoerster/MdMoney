package com.mdmoney.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * A horizontally scrolling row that a desktop mouse can also drive. Plain `horizontalScroll` only
 * answers touch and Shift+wheel, so a row of chips looked stuck on desktop; here the ordinary
 * (vertical) wheel scrolls it sideways too.
 */
fun Modifier.chipRowScroll(state: ScrollState? = null): Modifier = composed {
    val scroll = state ?: rememberScrollState()
    val scope = rememberCoroutineScope()
    this
        .pointerInput(scroll) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.firstOrNull() ?: continue
                    val d = change.scrollDelta
                    val delta = if (d.x != 0f) d.x else d.y
                    // Only swallow the wheel while the row can still move that way, so the sheet's
                    // own vertical scroll takes over at either end.
                    val canMove = if (delta > 0) scroll.canScrollForward else scroll.canScrollBackward
                    if (delta != 0f && canMove) {
                        change.consume()
                        scope.launch { scroll.scrollBy(delta * WHEEL_STEP_PX) }
                    }
                }
            }
        }
        .horizontalScroll(scroll)
}

private const val WHEEL_STEP_PX = 40f
