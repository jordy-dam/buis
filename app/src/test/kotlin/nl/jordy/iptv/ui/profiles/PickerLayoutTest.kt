package nl.jordy.iptv.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import nl.jordy.iptv.data.db.ProfileEntity
import nl.jordy.iptv.data.profile.ProfileRepository
import nl.jordy.iptv.ui.theme.IptvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Wie kijkt er?" must show every profile and the manage button on a 1080p tv (960 × 540 dp), for
 * any household size. The old FlowRow left out its second row once it no longer fitted: at the
 * fifth profile only three were left on screen, and the other two looked deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-xhdpi")
class PickerLayoutTest {

    @get:Rule val compose = createComposeRule()

    private val names = listOf("Profiel 1", "Bas", "Anna", "Daan", "Eva", "Fleur", "Gijs", "Hanna")

    private fun household(n: Int) = List(n) { i ->
        // Captions make a tile taller: give every profile one, the worst case.
        ProfileEntity(id = i + 1L, name = names[i], avatar = i, face = i % 6, kids = i % 2 == 1, pinHash = if (i % 2 == 0) "x" else null)
    }

    @Test
    fun `every profile, the add tile and the manage button are on screen, one to eight profiles`() {
        var size by mutableIntStateOf(1)
        compose.setContent {
            IptvTheme {
                val profiles = household(size)
                PickerLayout(
                    profiles = profiles,
                    managing = false,
                    focus = List(profiles.size) { FocusRequester() },
                    onProfile = {},
                    onAdd = {},
                    onManage = {},
                )
            }
        }
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        for (n in 1..ProfileRepository.MAX_PROFILES) {
            size = n
            compose.waitForIdle()
            val expected = names.take(n) + listOfNotNull("Toevoegen".takeIf { n < ProfileRepository.MAX_PROFILES }) + "Profielen beheren"
            expected.forEach { text ->
                val node = compose.onNodeWithText(text)
                node.assertIsDisplayed()
                val b = node.getUnclippedBoundsInRoot()
                assertTrue("$text is cut off with $n profiles", b.top >= 0.dp && b.bottom <= root.bottom && b.left >= 0.dp && b.right <= root.right)
            }
        }
    }
}
