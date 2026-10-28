package nl.jordy.iptv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class FailFastLoadErrorPolicyTest {

    private val policy = FailFastLoadErrorPolicy()

    private fun info(e: IOException) = LoadErrorInfo(
        LoadEventInfo(0, DataSpec(Uri.EMPTY), 0),
        MediaLoadData(C.DATA_TYPE_MANIFEST),
        e,
        1,
    )

    private fun http(code: Int) = HttpDataSource.InvalidResponseCodeException(
        code, null, null, emptyMap(), DataSpec(Uri.EMPTY), ByteArray(0),
    )

    @Test
    fun `a 404 playlist is not retried, so the ts fallback starts at once`() {
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(http(404))))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(http(403))))
    }

    @Test
    fun `flaky network and server errors still get retried`() {
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(http(503))))
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(IOException("connection reset"))))
    }
}
