package nl.jordy.iptv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The drawn avatars: white figures for on the avatar colour, built from plain shapes on a 24×24
 * grid. Eyes and mouths are holes (even-odd fill), so the colour shows through. Not emoji: those
 * ignore the theme and look different per device.
 */
object AvatarFaces {
    /** Index 1..12 as stored in `profile.face`; 0 is the initial letter. New faces go at the end. */
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "Kat" to cat, "Vos" to fox, "Robot" to robot, "Uil" to owl, "Monster" to monster,
            "Hond" to dog, "Beer" to bear, "Panda" to panda, "Konijn" to rabbit, "Pinguïn" to penguin, "Kikker" to frog, "Leeuw" to lion,
        )
    }

    fun get(face: Int): ImageVector? = all.getOrNull(face - 1)?.second
    fun name(face: Int): String = all.getOrNull(face - 1)?.first ?: "Letter"

    private fun face(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.shape(holes: Boolean = true, block: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.White), pathFillType = if (holes) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)

    private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
        moveTo(cx - rx, cy)
        arcTo(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + rx, y1 = cy)
        arcTo(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - rx, y1 = cy)
        close()
    }

    private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }

    /** A star of [points] tips around (cx, cy): a lion's mane. */
    private fun PathBuilder.star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int) {
        for (i in 0 until points * 2) {
            val a = Math.PI * i / points - Math.PI / 2
            val r = if (i % 2 == 0) outer else inner
            val x = cx + (r * Math.cos(a)).toFloat()
            val y = cy + (r * Math.sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun PathBuilder.poly(vararg xy: Float) {
        moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
        close()
    }

    private val cat = face("Kat") {
        shape(holes = false) {
            poly(5.6f, 10f, 6.4f, 3.6f, 10.6f, 8.2f) // ears
            poly(18.4f, 10f, 17.6f, 3.6f, 13.4f, 8.2f)
        }
        shape {
            ellipse(12f, 13.5f, 7.2f, 6.2f)
            circle(9.3f, 12.8f, 1.1f) // eyes
            circle(14.7f, 12.8f, 1.1f)
            poly(11.1f, 15f, 12.9f, 15f, 12f, 16.1f) // nose
        }
    }

    private val fox = face("Vos") {
        shape {
            poly(4f, 5.5f, 8.6f, 9f, 15.4f, 9f, 20f, 5.5f, 18.6f, 13.2f, 12f, 20f, 5.4f, 13.2f)
            circle(9.2f, 13f, 1f)
            circle(14.8f, 13f, 1f)
            circle(12f, 18f, 0.9f) // nose
        }
    }

    private val robot = face("Robot") {
        shape(holes = false) {
            rect(11.4f, 4f, 12.6f, 7f) // antenna
            circle(12f, 3.4f, 1.3f)
            rect(3.4f, 10.5f, 5.2f, 14.5f) // ears
            rect(18.8f, 10.5f, 20.6f, 14.5f)
        }
        shape {
            // Head with rounded corners.
            moveTo(7.5f, 6.8f); lineTo(16.5f, 6.8f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 9.3f)
            lineTo(19f, 16.3f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 16.5f, y1 = 18.8f)
            lineTo(7.5f, 18.8f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 5f, y1 = 16.3f)
            lineTo(5f, 9.3f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7.5f, y1 = 6.8f)
            close()
            rect(8f, 10f, 10.6f, 12.6f) // eyes
            rect(13.4f, 10f, 16f, 12.6f)
            rect(8.6f, 15f, 15.4f, 16.3f) // mouth
        }
    }

    private val owl = face("Uil") {
        shape(holes = false) {
            poly(6.6f, 7.6f, 7.2f, 3.4f, 10.2f, 6.2f) // ear tufts
            poly(17.4f, 7.6f, 16.8f, 3.4f, 13.8f, 6.2f)
        }
        shape {
            ellipse(12f, 13.2f, 7f, 7.8f)
            circle(9.2f, 11.6f, 2.6f) // eye rings
            circle(14.8f, 11.6f, 2.6f)
            poly(11f, 14.4f, 13f, 14.4f, 12f, 16.2f) // beak
        }
        shape(holes = false) {
            circle(9.6f, 11.8f, 1.1f) // pupils
            circle(15.2f, 11.8f, 1.1f)
        }
    }

    private val monster = face("Monster") {
        shape {
            moveTo(4f, 20f); lineTo(4f, 11f)
            curveTo(4f, 5.6f, 7.6f, 3.4f, 12f, 3.4f)
            curveTo(16.4f, 3.4f, 20f, 5.6f, 20f, 11f)
            lineTo(20f, 20f); lineTo(17.3f, 18f); lineTo(14.6f, 20f); lineTo(12f, 18f)
            lineTo(9.4f, 20f); lineTo(6.7f, 18f)
            close()
            circle(12f, 10f, 3f) // one big eye
            rect(9f, 14.2f, 15f, 15.5f) // mouth
        }
        shape(holes = false) { circle(12.7f, 10.4f, 1.3f) }
    }

    private val dog = face("Hond") {
        shape(holes = false) {
            ellipse(5.4f, 11.6f, 2.2f, 4.4f) // floppy ears
            ellipse(18.6f, 11.6f, 2.2f, 4.4f)
        }
        shape {
            ellipse(12f, 12.8f, 6.2f, 6.6f)
            circle(9.6f, 11.6f, 1f) // eyes
            circle(14.4f, 11.6f, 1f)
            ellipse(12f, 15.4f, 1.6f, 1.1f) // nose
        }
    }

    private val bear = face("Beer") {
        shape(holes = false) {
            circle(6.4f, 6.6f, 2.6f) // ears
            circle(17.6f, 6.6f, 2.6f)
        }
        shape {
            circle(12f, 13f, 7.2f)
            circle(9.3f, 11.6f, 1f) // eyes
            circle(14.7f, 11.6f, 1f)
            ellipse(12f, 15.6f, 3f, 2.3f) // muzzle
        }
        shape(holes = false) { circle(12f, 14.9f, 0.9f) } // nose
    }

    private val panda = face("Panda") {
        shape(holes = false) {
            circle(6.4f, 6.6f, 2.6f) // ears
            circle(17.6f, 6.6f, 2.6f)
        }
        shape {
            circle(12f, 13f, 7.2f)
            ellipse(9f, 12.4f, 1.9f, 2.4f) // eye patches
            ellipse(15f, 12.4f, 1.9f, 2.4f)
            ellipse(12f, 16.2f, 1.2f, 0.8f) // nose
        }
        shape(holes = false) {
            circle(9.2f, 12.1f, 0.7f) // pupils
            circle(14.8f, 12.1f, 0.7f)
        }
    }

    private val rabbit = face("Konijn") {
        shape(holes = false) {
            ellipse(9.2f, 6f, 1.8f, 4.6f) // ears
            ellipse(14.8f, 6f, 1.8f, 4.6f)
        }
        shape {
            circle(12f, 14.6f, 6f)
            circle(9.8f, 13.8f, 0.9f) // eyes
            circle(14.2f, 13.8f, 0.9f)
            poly(11.2f, 16f, 12.8f, 16f, 12f, 17f) // nose
        }
    }

    private val penguin = face("Pinguïn") {
        shape {
            ellipse(12f, 12.4f, 7f, 8.4f)
            ellipse(12f, 14f, 4.8f, 5.6f) // the face, in the avatar colour
        }
        shape(holes = false) {
            circle(10f, 12.6f, 0.9f) // eyes
            circle(14f, 12.6f, 0.9f)
            poly(10.8f, 14.4f, 13.2f, 14.4f, 12f, 16f) // beak
        }
    }

    private val frog = face("Kikker") {
        shape {
            circle(7.8f, 7.8f, 2.8f) // eye bumps with pupils
            circle(7.8f, 7.8f, 1.1f)
            circle(16.2f, 7.8f, 2.8f)
            circle(16.2f, 7.8f, 1.1f)
        }
        shape {
            ellipse(12f, 13.8f, 8f, 5.6f)
            // A wide smile.
            moveTo(8f, 15f)
            curveTo(9.5f, 17.6f, 14.5f, 17.6f, 16f, 15f)
            curveTo(14.5f, 16.6f, 9.5f, 16.6f, 8f, 15f)
            close()
        }
    }

    private val lion = face("Leeuw") {
        shape {
            star(12f, 12.5f, 10.5f, 8.6f, 12) // mane
            circle(12f, 12.5f, 6f) // the face, in the avatar colour
        }
        shape(holes = false) {
            circle(10f, 11.6f, 0.9f) // eyes
            circle(14f, 11.6f, 0.9f)
            poly(10.9f, 13.8f, 13.1f, 13.8f, 12f, 15.2f) // nose
        }
    }
}
