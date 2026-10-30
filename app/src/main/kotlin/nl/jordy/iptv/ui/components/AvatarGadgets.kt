package nl.jordy.iptv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Small gadgets for the avatar's lower right: what someone does or likes, next to the face. A
 * profile stores the index, 1-based; 0 is none. New gadgets go at the end so stored ones keep theirs.
 */
object AvatarGadgets {
    val all: List<Pair<String, ImageVector>> = listOf(
        "Steeksleutel" to Icons.Filled.Build,
        "Beveiliging" to Icons.Filled.LocalPolice,
        "Voetbal" to Icons.Filled.SportsSoccer,
        "Dokter" to Icons.Filled.MedicalServices,
        "ICT" to Icons.Filled.Mouse,
        "Gamen" to Icons.Filled.SportsEsports,
        "Muziek" to Icons.Filled.MusicNote,
        "Koken" to Icons.Filled.Restaurant,
        "Tekenen" to Icons.Filled.Brush,
        "Lezen" to Icons.Filled.MenuBook,
        "School" to Icons.Filled.School,
        "Brandweer" to Icons.Filled.LocalFireDepartment,
        "Fietsen" to Icons.Filled.DirectionsBike,
        "Reizen" to Icons.Filled.Flight,
        "Dieren" to Icons.Filled.Pets,
        "Ster" to Icons.Filled.Star,
    )

    fun get(gadget: Int): ImageVector? = all.getOrNull(gadget - 1)?.second
    fun name(gadget: Int): String = all.getOrNull(gadget - 1)?.first ?: "Geen"
}
