package nl.jordy.iptv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Pauzemoment card's icons: toilet, bottle, snack bowl. Plain white shapes on a 24×24 grid,
 * tinted where they're drawn; not emoji, for the same reason as [AvatarFaces].
 */
object BreakIcons {
    val all: List<ImageVector> by lazy { listOf(toilet, bottle, snack) }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.shape(holes: Boolean = false, block: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.White), pathFillType = if (holes) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block)

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private val toilet by lazy {
        icon("Wc") {
            // Tank, seat, then the bowl narrowing into its foot.
            shape { rect(5f, 2f, 12f, 9.5f) }
            shape { rect(4f, 10.5f, 20f, 12.5f) }
            shape {
                moveTo(5f, 13.5f); lineTo(19f, 13.5f)
                quadTo(19f, 18f, 14.5f, 18.5f)
                lineTo(15f, 22f); lineTo(8f, 22f); lineTo(8.5f, 18.3f)
                quadTo(5f, 17.5f, 5f, 13.5f); close()
            }
        }
    }

    private val bottle by lazy {
        icon("Flesje") {
            shape { rect(10f, 1.5f, 14f, 3.5f) }
            // Body with the label as a hole.
            shape(holes = true) {
                moveTo(10.5f, 4f); lineTo(13.5f, 4f); lineTo(13.5f, 6.5f)
                quadTo(17f, 8.5f, 17f, 11.5f)
                lineTo(17f, 20f); quadTo(17f, 22f, 15f, 22f)
                lineTo(9f, 22f); quadTo(7f, 22f, 7f, 20f)
                lineTo(7f, 11.5f); quadTo(7f, 8.5f, 10.5f, 6.5f); close()
                rect(8.5f, 13.5f, 15.5f, 17f)
            }
        }
    }

    private val snack by lazy {
        icon("Snack") {
            // Popcorn on top of a bowl on its foot.
            shape {
                circle(7.5f, 9.5f, 2.4f)
                circle(12f, 8f, 2.8f)
                circle(16.5f, 9.5f, 2.4f)
            }
            shape {
                moveTo(3f, 11.5f); lineTo(21f, 11.5f)
                quadTo(21f, 19.5f, 12f, 19.5f)
                quadTo(3f, 19.5f, 3f, 11.5f); close()
            }
            shape { rect(9f, 20.3f, 15f, 21.8f) }
        }
    }
}
