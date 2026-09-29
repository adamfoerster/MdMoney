package com.mdmoney.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdmoney.LocalDecimalSeparator
import com.mdmoney.LocalStrings
import com.mdmoney.data.formatInput
import com.mdmoney.data.parseAmount
import com.mdmoney.importer.ReviewRow
import com.mdmoney.ui.ImportPhase
import com.mdmoney.ui.ImportUiState
import com.mdmoney.ui.StatementImportModel
import com.mdmoney.ui.components.HairlineDivider
import com.mdmoney.ui.components.ReinoButton
import com.mdmoney.ui.components.ReinoButtonVariant
import com.mdmoney.ui.components.ReinoField
import com.mdmoney.ui.theme.LocalReinoColors
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Pages are drawn once at this width and zoomed from there: sharp at a few times a phone's width. */
private const val RENDER_WIDTH_PX = 2000
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f
private const val ZOOM_STEP = 1.5f

/**
 * Checking the read lines against the statement itself, for when the totals disagree: the PDF on
 * top (pinch or Ctrl+wheel to zoom, drag to move, arrows to change page), and at the bottom one line
 * at a time — its amount can be corrected, and ✓ moves on to the next. It opens on the page the line
 * was read from. All state lives in [StatementImportModel]; this only draws it.
 */
@Composable
fun AmountReviewScreen(importer: StatementImportModel, state: ImportUiState, phase: ImportPhase.Review) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val position = phase.reviewing ?: return
    val row = state.rows.getOrNull(position) ?: return
    val pageCount = phase.pageCount.coerceAtLeast(1)
    var page by remember { mutableStateOf(row.tx.page ?: 0) }
    // Each new line brings its own page into view; the user can still wander off to another.
    LaunchedEffect(row.id) { row.tx.page?.let { page = it.coerceIn(0, pageCount - 1) } }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ReinoButton("‹ ${s.importStatement}", onClick = { importer.closeAmountReview() }, variant = ReinoButtonVariant.Ghost)
            }
            HairlineDivider()
            PdfPageViewer(importer, page, pageCount, Modifier.weight(1f).fillMaxWidth())
            HairlineDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReinoButton("‹", onClick = { page-- }, variant = ReinoButtonVariant.Ghost, enabled = page > 0)
                Text(
                    s.pageProgress(page + 1, pageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = reino.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                ReinoButton("›", onClick = { page++ }, variant = ReinoButtonVariant.Ghost, enabled = page < pageCount - 1)
            }
            HairlineDivider()
            ReviewBar(importer, row, position, state.rows.size, phase)
        }
    }
}

private sealed interface PageImage {
    data object Loading : PageImage
    data object Unavailable : PageImage
    data class Ready(val bitmap: ImageBitmap) : PageImage
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun PdfPageViewer(importer: StatementImportModel, page: Int, pageCount: Int, modifier: Modifier) {
    val s = LocalStrings.current
    val reino = LocalReinoColors.current
    val image by produceState<PageImage>(PageImage.Loading, page) {
        value = PageImage.Loading
        val png = importer.renderPage(page, RENDER_WIDTH_PX)
        value = png?.let { runCatching { PageImage.Ready(it.decodeToImageBitmap()) }.getOrNull() } ?: PageImage.Unavailable
    }
    var zoom by remember(page) { mutableStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(Size.Zero) }

    /** Keeps the zoomed page covering the box, so it can't be dragged out of sight. */
    fun clamp(o: Offset, z: Float): Offset {
        val maxX = box.width * (z - 1) / 2
        val maxY = box.height * (z - 1) / 2
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    /** Zooms by [factor] keeping the point [at] (from the box's centre) where it is. */
    fun zoomBy(factor: Float, at: Offset = Offset.Zero) {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        offset = clamp(at - (at - offset) * (next / zoom), next)
        zoom = next
    }

    Box(modifier.clipToBounds().background(reino.paperDeep).onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }) {
        when (val img = image) {
            PageImage.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = reino.brass)
            PageImage.Unavailable -> Text(
                s.pdfUnavailable,
                style = MaterialTheme.typography.bodyMedium,
                color = reino.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            is PageImage.Ready -> Image(
                bitmap = img.bitmap,
                contentDescription = s.pageProgress(page + 1, pageCount),
                modifier = Modifier.fillMaxSize()
                    .pointerInput(page) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            if (gestureZoom != 1f) zoomBy(gestureZoom, centroid - center)
                            offset = clamp(offset + pan, zoom)
                        }
                    }
                    .pointerInput(page) {
                        detectTapGestures(onDoubleTap = { tap ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            if (zoom > 1f) {
                                zoom = 1f
                                offset = Offset.Zero
                            } else {
                                zoomBy(2.5f, tap - center)
                            }
                        })
                    }
                    .pointerInput(page) {
                        // Desktop: Ctrl+wheel zooms where the pointer is; the wheel alone moves a zoomed page.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Scroll) continue
                                val change = event.changes.firstOrNull() ?: continue
                                val d = change.scrollDelta
                                if (event.keyboardModifiers.isCtrlPressed) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    zoomBy(if (d.y < 0) 1.15f else 1 / 1.15f, change.position - center)
                                } else {
                                    offset = clamp(offset - Offset(d.x, d.y) * WHEEL_STEP_PX, zoom)
                                }
                                change.consume()
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReinoButton("−", onClick = { zoomBy(1 / ZOOM_STEP) }, variant = ReinoButtonVariant.Secondary, enabled = zoom > MIN_ZOOM)
            ReinoButton("+", onClick = { zoomBy(ZOOM_STEP) }, variant = ReinoButtonVariant.Secondary, enabled = zoom < MAX_ZOOM)
        }
    }
}

private const val WHEEL_STEP_PX = 40f

/** The line under review: what was read, its amount (editable), the totals so far, and ✓ for the next. */
@Composable
private fun ReviewBar(importer: StatementImportModel, row: ReviewRow, position: Int, count: Int, phase: ImportPhase.Review) {
    val s = LocalStrings.current
    val sep = LocalDecimalSeparator.current
    val reino = LocalReinoColors.current
    var text by remember(row.id, row.tx.amount, sep) { mutableStateOf(formatInput(row.tx.amount, sep)) }

    /** Writes a typed amount back; a blank or impossible one is put back as it was. */
    fun commit() {
        val typed = parseAmount(text)
        if (typed == null || typed <= 0.0) text = formatInput(row.tx.amount, sep)
        else if (typed != row.tx.amount) importer.setAmount(row.id, typed)
    }

    Column(Modifier.fillMaxWidth().background(reino.paperDeep).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            listOfNotNull(
                s.reviewPosition(position + 1, count),
                dateText(row.tx.date),
                s.kindName(row.tx.kind),
                row.tx.doubt?.let { s.doubtText(it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = if (row.tx.verified) reino.inkFaint else reino.brassDeep,
        )
        Text(
            row.description,
            style = MaterialTheme.typography.bodyLarge,
            color = reino.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.amountLabel,
                style = MaterialTheme.typography.labelMedium,
                color = reino.inkSoft,
                modifier = Modifier.weight(1f),
            )
            if (row.isIncome) Text("+", style = MaterialTheme.typography.bodyLarge, color = reino.verdigris)
            ReinoField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (row.isIncome) reino.verdigris else reino.ink),
                contentAlignment = Alignment.CenterEnd,
                keyboardType = KeyboardType.Decimal,
                onFocusLost = { commit() },
                modifier = Modifier.width(120.dp),
            )
            Spacer(Modifier.width(12.dp))
            CheckButton(s.confirmLine) {
                commit()
                importer.confirmAndNext()
            }
        }
        ReconciliationLines(phase.result.reconciliation)
    }
}

/** A square brass button with a drawn ✓ — the brand's mono has no such glyph (see ReinoCheckbox). */
@Composable
private fun CheckButton(label: String, onClick: () -> Unit) {
    val reino = LocalReinoColors.current
    Box(
        Modifier.size(48.dp)
            .background(reino.brass, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .drawBehind {
                val w = size.width
                val path = Path().apply {
                    moveTo(w * 0.30f, w * 0.52f)
                    lineTo(w * 0.44f, w * 0.66f)
                    lineTo(w * 0.72f, w * 0.36f)
                }
                drawPath(path, color = reino.onBrass, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Square))
            },
    )
}
