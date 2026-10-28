package nl.jordy.iptv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BestQualityTest {
    @Test
    fun `only the best picture quality is shown, other labels are dropped`() {
        assertEquals("4K", bestQuality(listOf("HD", "UHD")))
        assertEquals("FHD", bestQuality(listOf("Multi", "FHD", "HD")))
        assertEquals("HD", bestQuality(listOf("hd")))
        assertNull(bestQuality(listOf("Multi", "HEVC", "")))
    }
}
