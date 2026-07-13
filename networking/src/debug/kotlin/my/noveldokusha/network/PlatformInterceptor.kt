package my.noveldokusha.network

import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

internal fun createLoggingInterceptor() = HttpLoggingInterceptor { Timber.v(it) }.apply {
    level = HttpLoggingInterceptor.Level.HEADERS
}
