package my.noveldokusha.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.tooling.application_workers.setup.AppWorkerFactory

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltAppEntryPoint {
    fun workerFactory(): AppWorkerFactory
    fun appPreferences(): AppPreferences
    // ponytail: expose ScraperNetworkClient so GlideModule can share the OkHttp client
    // (connection pool, cookie jar, Cloudflare interceptor) instead of building a standalone one.
    fun scraperNetworkClient(): ScraperNetworkClient
}
