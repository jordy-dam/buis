package nl.jordy.iptv.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo

/**
 * The default policy retries a 404 playlist three times with 1s and 2s back-off. For a provider
 * without HLS that is 3+ seconds before the .ts fallback even starts: the whole zap budget.
 * A 4xx won't fix itself, so fail at once; network errors and 5xx keep the normal retries.
 */
@UnstableApi
class FailFastLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(info: LoadErrorInfo): Long {
        if (isClientError(info.exception)) return C.TIME_UNSET
        return super.getRetryDelayMsFor(info)
    }

    companion object {
        fun isClientError(e: Throwable): Boolean =
            e is HttpDataSource.InvalidResponseCodeException && e.responseCode in 400..499
    }
}
